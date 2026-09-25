import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: keystore.properties (created by tools/create-keystore.sh|.bat) or env vars.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingProp(name: String): String? =
    keystoreProps.getProperty(name)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }
val releaseKeystore: File? = signingProp("KEYSTORE_FILE")?.let { rootProject.file(it) }

android {
    namespace = "com.duel2048.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.duel2048.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "1.2.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null && releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = signingProp("KEYSTORE_PASSWORD")
                keyAlias = signingProp("KEY_ALIAS")
                keyPassword = signingProp("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        // AGP 7.3.1 era: Java 8 targets are the safest input for its D8; the module is pure Kotlin.
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures { compose = true }

    composeOptions {
        // Kotlin 1.9.24 pairs with Compose compiler 1.5.14 (see gradle/libs.versions.toml).
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packagingOptions {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // lintVital pulls extra tooling jars (trove4j, etc.) that may be missing from a partial
        // m2; compile, minify and signing still run for release APKs.
        checkReleaseBuilds = false
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

// Refuse to produce an unsigned release build; explain how to create the key.
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { it.path.startsWith(":android:") && it.name.contains("Release") && !it.name.contains("UnitTest") }
    if (wantsRelease && (releaseKeystore == null || !releaseKeystore.exists())) {
        throw GradleException(
            "Release builds must be signed. Run tools/create-keystore.sh (Windows: tools\\create-keystore.bat) once " +
                "to create android/keystore/duel2048-release.jks + keystore.properties, or set KEYSTORE_FILE, " +
                "KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD. See README.md.",
        )
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":cube2"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
