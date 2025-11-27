plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.panicbuttonrtdb"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.panicbuttonrtdb"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Firebase database dependency
    implementation(libs.firebase.database)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // ViewModel utilities for Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Jetpack Compose integration
    implementation(libs.androidx.navigation.compose)
    implementation(libs.firebase.database)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.firebase.storage)



    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation ("com.airbnb.android:lottie-compose:5.2.0")
    implementation ("io.coil-kt:coil-compose:2.7.0")
    implementation ("io.coil-kt:coil:2.1.0")
    implementation ("androidx.compose.animation:animation:1.7.3")
    implementation ("androidx.core:core-ktx:1.12.0")
    implementation ("com.google.android.gms:play-services-location:21.2.0")
    implementation ("com.google.maps.android:maps-compose:4.3.3")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation ("com.google.android.gms:play-services-location:21.2.0")
    implementation ("com.google.maps.android:maps-compose:4.3.3")


}