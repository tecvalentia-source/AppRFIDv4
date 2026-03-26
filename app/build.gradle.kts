plugins {
    alias(libs.plugins.android.application)
}

android {
    // Definimos el namespace una sola vez
    namespace = "com.confa.apprfid"
    compileSdk = 35 // Te sugiero usar 35, la 36 es aún experimental

    defaultConfig {
        applicationId = "com.confa.apprfid"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    // Librerías base desde el catálogo (libs.versions.toml)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    // Si libs.recyclerview te da error, usa: implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.recyclerview)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Soporte para archivos locales en la carpeta libs
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // --- SECCIÓN DE EXCEL (Apache POI) ---
    val poiVersion = "5.2.3" // Definida directamente para evitar errores de catálogo
    implementation("org.apache.poi:poi:$poiVersion")
    implementation("org.apache.poi:poi-ooxml:$poiVersion")

    // --- SECCIÓN DE GRÁFICAS (MPAndroidChart) ---
    // Esta es la línea que resuelve tus errores de import de gráficas
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}