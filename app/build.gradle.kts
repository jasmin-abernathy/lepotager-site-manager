plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val managerApplicationId = providers.gradleProperty("managerApplicationId")
    .orElse("org.lepotager.sitemanager")
    .get()
val managerAppName = providers.gradleProperty("managerAppName")
    .orElse("Mon Manager Web")
    .get()
val managerLauncherIcon = providers.gradleProperty("managerLauncherIcon")
    .orElse("@mipmap/ic_launcher")
    .get()
val managerLauncherRoundIcon = providers.gradleProperty("managerLauncherRoundIcon")
    .orNull
    ?: managerLauncherIcon

android {
    namespace = "org.lepotager.sitemanager"
    compileSdk = 36

    defaultConfig {
        applicationId = managerApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue("string", "app_name", managerAppName)
        manifestPlaceholders["launcherIcon"] = managerLauncherIcon
        manifestPlaceholders["launcherRoundIcon"] = managerLauncherRoundIcon
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")

    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Utilisé uniquement pour normaliser les JPEG (notamment iPhone) avant upload :
    // l'orientation EXIF est appliquée aux pixels afin qu'elle survive au réencodage serveur.
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
