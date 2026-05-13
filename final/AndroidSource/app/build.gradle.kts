import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val supabaseUrl = localProperties.getProperty("SUPABASE_URL", "")
val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY", "")
val supabaseAuthRedirectUrl = localProperties.getProperty(
    "SUPABASE_AUTH_REDIRECT_URL",
    "https://jerrylemin.github.io/HotelGo_ChuaBietTen/confirm.html"
)
val googleWebClientId = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")
val supabaseNotificationEmailFunction = localProperties.getProperty(
    "SUPABASE_NOTIFICATION_EMAIL_FUNCTION",
    "send-notification-email"
)

android {
    namespace = "com.example.hotelapp_test2"
    compileSdk {
        version = release(36)
    }
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.hotelapp_test2"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_AUTH_REDIRECT_URL", "\"$supabaseAuthRedirectUrl\"")
        buildConfigField("String", "SUPABASE_NOTIFICATION_EMAIL_FUNCTION", "\"$supabaseNotificationEmailFunction\"")

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
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
    }
    sourceSets {
        getByName("main") {
            assets.directories.add("../HotelList")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation("io.coil-kt:coil:2.6.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
