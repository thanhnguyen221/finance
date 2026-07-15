// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Nếu dự án có Kotlin, mở dòng dưới:
    // alias(libs.plugins.kotlin.android) apply false
}

// (Tuỳ chọn) task dọn build
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
