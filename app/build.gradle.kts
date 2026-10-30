plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nuvora.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nuvora.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
    }

    // Chave fixa das versões de teste (vem dos segredos do GitHub na compilação na nuvem).
    // Com ela, cada versão nova instala por cima da anterior sem apagar os dados.
    val testKeystore = System.getenv("NUVORA_TESTE_KEYSTORE")
    signingConfigs {
        if (testKeystore != null) {
            create("teste") {
                storeFile = file(testKeystore)
                storePassword = System.getenv("NUVORA_TESTE_SENHA")
                keyAlias = "nuvora-teste"
                keyPassword = System.getenv("NUVORA_TESTE_SENHA")
            }
        }
    }

    buildTypes {
        debug {
            if (testKeystore != null) signingConfig = signingConfigs.getByName("teste")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":feature:mobile"))
    implementation(project(":feature:tv"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
}
