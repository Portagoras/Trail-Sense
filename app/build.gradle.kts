plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("kotlin-parcelize")
}

android {
    compileSdk = 34

    defaultConfig {
        vectorDrawables.useSupportLibrary = true
        applicationId = "com.kylecorry.trail_sense"
        minSdk = 23
        targetSdk = 34
        versionCode = 103
        versionName = "5.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources {
        generateLocaleConfig = true
    }
    buildFeatures {
        viewBinding = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("beta") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = " (Beta)"
        }
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packagingOptions {
        resources.merges += "META-INF/LICENSE.md"
        resources.merges += "META-INF/LICENSE-notice.md"
        jniLibs {
            useLegacyPackaging = true
        }
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    lint {
        abortOnError = false
    }
    namespace = "com.kylecorry.trail_sense"
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    // AndroidX
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    val navVersion = "2.5.3"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    val roomVersion = "2.5.2"
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    val lifecycleVersion = "2.6.1"
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    val cameraxVersion = "1.2.3"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Material
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    // Andromeda
    val andromedaVersion = "6.2.0"
    implementation("com.github.kylecorry31.andromeda:core:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:fragments:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:forms:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:csv:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:background:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:location:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:camera:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:gpx:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:sound:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:sense:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:signal:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:preferences:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:permissions:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:canvas:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:files:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:notify:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:alerts:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:pickers:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:list:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:qr:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:markdown:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:camera:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:clipboard:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:buzz:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:torch:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:battery:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:compression:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:pdf:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:exceptions:$andromedaVersion")
    implementation("com.github.kylecorry31.andromeda:print:$andromedaVersion")

    // Ceres
    val ceresVersion = "0.4.0"
    implementation("com.github.kylecorry31.ceres:list:$ceresVersion")
    implementation("com.github.kylecorry31.ceres:toolbar:$ceresVersion")
    implementation("com.github.kylecorry31.ceres:badge:$ceresVersion")
    implementation("com.github.kylecorry31.ceres:chart:$ceresVersion")
    implementation("com.github.kylecorry31.ceres:image:$ceresVersion")

    // Misc
    implementation("com.github.kylecorry31:subsampling-scale-image-view:3.11.9")
    implementation("com.github.kylecorry31:sol:7.1.2")
    implementation("com.github.kylecorry31:luna:6a88851e2b")
//    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    testImplementation("org.junit.platform:junit-platform-runner:1.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.0.0")
}
