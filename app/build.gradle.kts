import org.gradle.api.tasks.Copy
import java.util.Properties
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val roomSchemaDir = projectDir.resolve("schemas")
val appVersionCode = 130
val appVersionName = "1.85.02"

val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties().apply {
    if (versionPropertiesFile.exists()) {
        versionPropertiesFile.inputStream().use(::load)
    } else {
        setProperty("VERSION_CODE", appVersionCode.toString())
        setProperty("VERSION_NAME", appVersionName)
        versionPropertiesFile.outputStream().use { output ->
            store(output, "RSS Reader build version")
        }
    }
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

val resolvedVersionCode = versionProperties.getProperty("VERSION_CODE")?.toIntOrNull() ?: appVersionCode
val resolvedVersionName = versionProperties.getProperty("VERSION_NAME") ?: appVersionName
val debugBuildStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.example.rssreader"
    compileSdk = 35

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "de.lolo.rssreader"
        minSdk = 26
        targetSdk = 35
        // Keep the version directly visible for external scanners like F-Droid.
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Fuer spaetere Release-Haertung bei Bedarf vorsichtig aktivieren:
            // isMinifyEnabled = true
            // isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", roomSchemaDir.path)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.work:work-testing:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
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
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(exportedApkDir)
    rename { "RSS-Reader-v$resolvedVersionName-release.apk" }
}

tasks.register<Copy>("exportReleaseBundle") {
    from(layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
    into(exportedApkDir)
    rename { "RSS-Reader-v$resolvedVersionName-release.aab" }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("exportDebugApk")
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("exportReleaseApk")
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    finalizedBy("exportReleaseBundle")
}

tasks.register("bumpReleaseVersion") {
    group = "versioning"
    description = "Erhoeht VERSION_NAME und VERSION_CODE bewusst fuer den naechsten Release."
    doLast {
        val currentVersionCode = versionProperties.getProperty("VERSION_CODE")?.toIntOrNull() ?: appVersionCode
        val currentVersionName = versionProperties.getProperty("VERSION_NAME") ?: appVersionName
        val nextVersionCode = currentVersionCode + 1
        val nextVersionName = incrementPatchVersion(currentVersionName)

        versionProperties.setProperty("VERSION_CODE", nextVersionCode.toString())
        versionProperties.setProperty("VERSION_NAME", nextVersionName)
        versionPropertiesFile.outputStream().use { output ->
            versionProperties.store(output, "RSS Reader build version")
        }

        val buildScriptFile = project.buildFile
        val updatedBuildScript = buildScriptFile.readText()
            .replace(Regex("""val appVersionCode = \d+"""), "val appVersionCode = $nextVersionCode")
            .replace(Regex("""val appVersionName = "1.70.02"]+""""), """val appVersionName = "1.70.02"""")
        buildScriptFile.writeText(updatedBuildScript)
    }
}


