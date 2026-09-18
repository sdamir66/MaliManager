plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    // ...
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    // ...
    
    // ✅ اینجا اضافه کن:
    implementation("sh.calvin.reorderable:reorderable:2.4.0")
    
    // ...
    debugImplementation("androidx.compose.ui:ui-tooling")
}
