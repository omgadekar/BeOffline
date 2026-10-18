plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.beoffline.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.beoffline.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.0.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // Work around an R8/Windows failure that blocks signed Play Store builds.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // MUST be HTTPS (release builds block cleartext) and MUST be set to
            // the real deployed host before shipping — the placeholder does not
            // resolve, so a forgotten edit fails loudly, not silently.
            buildConfigField(
                "String", "ACCOUNTABILITY_API_BASE_URL",
                "\"https://beoffline-api.askthepolicy.com/\""   // trailing slash required
            )
        }
        debug {
            isDebuggable = true
            // Host machine as seen from an emulator (local Kestrel / docker).
            buildConfigField(
                "String", "ACCOUNTABILITY_API_BASE_URL",
                "\"http://10.0.2.2:5080/\""
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        // Expose exported Room schemas to instrumentation tests (MigrationTestHelper).
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    // Export Room schemas so real migrations can be written and tested.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    // Accountability backend client
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.microsoft.signalr)

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Gson (for Room TypeConverters)
    implementation(libs.gson)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
