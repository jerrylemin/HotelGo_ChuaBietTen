import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun resolveConfig(name: String, defaultValue: String = ""): String {
    return localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable(name).orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: defaultValue
}

val supabaseUrl = resolveConfig("SUPABASE_URL")
val supabaseAnonKey = resolveConfig("SUPABASE_ANON_KEY")
val payosClientId = resolveConfig("PAYOS_CLIENT_ID")
val payosApiKey = resolveConfig("PAYOS_API_KEY")
val payosChecksumKey = resolveConfig("PAYOS_CHECKSUM_KEY")
val payosBaseUrl = resolveConfig("PAYOS_BASE_URL", "https://api-merchant.payos.vn")
val payosReturnUrl = resolveConfig("PAYOS_RETURN_URL", "hotelapp://payos-return")
val payosCancelUrl = resolveConfig("PAYOS_CANCEL_URL", "hotelapp://payos-cancel")
val googleWebClientId = resolveConfig(
    "GOOGLE_WEB_CLIENT_ID",
    "573206626596-gv1btnudau56dm0pog4sit1j4cq6d9m7.apps.googleusercontent.com"
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
        buildConfigField("String", "PAYOS_CLIENT_ID", "\"$payosClientId\"")
        buildConfigField("String", "PAYOS_API_KEY", "\"$payosApiKey\"")
        buildConfigField("String", "PAYOS_CHECKSUM_KEY", "\"$payosChecksumKey\"")
        buildConfigField("String", "PAYOS_BASE_URL", "\"$payosBaseUrl\"")
        buildConfigField("String", "PAYOS_RETURN_URL", "\"$payosReturnUrl\"")
        buildConfigField("String", "PAYOS_CANCEL_URL", "\"$payosCancelUrl\"")
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
