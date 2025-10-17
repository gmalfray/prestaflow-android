plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Désactiver temporairement KAPT pour révéler les vraies erreurs
// subprojects {
//     tasks.configureEach {
//         if (name.contains("kapt", ignoreCase = true)) {
//             enabled = false
//         }
//     }
// }

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

tasks.register("testDebugUnitTest") {
    description = "Runs unit tests for all debug variants."
    group = "verification"
    dependsOn(
        ":app:testPreprodDebugUnitTest",
        ":app:testProdDebugUnitTest"
    )
}
