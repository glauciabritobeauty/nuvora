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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// A versão otimizada usa a mesma demonstração da versão de teste (pasta src/debug).
androidComponents {
    onVariants(selector().withBuildType("otimizada")) { variant ->
        val kotlin = variant.sources.kotlin
        if (kotlin != null) kotlin.addStaticSourceDirectory("src/debug/java")
        else variant.sources.java?.addStaticSourceDirectory("src/debug/java")
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
