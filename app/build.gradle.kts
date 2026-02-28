import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ksp)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.detekt)
  alias(libs.plugins.ktfmt)
  jacoco
}

android {
  namespace = "com.grid.cosrayapp"
  compileSdk { version = release(36) }

  defaultConfig {
    applicationId = "com.grid.cosrayapp"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "BASE_URL", "\"http://192.168.3.4:8000\"")
  }

  buildTypes {
    debug { buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000\"") }
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_21
    freeCompilerArgs.addAll(
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
      "-opt-in=kotlinx.coroutines.FlowPreview",
    )
  }
  jvmToolchain(21)
}

ktfmt {
  // Google style - 2 space indentation & automatically adds/removes trailing commas
  googleStyle()
}

detekt { config.setFrom("detekt-config.yml") }

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended:1.7.8")
  implementation(libs.androidx.navigation.compose)
  implementation(libs.accompanist.permissions)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okio)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.ktor.client.android)
  implementation("io.ktor:ktor-client-core:${libs.versions.ktor.get()}")
  implementation(libs.ktor.client.auth)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.client.logging)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ble.ktx)
  implementation(libs.ble.scanner.compat)
  implementation(libs.dagger.hilt.android)
  ksp(libs.dagger.hilt.compiler)
  implementation(libs.androidx.navigation.common.ktx)
  testImplementation(libs.junit)
  testImplementation("io.ktor:ktor-client-java:${libs.versions.ktor.get()}")
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
  dependsOn("testDebugUnitTest")

  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
  }

  val excludes =
    listOf(
      "**/R.class",
      "**/R$*.class",
      "**/BuildConfig.*",
      "**/Manifest*.*",
      "**/*Test*.*",
      "android/**/*.*",
      "**/*_Factory*.*",
      "**/*_MembersInjector*.*",
      "**/Dagger*.*",
      "**/Hilt*.*",
      "**/*_HiltModules*.*",
      "**/*_HiltComponents*.*",
    )

  classDirectories.setFrom(
    files(
      fileTree("$buildDir/tmp/kotlin-classes/debug") {
        exclude(excludes)
      },
      fileTree("$buildDir/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
        exclude(excludes)
      },
    )
  )

  sourceDirectories.setFrom(
    files(
      "src/main/java",
      "src/main/kotlin",
    )
  )

  executionData.setFrom(
    fileTree(buildDir) {
      include(
        "jacoco/testDebugUnitTest.exec",
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
      )
    }
  )
}
