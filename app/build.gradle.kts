import org.gradle.api.tasks.Copy
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

val roomSchemaDir = projectDir.resolve("schemas")

val versionPropertiesFile = rootProject.file("version.properties")
if (!versionPropertiesFile.exists()) {
    error(
        "version.properties fehlt: $versionPropertiesFile. " +
            "Die Versionsnummer wird ausschliesslich aus dieser Datei gelesen."
    )
}
val versionProperties = Properties().apply {
    versionPropertiesFile.inputStream().use(::load)
}

fun incrementPatchVersion(versionName: String): String {
    val parts = versionName.split(".").mapNotNull { it.toIntOrNull() }.toMutableList()
    if (parts.isEmpty()) {
        return "1.70.01"
    }
    while (parts.size < 3) {
        parts += 0
    }
    val major = parts[0]
    val minor = parts[1]
    val patch = parts[2] + 1
    return "%d.%d.%02d".format(major, minor, patch)
}

val resolvedVersionCode = versionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: error("version.properties enthaelt keinen gueltigen VERSION_CODE")
val resolvedVersionName = versionProperties.getProperty("VERSION_NAME")
    ?: error("version.properties enthaelt keinen VERSION_NAME")
val debugBuildStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

// Liest ein Release-Signierpasswort aus der Windows-Anmeldeinformationsverwaltung
// (Credential Manager). Die Werte liegen benutzergebunden im OS-Secret-Store und
// werden zur Buildzeit ueber tools/get-signing-secret.ps1 abgerufen – niemals im
// Klartext in einer Projektdatei. Das Ergebnis wird direkt eingefangen, nicht
// auf der Konsole ausgegeben.
fun readWindowsCredential(target: String): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", rootProject.file("tools/get-signing-secret.ps1").absolutePath,
            target
        )
        standardOutput = stdout
    }
    return stdout.toString(Charsets.UTF_8.name()).trim().replace("\uFEFF", "")
}

android {
    namespace = "de.lolo.rssreader"
    compileSdk = 36

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                keyAlias = keystoreProperties.getProperty("keyAlias")
                // Passwoerter kommen ausschliesslich aus dem Windows-Secret-Store.
                storePassword = readWindowsCredential("rssreader_store_password")
                keyPassword = readWindowsCredential("rssreader_key_password")
            }
        }
    }

    defaultConfig {
        applicationId = "de.lolo.rssreader"
        minSdk = 26
        targetSdk = 36
        // Keep the version directly visible for external scanners like F-Droid.
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            // Fuer spaetere Release-Haertung bei Bedarf vorsichtig aktivieren:
            // isMinifyEnabled = true
            // isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug-Builds mit demselben Release-Key signieren, damit
            // adb install -r eine installierte Release-Version ohne
            // Deinstallation aktualisieren kann (kein Signatur-Konflikt).
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

tasks.matching { it.name == "compileReleaseArtProfile" }.configureEach {
    enabled = false
}

ksp {
    arg("room.schemaLocation", roomSchemaDir.path)
}

configurations.all {
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val exportedApkDir = rootProject.projectDir.parentFile?.resolve("Ausgabe_APK")
    ?: rootProject.projectDir.resolve("Ausgabe_APK")

tasks.register<Copy>("exportDebugApk") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(exportedApkDir)
    outputs.upToDateWhen { false }
    // Debug-Builds sollen archiviert statt ueberschrieben werden.
    rename { "RSS-Reader-v$resolvedVersionName-debug-$debugBuildStamp.apk" }
}

tasks.register<Copy>("exportReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(exportedApkDir)
    rename { "RSS-Reader-v$resolvedVersionName-release.apk" }
}

tasks.register<Copy>("exportReleaseBundle") {
    dependsOn("bundleRelease")
    from(layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
    into(exportedApkDir)
    rename { "RSS-Reader-v$resolvedVersionName-release.aab" }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("exportDebugApk")
}

tasks.register("bumpReleaseVersion") {
    group = "versioning"
    description = "Erhoeht VERSION_NAME und VERSION_CODE bewusst fuer den naechsten Release. " +
        "version.properties ist die einzige Versionsquelle."
    doLast {
        val currentVersionCode = versionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
            ?: error("version.properties enthaelt keinen gueltigen VERSION_CODE")
        val currentVersionName = versionProperties.getProperty("VERSION_NAME")
            ?: error("version.properties enthaelt keinen VERSION_NAME")
        val nextVersionCode = currentVersionCode + 1
        val nextVersionName = incrementPatchVersion(currentVersionName)

        versionProperties.setProperty("VERSION_CODE", nextVersionCode.toString())
        versionProperties.setProperty("VERSION_NAME", nextVersionName)
        versionPropertiesFile.outputStream().use { output ->
            versionProperties.store(output, "RSS Reader build version")
        }
        logger.lifecycle(
            "version.properties aktualisiert: " +
                "$currentVersionName -> $nextVersionName (Code $currentVersionCode -> $nextVersionCode)"
        )
    }
}
