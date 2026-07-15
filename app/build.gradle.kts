plugins {
    alias(libs.plugins.android.application)
    // nếu app có Kotlin thì thêm:
    // alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.finance"

    // Theo Cách A: nâng compileSdk lên 36 để hợp với androidx.activity:1.11.x
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.finance"

        // Hạ minSdk để cài được trên nhiều máy thật
        minSdk = 21

        // Play yêu cầu target >= 33; bạn đặt 35 là chuẩn hiện tại
        targetSdk = 35

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        // Kotlin DSL: đúng cú pháp là isCoreLibraryDesugaringEnabled
        isCoreLibraryDesugaringEnabled = true
    }

    // Nếu có Kotlin source (.kt) thì mở phần này:
    // kotlinOptions {
    //     jvmTarget = "11"
    // }
}

dependencies {
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // dùng version catalog như bạn đang có
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)         // 1.11.x yêu cầu compileSdk >= 36
    implementation(libs.constraintlayout)

    // ====== BỔ SUNG: CameraX cho ReceiptOcrActivity ======
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ====== BỔ SUNG: ML Kit OCR (on-device) ======
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // (Tuỳ chọn) Nếu IDE báo thiếu ListenableFuture khi dùng CameraX:
    implementation("com.google.guava:guava:33.3.1-android")

    // Desugaring để java.time chạy trên minSdk < 26
    // (bạn đang dùng 2.0.4 cũng OK; mình khuyên dùng 2.1.2 mới hơn)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
