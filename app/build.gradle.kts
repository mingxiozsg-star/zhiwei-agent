plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 本地 proot(aarch64) 需要 ARM64 版 aapt2；x86_64 的 CI 使用官方二进制
val isArm64Host = System.getProperty("os.arch")?.lowercase()?.let {
    it.contains("aarch64") || it.contains("arm64")
} ?: false

android {
    namespace = "com.zhiwei.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhiwei.agent"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 固定签名：keystore/zhiwei-agent.jks 随仓库提供，保证任何机器与 CI 产出的包都能互相覆盖安装
    val zhiweiKs = rootProject.file("keystore/zhiwei-agent.jks")
    val hasZhiweiKs = zhiweiKs.exists()

    signingConfigs {
        if (hasZhiweiKs) {
            create("zhiwei") {
                storeFile = zhiweiKs
                storePassword = "zhiwei123"
                keyAlias = "zhiwei"
                keyPassword = "zhiwei123"
                enableV1Signing = true
                enableV2Signing = true
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
            signingConfig = if (hasZhiweiKs) {
                signingConfigs.getByName("zhiwei")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Force use of ARM64 binaries for AAPT2 in Proot environment
if (isArm64Host) {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
                useTarget("com.android.tools.build:aapt2:${requested.version}:linux-aarch64")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}