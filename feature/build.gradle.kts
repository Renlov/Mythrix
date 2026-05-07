import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

// Read HuggingFace token from local.properties (git-ignored).
// Add this line locally to enable downloads of license-gated models like Gemma:
//   hf.token=hf_xxxxxxxxxxxxxxxxxxxx
// Token must NEVER be committed to git.
val hfToken: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    (props["hf.token"] as String?) ?: System.getenv("HF_TOKEN") ?: ""
}

android {
    namespace = "com.pimenov.feature"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "HF_TOKEN", "\"$hfToken\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
