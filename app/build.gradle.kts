import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Single version source: version.properties (MAJOR.MINOR.PATCH). versionCode is derived as
// major*10000 + minor*100 + patch; keep in sync with util/AppVersion.kt (covered by a unit test).
val appVersionName: String = Properties()
    .apply { rootProject.file("version.properties").inputStream().use { load(it) } }
    .getProperty("VERSION_NAME")?.trim().orEmpty()
val appVersionCode: Int = run {
    val m = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""").matchEntire(appVersionName)
    val parts = m?.destructured?.toList()?.map { it.toInt() }
    require(parts != null && parts[1] <= 99 && parts[2] <= 99) {
        "version.properties: VERSION_NAME must be MAJOR.MINOR.PATCH with minor and patch in 0..99, got '$appVersionName'"
    }
    parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

// Release signing values come from the git-ignored root local.properties, falling back to env vars
// of the same name (CI/other machines). With none set the release build stays unsigned; a partial
// set or a missing keystore file fails the configuration instead of silently shipping unsigned.
val signingKeys = listOf(
    "MICUADRE_KEYSTORE_FILE",
    "MICUADRE_KEYSTORE_PASSWORD",
    "MICUADRE_KEY_ALIAS",
    "MICUADRE_KEY_PASSWORD",
)
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val signingValues: Map<String, String> = signingKeys
    .mapNotNull { key ->
        (localProps.getProperty(key) ?: System.getenv(key))?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { key to it }
    }
    .toMap()
val hasReleaseSigning = signingValues.size == signingKeys.size
require(signingValues.isEmpty() || hasReleaseSigning) {
    "Incomplete release signing config; missing: " +
        signingKeys.filter { it !in signingValues }.joinToString()
}
val releaseKeystore: File? = signingValues["MICUADRE_KEYSTORE_FILE"]?.let { rootProject.file(it) }
require(releaseKeystore == null || releaseKeystore.isFile) {
    "MICUADRE_KEYSTORE_FILE points to a missing file: $releaseKeystore"
}

android {
    namespace = "com.micuadre.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.micuadre.app"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValues.getValue("MICUADRE_KEYSTORE_PASSWORD")
                keyAlias = signingValues.getValue("MICUADRE_KEY_ALIAS")
                keyPassword = signingValues.getValue("MICUADRE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

// Room's MigrationTestHelper reads the exported schemas from the assets Robolectric sees. Ship them
// in the debug source set only (~10 KB, debug APK) and disable the release unit-test variant so no
// test run ever needs them elsewhere. The release APK stays free of schemas.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { builder ->
        builder.enableUnitTest = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
