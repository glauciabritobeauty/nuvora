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
        versionCode = 17
        versionName = "0.9.3"
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
        // Versão de teste OTIMIZADA (a que vai para o aparelho da Glaucia): o mesmo motor rápido da versão final
        // (R8, sem modo de depuração, perfis de inicialização), com a demonstração e as medições da versão de teste.
        // Mesma chave da versão de teste: instala por cima sem apagar as listas.
        create("otimizada") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            if (testKeystore != null) signingConfig = signingConfigs.getByName("teste")
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
    implementation(project(":core:data"))
    implementation(project(":feature:mobile"))
    implementation(project(":feature:tv"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    // Baixa as capas e logos pela internet.
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.compose.ui.tooling)
}
