plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.panoramapro"
    compileSdk {
        version = release(36)
    }

    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.example.panoramapro"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(
                files("../../opencv/sdk/native/libs"), // OpenCV 的路径
                files("../../onnxruntime/lib") // ONNX 的路径
            )
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 1. Jetpack Navigation (用于 Fragment 跳转)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // 2. ViewModel (用于管理 UI 数据)
    implementation(libs.androidx.lifecycle.viewmodel)

    // 3. Glide (用于加载图片)
    implementation(libs.glide)
    // 如果后续使用 Glide 的注解生成器，可能需要添加: annotationProcessor(libs.glide.compiler)
}