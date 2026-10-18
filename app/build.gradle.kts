plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.micuadre.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.micuadre.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
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
    }
}

// Room's MigrationTestHelper reads the exported schemas from the assets Robolectric sees, which are
// the merged assets of the variant under test. Copy them in right before each unit-test run (instead
// of declaring them as a source set) so they are available to debug and release unit tests without
// being packaged into the APK: they are removed again once the tests finish.
afterEvaluate {
    listOf("Debug", "Release").forEach { variant ->
        val mergedAssets = tasks.named("merge${variant}Assets")
        val copySchemas = tasks.register<Copy>("copy${variant}SchemasForUnitTests") {
            dependsOn(mergedAssets)
            from(layout.projectDirectory.dir("schemas"))
            into(mergedAssets.map { (it.outputs.files.singleFile) })
        }
        val removeSchemas = tasks.register<Delete>("remove${variant}SchemasAfterUnitTests") {
            delete(mergedAssets.map { it.outputs.files.singleFile.resolve("com.micuadre.app.data.local.MiCuadreDatabase") })
        }
        tasks.named("test${variant}UnitTest") {
            dependsOn(copySchemas)
            finalizedBy(removeSchemas)
        }
        tasks.named("package${variant}UnitTestForUnitTest") { dependsOn(copySchemas) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
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
