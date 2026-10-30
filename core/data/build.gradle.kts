plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nuvora.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // @Immutable ajuda o Compose a evitar redesenhos.
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    // ViewModel: guarda o estado da tela e sobrevive à rotação.
    api(libs.lifecycle.viewmodel.compose)
    api(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
}
