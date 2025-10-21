import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    id("jacoco")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("google-services.json not found, Firebase push registration will run in limited mode.")
}

android {
    namespace = "com.rebuildit.prestaflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rebuildit.prestaflow"
        minSdk = 26
        targetSdk = 35
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
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
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
            isReturnDefaultValues = true
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

// ═══════════════════════════════════════════════════════════════
// Configuration JaCoCo pour la couverture de code
// ═══════════════════════════════════════════════════════════════

jacoco {
    toolVersion = "0.8.11"
}

// Créer des tâches JaCoCo pour chaque variant
android.applicationVariants.all {
    val variantName = name
    val variantCapitalized = variantName.replaceFirstChar { it.uppercase() }
    val testTaskName = "test${variantCapitalized}UnitTest"
    
    tasks.register<JacocoReport>("jacoco${variantCapitalized}TestReport") {
        description = "Generate Jacoco coverage report for $variantName variant"
        group = "verification"
        
        dependsOn(testTaskName)
        
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
        
        val javaTree = fileTree("${layout.buildDirectory.get()}/intermediates/javac/$variantName/classes") {
            exclude(
                "**/R.class",
                "**/R\$*.class",
                "**/BuildConfig.*",
                "**/Manifest*.*",
                "**/*Test*.*",
                "android/**/*.*"
            )
        }
        
        val kotlinTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/$variantName") {
            exclude(
                "**/R.class",
                "**/R\$*.class",
                "**/BuildConfig.*",
                "**/Manifest*.*",
                "**/*Test*.*",
                "android/**/*.*",
                "**/*\$Lambda$*.*",
                "**/*\$inlined$*.*",
                // Exclusions Dagger/Hilt
                "**/di/**",
                "**/*_Factory.*",
                "**/*_MembersInjector.*",
                "**/*Module.*",
                "**/*Module\$*.*",
                "**/*Dagger*.*",
                "**/*Hilt*.*",
                "**/*_HiltModules*.*",
                "**/*_ComponentTreeDeps*.*",
                "**/*_Provide*Factory*.*",
                // Exclusions générées
                "**/databinding/**",
                "**/android/databinding/**",
                "**/androidx/databinding/**",
                "**/BR.*",
                "**/DataBindingInfo.*"
            )
        }
        
        classDirectories.setFrom(files(javaTree, kotlinTree))
        
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include("jacoco/$testTaskName.exec")
            }
        )
        
        sourceDirectories.setFrom(
            files(
                "$projectDir/src/main/java",
                "$projectDir/src/main/kotlin",
                "$projectDir/src/$variantName/java",
                "$projectDir/src/$variantName/kotlin"
            )
        )
        
        doLast {
            val reportPath = reports.html.outputLocation.get().asFile.absolutePath
            println("✅ Coverage report generated: file://$reportPath/index.html")
        }
    }
}

// Tâche globale pour générer tous les rapports de couverture
tasks.register("jacocoTestReport") {
    description = "Generate Jacoco coverage reports for all debug variants"
    group = "verification"
    
    dependsOn(
        "jacocoTestPreprodDebugUnitTest",
        "jacocoPreprodDebugTestReport",
        "jacocoTestProdDebugUnitTest",
        "jaccooProdDebugTestReport"
    )
    
    doLast {
        println("\n╔═══════════════════════════════════════════════════════════╗")
        println("║           📊 Coverage Reports Generated                  ║")
        println("╚═══════════════════════════════════════════════════════════╝")
        println("\n📁 Preprod Debug:")
        println("   file://${layout.buildDirectory.get()}/reports/jacoco/jaccooPreprodDebugTestReport/html/index.html")
        println("\n📁 Prod Debug:")
        println("   file://${layout.buildDirectory.get()}/reports/jacoco/jaccooProdDebugTestReport/html/index.html\n")
    }
}

// Tâche de vérification de couverture minimale
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    description = "Verify minimum code coverage thresholds"
    group = "verification"
    
    dependsOn("jacocoTestReport")
    
    violationRules {
        rule {
            limit {
                minimum = "0.30".toBigDecimal() // 30% de couverture minimale
            }
        }
        
        rule {
            enabled = true
            element = "CLASS"
            
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.20".toBigDecimal()
            }
            
            excludes = listOf(
                "*.di.*",
                "*.BuildConfig",
                "*.*Test",
                "*.R",
                "*.R\$*",
                "*.*Module",
                "*.*Dagger*",
                "*.*Hilt*"
            )
        }
    }
}

// Améliorer les logs des tests
tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    val htmlReportDir = reports.html.outputLocation

    doFirst {
        println("\n🧪 Running tests for ${this.name}...")
    }
    
    doLast {
        println("✅ Tests completed for ${this.name}")
        println("   Results: ${htmlReportDir.get().asFile.absolutePath}\n")
    }
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
    implementation(libs.androidx.compose.material3.windowsizeclass)
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
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    kapt(libs.androidx.hilt.compiler)

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

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
