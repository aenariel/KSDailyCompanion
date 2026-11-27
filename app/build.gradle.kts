import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

// Release signing comes from the CI secrets (see README). Without them the release build is signed with the debug key.
val keystorePath: String? = System.getenv("KS_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "io.github.aenariel.ksdailycompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.aenariel.ksdailycompanion"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        if (keystorePath != null) create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("KS_STORE_PASSWORD")
            keyAlias = System.getenv("KS_KEY_ALIAS")
            keyPassword = System.getenv("KS_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystorePath != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
}

// Full causes in the CI log when a test fails (the report artifact may not be readable from everywhere)
tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

// Paparazzi's layoutlib needs Guava's -jre variant (known issue in Paparazzi 1.3.x)
dependencies.constraints {
    add("testImplementation", "com.google.guava:guava") {
        attributes {
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM))
        }
        because("LayoutLib and sdk-common depend on Guava's -jre published variant.")
    }
}
