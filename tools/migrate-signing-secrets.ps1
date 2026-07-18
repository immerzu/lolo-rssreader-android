# Einmalige Migration der Release-Signierpasswoerter aus der lokalen, nicht
# versionierten keystore.properties in die Windows-Anmeldeinformationsverwaltung
# (Credential Manager). Danach duerfen die Klartext-Passwoerter aus keystore.properties
# entfernt werden, da der Release-Build sie ueber tools/get-signing-secret.ps1 abruft.
#
# Das Skript gibt KEINE Passwortwerte aus. Es liest ausschliesslich aus der lokalen
# keystore.properties und schreibt die Werte benutzergebunden in den Credential Store.
#
# Aufruf: powershell -File migrate-signing-secrets.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
$propsPath = Join-Path $root ".." | Join-Path -ChildPath "keystore.properties"
$propsPath = Resolve-Path $propsPath

$props = @{}
Get-Content $propsPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $idx = $line.IndexOf("=")
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        $props[$key] = $val
    }
}

$storePassword = $props["storePassword"]
$keyPassword = $props["keyPassword"]
if (-not $storePassword -or -not $keyPassword) {
    throw "storePassword oder keyPassword nicht in keystore.properties gefunden."
}

$code = @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public class CredStore {
    public const uint CRED_TYPE_GENERIC = 0x1;
    public const uint CRED_PERSIST_LOCAL_MACHINE = 0x2;

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public uint Flags;
        public uint Type;
        public string TargetName;
        public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CredWrite([In] ref CREDENTIAL cred, uint flags);

    public static void Write(string target, string secret) {
        byte[] bytes = Encoding.Unicode.GetBytes(secret);
        var cred = new CREDENTIAL();
        cred.Type = CRED_TYPE_GENERIC;
        cred.TargetName = target;
        cred.CredentialBlobSize = (uint)bytes.Length;
        cred.CredentialBlob = Marshal.AllocHGlobal(bytes.Length);
        Marshal.Copy(bytes, 0, cred.CredentialBlob, bytes.Length);
        cred.Persist = CRED_PERSIST_LOCAL_MACHINE;
        try {
            if (!CredWrite(ref cred, 0)) {
                int err = Marshal.GetLastWin32Error();
                throw new System.ComponentModel.Win32Exception(err, "CredWrite failed for target: " + target);
            }
        } finally {
            Marshal.FreeHGlobal(cred.CredentialBlob);
        }
    }
}
'@
Add-Type $code

[CredStore]::Write("rssreader_store_password", $storePassword)
[CredStore]::Write("rssreader_key_password", $keyPassword)

# Nur nicht-geheime Bestaetigung (Laenge, kein Wert).
Write-Output ("Stored rssreader_store_password (len=" + $storePassword.Length + ")")
Write-Output ("Stored rssreader_key_password (len=" + $keyPassword.Length + ")")
