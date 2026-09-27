plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.joaohouto.clusterplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joaohouto.clusterplayer"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/release.keystore")
            storePassword = "clusterplayer"
            keyAlias = "clusterplayer"
            keyPassword = "clusterplayer"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // AndroidX Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

project.afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") }.configureEach {
        doLast {
            val isRelease = name.contains("Release", ignoreCase = true)
            val subfolder = if (isRelease) "release" else "debug"
            val apkDir = layout.buildDirectory.dir("outputs/apk/$subfolder").orNull?.asFile ?: return@doLast
            val defaultApk = File(apkDir, if (isRelease) "app-release.apk" else "app-debug.apk")
            val targetApk = File(apkDir, if (isRelease) "ClusterPlayer-v${android.defaultConfig.versionName}.apk" else "ClusterPlayer-v${android.defaultConfig.versionName}-debug.apk")
            if (defaultApk.exists()) {
                defaultApk.copyTo(targetApk, overwrite = true)
                defaultApk.copyTo(File(apkDir, if (isRelease) "ClusterPlayer.apk" else "ClusterPlayer-debug.apk"), overwrite = true)
                println("APK generated: ${targetApk.name}")
            }
        }
    }
}