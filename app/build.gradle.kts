import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// IDs de cliente OAuth (arquivo oauth.properties, na raiz do repositório)
val oauth = Properties().apply {
    val f = rootProject.file("oauth.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val googleClientId = oauth.getProperty("GOOGLE_CLIENT_ID", "").trim()
val msClientId = oauth.getProperty("MS_CLIENT_ID", "").trim()
val googleScheme = if (googleClientId.endsWith(".apps.googleusercontent.com"))
    "com.googleusercontent.apps." + googleClientId.removeSuffix(".apps.googleusercontent.com")
else "com.googleusercontent.apps.naoconfigurado"

android {
    namespace = "com.willian.nuvem"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.willian.nuvem"
        minSdk = 30
        targetSdk = 34
        versionCode = maxOf(1, (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt())
        versionName = "1.0.0"

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "MS_CLIENT_ID", "\"$msClientId\"")
        buildConfigField("String", "GOOGLE_SCHEME", "\"$googleScheme\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "com.willian.nuvem"
        manifestPlaceholders["googleScheme"] = googleScheme
    }

    signingConfigs {
        create("fixa") {
            storeFile = file("nuvem.jks")
            storePassword = "nuvem-willian-2026"
            keyAlias = "nuvem"
            keyPassword = "nuvem-willian-2026"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixa")
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
    kotlinOptions { jvmTarget = "17" }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    implementation("com.google.guava:guava:33.2.1-android")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.openid:appauth:0.11.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
