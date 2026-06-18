plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.bgf.unityhc"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.health.connect)

    // Unity classes are provided at runtime by the Unity Player. We only need
    // them to compile (UnityPlayer.UnitySendMessage / currentActivity). The
    // bridge uses reflection so this is optional, but keeping it commented in
    // case you want to drop classes.jar from your Unity install here later.
    // compileOnly(files("libs/classes.jar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
