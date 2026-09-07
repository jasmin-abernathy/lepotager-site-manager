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

        // Un seul moteur Android, mais une identité d'installation peut être fournie au build
        // pour un client donné sans forker le code : nom, applicationId et icône du launcher.
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
    // Pile volontairement conservatrice : compatible AGP 8.10.1 + compileSdk 36.
    // Elle évite qu'une mise à jour transitive de Compose/Coil impose SDK 37/AGP 9.1
    // avant que notre chaîne de build commune soit prête à cette migration.
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
    implementation("com.materialkolor:material-kolor:2.1.1")

    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Credential Manager permet au gestionnaire de mots de passe Android de proposer
    // les identifiants du site sans que notre application conserve le mot de passe.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")

    // Association QR avec le scanner Google Play Services : pas de permission caméra
    // persistante demandée par l'application elle-même.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
