plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.t3detector"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.t3detector"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true
    }
}

dependencies {

    // Core Android
    implementation(libs.play.services.wearable.v1810)
    implementation(libs.androidx.core.ktx.v1120)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

// ------------- TENSORFLOW LITE (MUST MATCH EXACT VERSION) -------------
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.select.tf.ops)
    implementation(libs.tensorflow.lite.support)
// ----------------------------------------------------------------------

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v130)
    androidTestImplementation(libs.androidx.espresso.core.v370)
}
