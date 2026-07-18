# Liest ein generisches Anmeldedatum aus der Windows-Anmeldeinformationsverwaltung
# (Credential Manager) und gibt das Passwort ausschliesslich auf stdout aus, damit
# Gradle es direkt als Prozessausgabe einfaengt. Es erfolgt KEINE Konsolenausgabe,
# kein Logging und keine Zwischenspeicherung des Wertes durch dieses Skript.
#
# Aufruf: powershell -File get-signing-secret.ps1 <TargetName>
#   <TargetName> ist der in der Anmeldeinformationsverwaltung gespeicherte Zielname
#   (z. B. rssreader_store_password oder rssreader_key_password).
#
# Das Skript selbst enthaelt keine Geheimniswerte; die Werte liegen ausschliesslich
# benutzergebunden in der Windows-Anmeldeinformationsverwaltung.

param(
    [Parameter(Mandatory = $true)]
    [string]$TargetName
)

$code = @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public class CredStore {
    public const uint CRED_TYPE_GENERIC = 0x1;

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
    public static extern bool CredRead(string target, uint type, uint reserved, out IntPtr cred);

    [DllImport("advapi32.dll")]
    public static extern void CredFree(IntPtr cred);

    public static string Read(string target) {
        IntPtr ptr = IntPtr.Zero;
        try {
            if (!CredRead(target, CRED_TYPE_GENERIC, 0, out ptr)) {
                int err = Marshal.GetLastWin32Error();
                throw new System.ComponentModel.Win32Exception(err, "CredRead failed for target: " + target);
            }
            var cred = (CREDENTIAL)Marshal.PtrToStructure(ptr, typeof(CREDENTIAL));
            byte[] bytes = new byte[cred.CredentialBlobSize];
            Marshal.Copy(cred.CredentialBlob, bytes, 0, (int)cred.CredentialBlobSize);
            return Encoding.Unicode.GetString(bytes);
        } finally {
            if (ptr != IntPtr.Zero) CredFree(ptr);
        }
    }
}
'@
Add-Type $code

# Ausschliesslich der Geheimniswert auf stdout; keine weiteren Ausgaben.
# Explizit UTF-8 ohne BOM ueber den Standardausgabestrom schreiben, um
# Codepage-Mehrdeutigkeiten beim Einlesen durch Gradle zu vermeiden.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$writer = New-Object System.IO.StreamWriter([Console]::OpenStandardOutput(), $utf8NoBom)
$writer.Write([CredStore]::Read($TargetName))
$writer.Flush()
$writer.Dispose()
