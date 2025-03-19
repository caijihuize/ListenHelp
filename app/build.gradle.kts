plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.listenhelp6"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.listenhelp6"
        minSdk = 26  // AAudio需要Android 8.0 (API 26)及以上
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 添加对AAudio的支持
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
                cppFlags("-std=c++14")
            }
        }
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
    
    // 配置CMake构建
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}