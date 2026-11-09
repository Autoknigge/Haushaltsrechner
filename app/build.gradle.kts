import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// AdMob-Kennungen sind öffentlich (stehen in jeder App). Überschreibbar per Gradle-Property.
fun setting(name: String, fallback: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: fallback

val admobAppId = setting("ADMOB_APP_ID", "ca-app-pub-7178658520690671~6631369757")
val admobBannerId = setting("ADMOB_BANNER_ID", "ca-app-pub-7178658520690671/3393018237")
val admobStartId = setting("ADMOB_START_ID", "ca-app-pub-7178658520690671/3833816029")

val keystoreFile = setting("KEYSTORE_FILE", "")

android {
    namespace = "de.autoknigge.hauskontor"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.autoknigge.hauskontor"
        minSdk = 26
        targetSdk = 36
        versionCode = setting("VERSION_CODE", "1").toInt()
        versionName = setting("VERSION_NAME", "1.0")

        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_START_ID", "\"$admobStartId\"")
    }

    signingConfigs {
        if (keystoreFile.isNotBlank()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = setting("KEYSTORE_PASSWORD", "")
                keyAlias = setting("KEY_ALIAS", "")
                keyPassword = setting("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile.isNotBlank()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Texterkennung auf dem Gerät (Modell ist in der App enthalten, kein Upload)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Werbung und Einwilligungsabfrage
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
