import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
val stableDebugKeystore = rootProject.layout.projectDirectory.file("keystore/whitevpn-debug.keystore").asFile

android {
    namespace = "com.white.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.white.vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = ciRunNumber
        versionName = "0.1.$ciRunNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = stableDebugKeystore
            storePassword = "android"
            keyAlias = "whitevpn-debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }

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
val libv2rayUrl = "https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.3.27/libv2ray.aar"
val libv2raySha256 = "aac45dfc31e8c85fce14641afac9a1747fc88938bcf4bcaa5de005147880baa9"

fun File.sha256(): String =
    inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

val prepareLibv2ray by tasks.registering {
    outputs.file(libv2rayFile)
    doLast {
        if (libv2rayFile.exists() && libv2rayFile.length() > 0L && libv2rayFile.sha256() == libv2raySha256) {
            return@doLast
        }

        libv2rayFile.parentFile.mkdirs()
        val temporaryFile = File(libv2rayFile.parentFile, "${libv2rayFile.name}.part")
        val connection =
            URL(libv2rayUrl)
                .openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "WhiteVPN-Build")
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.requestMethod = "GET"
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw GradleException("Failed to download libv2ray.aar: HTTP ${connection.responseCode}")
        }

        connection.inputStream.use { input ->
            temporaryFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val downloadedSha256 = temporaryFile.sha256()
        if (downloadedSha256 != libv2raySha256) {
            temporaryFile.delete()
            throw GradleException(
                "libv2ray.aar checksum mismatch: expected $libv2raySha256 but got $downloadedSha256",
            )
        }

        temporaryFile.copyTo(libv2rayFile, overwrite = true)
        temporaryFile.delete()
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
