plugins {
    id("com.android.library")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "br.com.fpagency.fptube.engine"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    
    // Bibliotecas oficiais - Usando API para expor aos consumidores
    api("io.github.junkfood02.youtubedl-android:library:0.18.1")
    api("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Tests
    testImplementation(libs.junit)
}
