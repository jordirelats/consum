plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cat.jordi.consum"
    compileSdk = 35

    defaultConfig {
        applicationId = "cat.jordi.consum"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "0.25"
        // Tesseract porta codi natiu: només el dels mòbils (a la versió de prova, també el de l'emulador)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        debug {
            ndk { abiFilters += "x86_64" }
        }
        release {
            // Signada amb la mateixa clau que les versions de prova, perquè s'actualitzin a sobre sense desinstal·lar
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // Els idiomes de l'app surten de les carpetes res/values-xx: per afegir-ne un, n'hi ha prou amb la carpeta
    androidResources {
        generateLocaleConfig = true
    }
    // Una traducció a mitges no ha d'aturar la compilació: el que falta surt en català
    lint {
        warning += "MissingTranslation"
    }
    // El codi natiu comprimit dins de l'APK: es baixa la meitat i Android el descomprimeix en instal·lar
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
}
