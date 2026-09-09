import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "me.foxtails.palustris"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "me.foxtails.palustris"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    // *Test classes, including MisskeySourceContractTest, are discovered automatically.
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildTypes { release { optimization { enable = true } } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<Test>().configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.60.1")
    kapt("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.github.awxkee:avif-coder:2.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.unifiedpush.connector)
    implementation(libs.androidx.lifecycle.process)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    implementation(libs.androidx.core.ktx)
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui:1.9.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.5")
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.9.5")
    testImplementation("androidx.test:core:1.7.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.5")
}

kapt { correctErrorTypes = true }
