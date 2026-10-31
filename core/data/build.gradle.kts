plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nuvora.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    buildTypes {
        // Versão de teste otimizada: código de versão final, mas com a demonstração da versão de teste.
        create("otimizada") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }
    sourceSets {
        getByName("otimizada").java.srcDir("src/debug/java")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":source:api"))
    // @Immutable ajuda o Compose a evitar redesenhos.
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    // ViewModel: guarda o estado da tela e sobrevive à rotação.
    api(libs.lifecycle.viewmodel.compose)
    api(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
}
