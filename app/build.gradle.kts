import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.rebuildit.prestaflow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rebuildit.prestaflow"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("preprod") {
            dimension = "environment"
            applicationIdSuffix = ".preprod"
            versionNameSuffix = "-preprod"
            resValue("string", "build_config_name", "Préproduction")
            resValue("string", "api_base_url", "https://preprod.example.com/module/rebuildconnector/api/")
            buildConfigField("String", "API_BASE_URL", "\"https://preprod.example.com/module/rebuildconnector/api/\"")
            buildConfigField("String", "ENVIRONMENT_NAME", "\"PREPROD\"")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "build_config_name", "Production")
            resValue("string", "api_base_url", "https://prod.example.com/module/rebuildconnector/api/")
            buildConfigField("String", "API_BASE_URL", "\"https://prod.example.com/module/rebuildconnector/api/\"")
            buildConfigField("String", "ENVIRONMENT_NAME", "\"PROD\"")
        }
    }

    buildFeatures {
        compose = true
        viewBinding = false
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    composeOptions {
        kotlinCompilerExtensionVersion =
            (project.findProperty("compose.compiler.version") as String?) ?: "1.5.13"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.txt}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("robolectric.logging", "stdout")
            }
        }
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xcontext-receivers",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlin.ExperimentalStdlibApi"
        )
    }
}

hilt {
    enableAggregatingTask = true
}

kapt {
    correctErrorTypes = true
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    outputToConsole.set(true)
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

detekt {
    config.from(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.google.material)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.util)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    kapt(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.navigation.material)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore)
    implementation(libs.timber)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    kapt(libs.androidx.hilt.compiler)

    kaptTest(libs.androidx.room.compiler)
    kaptAndroidTest(libs.androidx.room.compiler)
    kaptTest(libs.hilt.compiler)
    kaptAndroidTest(libs.hilt.compiler)
    kaptTest(libs.androidx.hilt.compiler)
    kaptAndroidTest(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
