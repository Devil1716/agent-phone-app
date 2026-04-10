plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gemma.agentphone"
    compileSdk = 34
    val hasReleaseSigning = !System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank() &&
        !System.getenv("ANDROID_KEYSTORE_PASSWORD").isNullOrBlank() &&
        !System.getenv("ANDROID_KEY_ALIAS").isNullOrBlank() &&
        !System.getenv("ANDROID_KEY_PASSWORD").isNullOrBlank()

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(System.getenv("ANDROID_KEYSTORE_PATH"))
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            } else {
                // Restore fallback for CI builds without secrets
                val localJks = file("release.jks")
                if (localJks.exists()) {
                    storeFile = localJks
                    storePassword = "gemma123"
                    keyAlias = "gemma"
                    keyPassword = "gemma123"
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.gemma.agentphone"
        minSdk = 28
        targetSdk = 34
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 6
        versionName = (project.findProperty("versionName") as String?) ?: "0.3.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "APP_REPO_OWNER", "\"Devil1716\"")
        buildConfigField("String", "APP_REPO_NAME", "\"agent-phone-app\"")
        buildConfigField(
            "String",
            "DEFAULT_MODEL_DOWNLOAD_URL",
            "\"https://huggingface.co/AfiOne/gemma3-1b-it-int4.task/resolve/main/gemma3-1b-it-int4.task?download=true\""
        )
        buildConfigField("String", "DEFAULT_MODEL_FILENAME", "\"gemma3-1b-it-int4.task\"")
        buildConfigField(
            "String",
            "DEFAULT_MODEL_SOURCE_PAGE_URL",
            "\"https://huggingface.co/AfiOne/gemma3-1b-it-int4.task\""
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        create("alpha") {
            initWith(getByName("release"))
            applicationIdSuffix = ".alpha"
            versionNameSuffix = "-alpha"
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isDebuggable = false
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    testOptions {
        animationsDisabled = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("com.google.truth:truth:1.4.4")
}
