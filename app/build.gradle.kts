import org.gradle.api.tasks.Copy
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties().apply {
    if (versionPropertiesFile.exists()) {
        versionPropertiesFile.inputStream().use(::load)
    } else {
        setProperty("VERSION_CODE", "1")
        setProperty("VERSION_NAME", "1.50.00")
        versionPropertiesFile.outputStream().use { output ->
            store(output, "RSS Reader build version")
        }
    }
}

fun incrementPatchVersion(versionName: String): String {
    val parts = versionName.split(".").mapNotNull { it.toIntOrNull() }.toMutableList()
    if (parts.isEmpty()) {
        return "1.50.01"
    }
    while (parts.size < 3) {
        parts += 0
    }
    val major = parts[0]
    val minor = parts[1]
    val patch = parts[2] + 1
    return "%d.%d.%02d".format(major, minor, patch)
}

val buildRequested = gradle.startParameter.taskNames.any { taskName ->
    val normalized = taskName.substringAfterLast(":").lowercase()
    normalized.startsWith("assemble") ||
        normalized.startsWith("bundle") ||
        normalized.startsWith("install")
}

fun resolveBuildVersion(): Pair<Int, String> {
    val storedVersionCode = versionProperties.getProperty("VERSION_CODE")?.toIntOrNull() ?: 1
    val storedVersionName = versionProperties.getProperty("VERSION_NAME") ?: "1.50.00"

    if (!buildRequested) {
        return storedVersionCode to storedVersionName
    }

    val nextVersionCode = storedVersionCode + 1
    val nextVersionName = incrementPatchVersion(storedVersionName)
    versionProperties.setProperty("VERSION_CODE", nextVersionCode.toString())
    versionProperties.setProperty("VERSION_NAME", nextVersionName)
    versionPropertiesFile.outputStream().use { output ->
        versionProperties.store(output, "RSS Reader build version")
    }
    return nextVersionCode to nextVersionName
}

val resolvedBuildVersion = resolveBuildVersion()
val resolvedVersionCode = resolvedBuildVersion.first
val resolvedVersionName = resolvedBuildVersion.second
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
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val exportedApkDir = rootProject.projectDir.parentFile?.resolve("Ausgabe_APK")
    ?: rootProject.projectDir.resolve("Ausgabe_APK")

tasks.register<Copy>("exportDebugApk") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(exportedApkDir)
    rename { "RSS-Reader-v$resolvedVersionName-$resolvedVersionCode-debug.apk" }
}

tasks.register<Copy>("exportReleaseApk") {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(exportedApkDir)
    rename { "RSS-Reader-v$resolvedVersionName-$resolvedVersionCode-release.apk" }
}

tasks.register<Copy>("exportReleaseBundle") {
    from(layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
    into(exportedApkDir)
    rename { "RSS-Reader-v$resolvedVersionName-$resolvedVersionCode-release.aab" }
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
