import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("com.google.firebase.crashlytics")
}

// LÓGICA DE VERSÃO
// LÓGICA DE VERSÃO
val versionFile = file("version.properties")
fun currentVersion(): Int {
    val p = Properties()
    var current = 1

    // 1. Lê a versão atual do arquivo
    if (versionFile.exists()) {
        versionFile.inputStream().use { p.load(it) }
        current = p.getProperty("VERSION_CODE", "1").toInt()
    }

    // 2. A MÁGICA QUE SUMIU: Soma +1 e salva de volta no arquivo!
    val nextVersion = current + 1
    p.setProperty("VERSION_CODE", nextVersion.toString())
    versionFile.outputStream().use { p.store(it, "Versão atualizada automaticamente pelo Gradle") }

    return current
}
// No GitHub Actions a versão vem da tag (-PversionCodeCI=N) e o arquivo não é tocado
val verCode = (findProperty("versionCodeCI") as String?)?.toInt() ?: currentVersion()

android {
    namespace = "fluxai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "fluxai.app"
        minSdk = 24
        targetSdk = 36
        versionCode = verCode
        versionName = "1.0.$verCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Chave que assina o app (a mesma desde a primeira versão). O login do Google no Firebase está
    // ligado à impressão digital dela: trocar de chave quebra o login e impede atualizar por cima.
    // No GitHub Actions o arquivo vem do segredo DEBUG_KEYSTORE, pelo caminho em FLUXAI_KEYSTORE.
    signingConfigs {
        create("fluxai") {
            storeFile = file(System.getenv("FLUXAI_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Duas formas de distribuir o mesmo app:
    // - site: APK baixado do site, que se atualiza sozinho pelo versao.json
    // - loja: AAB do Google Play, sem auto-atualização nem permissão de instalar pacotes (a loja não aceita)
    flavorDimensions += "distribuicao"
    productFlavors {
        create("site") {
            dimension = "distribuicao"
            buildConfigField("boolean", "AUTO_ATUALIZACAO", "true")
        }
        create("loja") {
            dimension = "distribuicao"
            buildConfigField("boolean", "AUTO_ATUALIZACAO", "false")
        }
    }

    buildTypes {
        release {
            // R8: ofusca o código e remove o que não é usado (dificulta extrair chaves e reduz o APK)
            isMinifyEnabled = true
            isShrinkResources = true
            // Mesma chave dos builds de debug: instala por cima das versões já distribuídas sem apagar dados
            signingConfig = signingConfigs.getByName("fluxai")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17 // O erro fatal do "VERSION_1" estava aqui
    }

    // Padrão correto e moderno para alinhar o Kotlin com o Java 17
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Firebase e IA
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation("com.google.firebase:firebase-storage")      // comprovantes
    implementation("com.google.firebase:firebase-crashlytics")  // relatório de falhas

    // Navegação e Utilidades
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.animation.core.lint)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.google.googleid)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")  // boleto e Pix
    // Testes
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303") // org.json real nos testes (o do Android é só um esqueleto)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

}

// As chaves de IA ficam só no servidor (Cloud Function groqChat). Não deixa o plugin embuti-las no BuildConfig/APK.
secrets {
    ignoreList.add("GROQ_API_KEY_.*")
    ignoreList.add("GEMINI_API_KEY")
}
