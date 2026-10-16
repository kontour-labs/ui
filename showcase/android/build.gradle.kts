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
        // Judging performance from a debug build is judging the wrong thing,
        // and a release build without R8 is most of the way to one. Compose
        // leaves a `sourceInformation` and a pair of trace calls in every
        // composable for R8 to strip, relies on it to inline and to merge the
        // small classes every lambda and modifier compiles to, and its Android
        // performance guidance assumes it. This was `isMinifyEnabled = false`
        // until 0.4.5, so "use the release build" measured a build Compose
        // does not expect anyone to ship.
        //
        // It still has no baseline profile of its own, so code the platform's
        // profiles do not cover runs interpreted until the next background
        // compile. To judge it as it will settle, after installing:
        //
        //     adb shell cmd package compile -m speed -f io.kontour.ui.catalog
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
