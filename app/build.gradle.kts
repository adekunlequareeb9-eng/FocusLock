plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // To rename your app's package, change BOTH of these to the same new value
    // and move the folder app/src/main/java/com/example/phoneapk to match.
    namespace = "com.example.phoneapk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.phoneapk"
        minSdk = 24
        targetSdk = 34
        // The build number is passed in by the workflow so every build counts up.
        versionCode = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
