plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.google.services) apply false
    id("org.owasp.dependencycheck") version "9.0.9"
}

// Configuration globale pour tous les sous-projets
subprojects {
    // Appliquer JaCoCo à tous les modules
    afterEvaluate {
        if (plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")) {
            apply(plugin = "jacoco")
            
            extensions.configure<JacocoPluginExtension> {
                toolVersion = "0.8.12"
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

dependencyCheck {
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.absolutePath
    formats = listOf("HTML", "JSON")
}

val nvdApiKey: String? = (findProperty("dependencyCheck.nvd.apiKey") as? String)?.takeIf { it.isNotBlank() }
    ?: System.getenv("NVD_API_KEY")?.takeIf { it.isNotBlank() }

dependencyCheck {
    nvdApiKey?.let { nvd.apiKey = it }
}

tasks.withType<org.owasp.dependencycheck.gradle.tasks.AbstractAnalyze>().configureEach {
    notCompatibleWithConfigurationCache("DependencyCheck plugin accesses project model during execution.")
    doFirst {
        if (nvdApiKey == null) {
            throw GradleException(
                "NVD API key not configured. Set the 'NVD_API_KEY' environment variable or define 'dependencyCheck.nvd.apiKey=<your-key>' in gradle.properties."
            )
        }
    }
}

tasks.register("testDebugUnitTest") {
    description = "Runs unit tests for all debug variants."
    group = "verification"
    dependsOn(
        ":app:testPreprodDebugUnitTest",
        ":app:testProdDebugUnitTest"
    )
}

// Tâche pour générer tous les rapports de couverture
tasks.register("jacocoTestReport") {
    description = "Generate Jacoco coverage reports for all debug variants."
    group = "verification"
    dependsOn("testDebugUnitTest")
    
    doLast {
        println("\n╔═══════════════════════════════════════════════════════════╗")
        println("║         ✅ Coverage Reports Generated                    ║")
        println("╚═══════════════════════════════════════════════════════════╝")
        println("\n📊 Open the HTML reports in your browser:")
        println("   • Preprod Debug")
        println("   • Prod Debug")
        println("\n💡 Tip: Use './gradlew jacocoTestCoverageVerification' to check coverage thresholds\n")
    }
}

tasks.register("lintDebug") {
    description = "Run Android lint on all debug product flavors."
    group = "verification"
    dependsOn(
        ":app:lintPreprodDebug",
        ":app:lintProdDebug"
    )
}

// Tâche de vérification de qualité globale
tasks.register("qualityCheck") {
    description = "Run all quality checks (tests, lint, detekt)"
    group = "verification"
    
    dependsOn(
        "testDebugUnitTest",
        "lintDebug",
        ":app:detekt"
    )
    
    doLast {
        println("\n╔═══════════════════════════════════════════════════════════╗")
        println("║              ✅ Quality Check Completed                  ║")
        println("╚═══════════════════════════════════════════════════════════╝")
        println("\n✨ All quality checks passed successfully!\n")
    }
}

// Tâche pour afficher un résumé du projet
tasks.register("projectInfo") {
    description = "Display project information"
    group = "help"
    
    doLast {
        println("""
            
            ╔═══════════════════════════════════════════════════════════╗
            ║              PrestaFlow Android Project                  ║
            ╚═══════════════════════════════════════════════════════════╝
            
            📦 Project: ${rootProject.name}
            🏗️  Gradle: ${gradle.gradleVersion}
            ☕ Java: ${System.getProperty("java.version")}
            🎯 Kotlin: 2.0.21
            
            🚀 Available tasks:
               • ./gradlew assembleDebug          - Build debug APKs
               • ./gradlew testDebugUnitTest      - Run all unit tests
               • ./gradlew jacocoTestReport       - Generate coverage reports
               • ./gradlew qualityCheck           - Run all quality checks
               • ./gradlew lintDebug              - Run lint analysis
               • ./gradlew detekt                 - Run static code analysis
               • ./gradlew ktlintCheck            - Check code style
               • ./gradlew clean                  - Clean build artifacts
            
            📱 Build variants:
               • preprodDebug
               • prodDebug
               • preprodRelease
               • prodRelease
            
            🔗 More info: https://github.com/yourrepo/prestaflow-android
            
        """.trimIndent())
    }
}

// Tâche pour nettoyer en profondeur
tasks.register<Delete>("cleanAll") {
    description = "Clean all build artifacts including caches"
    group = "build"
    
    delete(
        layout.buildDirectory,
        fileTree(rootDir) { include("**/.gradle") },
        fileTree(rootDir) { include("**/build") }
    )
    
    doLast {
        println("\n✨ All build artifacts and caches cleaned!\n")
    }
}

// Configuration pour afficher les warnings Gradle
gradle.projectsEvaluated {
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }
}
