import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Push notifications: the Google services plugin needs composeApp/google-services.json from the
// Firebase console. Without it the app still builds and runs, just without push.
// Crash reporting (Firebase Crashlytics) uses the same file.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
} else {
    logger.warn("Notiz: composeApp/google-services.json not found — push notifications are disabled on Android.")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.material.icons.core)
            implementation(compose.components.resources)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)

            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.functions)
            implementation(libs.supabase.storage)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core) // FileProvider for sharing receipts / exports
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.firebase.crashlytics)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Strings live in src/commonMain/composeResources (values = English, values-ta = Tamil)
compose.resources {
    packageOfResClass = "com.notiz.app.resources"
    publicResClass = false
}

// Release signing: keystore.properties in the project root (never commit it), or the
// NOTIZ_KEYSTORE_* environment variables on CI. See docs/RELEASE.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "NOTIZ_KEYSTORE_FILE")

android {
    namespace = "com.notiz.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.notiz.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (findProperty("notiz.versionCode") as String? ?: "1").toInt()
        versionName = findProperty("notiz.versionName") as String? ?: "0.1.0"
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "NOTIZ_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NOTIZ_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NOTIZ_KEY_PASSWORD")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("Notiz: no keystore.properties — the release build will be unsigned (see docs/RELEASE.md).")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}

