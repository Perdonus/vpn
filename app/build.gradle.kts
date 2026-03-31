import java.net.HttpURLConnection
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.perdonus.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.perdonus.vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val libv2rayFile = layout.projectDirectory.file("libs/libv2ray.aar").asFile

val prepareLibv2ray by tasks.registering {
    outputs.file(libv2rayFile)
    doLast {
        if (libv2rayFile.exists() && libv2rayFile.length() > 0L) {
            return@doLast
        }

        libv2rayFile.parentFile.mkdirs()
        val connection =
            URL("https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar")
                .openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "PerdonusVPN-Build")
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.requestMethod = "GET"

        connection.inputStream.use { input ->
            libv2rayFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(prepareLibv2ray)
}

dependencies {
    implementation(files("libs/libv2ray.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

