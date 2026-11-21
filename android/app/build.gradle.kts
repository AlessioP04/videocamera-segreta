import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Chiave di firma della release: dalle variabili d'ambiente (CI, vedi .github/workflows/release.yml)
 * oppure da android/keystore.properties (build locali, file ignorato da git). Senza, si usa la chiave di debug.
 */
val releaseKey: Map<String, String>? = run {
    val env = System.getenv()
    if (env["ANDROID_KEYSTORE_FILE"] != null) {
        return@run mapOf(
            "storeFile" to env.getValue("ANDROID_KEYSTORE_FILE"),
            "storePassword" to env.getValue("ANDROID_KEYSTORE_PASSWORD"),
            "keyAlias" to env.getValue("ANDROID_KEY_ALIAS"),
        )
    }
    val file = rootProject.file("keystore.properties")
    if (!file.exists()) return@run null
    val props = Properties().apply { file.inputStream().use(::load) }
    props.stringPropertyNames().associateWith { props.getProperty(it) }
}

/** Versione: dal tag (es. -PappVersion=0.2.0 nel workflow di release), altrimenti quella di sviluppo. */
val appVersion = (findProperty("appVersion") as String?) ?: "0.1.0"
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

android {
    namespace = "io.github.alessiop04.videocamerasegreta"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.alessiop04.videocamerasegreta"
        minSdk = 29 // MediaStore con RELATIVE_PATH: niente permesso di scrittura per salvare in galleria
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = file(releaseKey.getValue("storeFile"))
            storePassword = releaseKey.getValue("storePassword")
            keyAlias = releaseKey.getValue("keyAlias")
            keyPassword = releaseKey["keyPassword"] ?: releaseKey.getValue("storePassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.video)

    testImplementation(libs.junit)
}
