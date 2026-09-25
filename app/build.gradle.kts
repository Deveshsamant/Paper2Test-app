plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Push notifications need the Firebase project file (app/google-services.json, from the Firebase console).
// Without it the app still builds; notifications simply stay off.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "app.paper2test"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.paper2test"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"
        // Web OAuth client id: Credential Manager issues ID tokens for this audience, which the API already accepts.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"551296682410-7tjhbhomppk9q8j2lacfv47o8cr4lq5d.apps.googleusercontent.com\"")
        buildConfigField("String", "SITE", "\"https://paper2test.app\"")
    }
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Google Sign-In via Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Camera document scanner + QR scanner (Play services, no camera permission code needed)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // Google Play in-app updates (the app offers / requires new versions without leaving it)
    implementation("com.google.android.play:app-update:2.1.0")
    // Google Play Billing: plans and paper packs bought in the app
    implementation("com.android.billingclient:billing:8.0.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") // text of typed PDFs (read free, no AI)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Push notifications (Firebase Cloud Messaging, free)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    // Institute links from the Play Store (install referrer)
    implementation("com.android.installreferrer:installreferrer:2.2")
}
