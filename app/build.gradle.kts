import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 从仓库外读取签名配置（不存在时 release 包保持未签名，不影响 debug）
val keystoreProps: Properties? = run {
    val candidates = listOf(
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), ".config/voiceassistant/keystore.properties"),
    )
    candidates.firstOrNull { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
}

android {
    namespace = "com.demian.voiceassistant"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.demian.voiceassistant"
        minSdk = 29
        targetSdk = 37
        versionCode = 7
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val props = keystoreProps
            if (props != null) {
                storeFile = File(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 注意：AGP 9 的 optimization（R8 类合并）在当前项目会触发
            // kotlin.text 的 IllegalAccessError，先关闭以保证正式包可运行。
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 本地拼音库（用于自定义唤醒词：中文 → 拼音 token）
    implementation(files("libs/pinyin4j-2.5.1.jar"))
    // 单元测试用的真实 org.json（Android 本地单测默认是桩实现）
    testImplementation(files("libs/json-20240303.jar"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}