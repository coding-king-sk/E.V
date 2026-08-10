plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ev.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ev.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 11
        versionName = "1.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Native libraries sirf arm64-v8a ke liye repo me hain. Isse APK me
        // khaali architecture folders nahi bante.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    packaging {
        resources {
            // commons-compress apne saath metadata laata hai jo APK me bekaar hai.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/LICENSE*"
        }
    }

    testOptions {
        unitTests {
            // Parser tests pure Kotlin hain, par koi Android class chhu jaye to
            // test crash na ho — default value mil jaye.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Coroutines har jagah use hote hain (network, contacts, app list). Pehle ye
    // sirf lifecycle ke through transitively aa rahe the.
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Camera — "photo lo" bolne pe khud photo/video lene ke liye.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // Wake word model .tar.bz2 me aata hai — use kholne ke liye.
    implementation(libs.commons.compress)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
