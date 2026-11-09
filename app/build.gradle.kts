plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.herkules.galleryai"
    compileSdk = 35
    defaultConfig { applicationId = "com.herkules.galleryai"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.mlkit:image-labeling:17.0.9")
}
