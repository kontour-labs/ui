plugins {
    // AGP 9 brings its own Kotlin support, so no `kotlin.android` here.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "io.kontour.ui.catalog.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.kontour.ui.catalog"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // Signed with the debug key so `installRelease` works without a keystore.
        //
        // Judging performance from a debug build is judging the wrong thing. A
        // debug build has no baseline profile, so the first run through any code
        // path is interpreted — and opening a sheet for the first time runs a
        // great deal of code for the first time. That reads exactly like "the
        // app drops to a low frame rate when a sheet opens", and it is not what
        // a user would see. Anything measured with the frame readout should be
        // measured here.
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":ui-catalog"))
    implementation(libs.androidx.activity.compose)
}
