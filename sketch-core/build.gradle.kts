plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "com.github.panpf.sketch.core"
    compileSdk = property("compileSdk").toString().toInt()

    defaultConfig {
        minSdk = property("minSdk").toString().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "VERSION_NAME", "\"${property("versionName").toString()}\"")
        buildConfigField("int", "VERSION_CODE", property("versionCode").toString())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        targetSdk = property("targetSdk").toString().toInt()
    }

    // Set both the Java and Kotlin compilers to target Java 8.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    api(libs.kotlin.stdlib.jdk8)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.annotation)
    api(libs.androidx.appcompat.resources)
    api(libs.androidx.core)
    api(libs.androidx.exifinterface)
    api(libs.androidx.lifecycle.runtime)
    compileOnly(libs.composeStableMarker)

    androidTestImplementation(project(":sketch-test"))
    androidTestImplementation(project(":sketch-test-singleton"))
    androidTestImplementation(project(":sketch-resources"))
}