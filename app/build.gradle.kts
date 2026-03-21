import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val debugBaseUrl: String? =
  providers.gradleProperty("COSRAY_DEBUG_BASE_URL").getOrElse("http://59.66.16.192:8000")
val releaseBaseUrl: String? =
  providers.gradleProperty("COSRAY_RELEASE_BASE_URL").getOrElse("https://cosray.top")

val releaseCertPins: List<String> =
  providers
    .gradleProperty("COSRAY_RELEASE_CERT_PINS")
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotBlank) ?: listOf("sha256/5lyRQ3JztmzrjYDVkWCkL+ZvTglL6DqE72KrwbLrDW0=")

val releaseCertPinsLiteral =
  releaseCertPins.joinToString(prefix = "{", postfix = "}") { pin -> "\"$pin\"" }

plugins {
  alias(libs.plugins.android.application)
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
    buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
    buildConfigField("String[]", "CERT_PINS", "{}")
  }

  buildTypes {
    debug { buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"") }
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")

      buildConfigField("String[]", "CERT_PINS", releaseCertPinsLiteral)
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_25
    freeCompilerArgs.addAll(
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
      "-opt-in=kotlinx.coroutines.FlowPreview",
    )
  }
  jvmToolchain(25)
}

ktfmt {
  // Google style - 2 space indentation & automatically adds/removes trailing commas
  googleStyle()
}

detekt {
  config.setFrom("detekt-config.yml")
  baseline = file("detekt-baseline.xml")
}

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
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.errorprone.annotations)
  implementation(libs.ktor.client.android)
  implementation("io.ktor:ktor-client-okhttp:${libs.versions.ktor.get()}")
  implementation("io.ktor:ktor-client-core:${libs.versions.ktor.get()}")
  implementation(libs.ktor.client.auth)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.client.logging)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ble.ktx)
  implementation(libs.ble.scanner.compat)
  implementation(libs.dagger.hilt.android)
  ksp(libs.dagger.hilt.compiler)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.navigation.common.ktx)
  testImplementation(libs.junit)
  testImplementation(
    "org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}"
  )
  testImplementation("io.ktor:ktor-client-java:${libs.versions.ktor.get()}")
  testImplementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
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
      fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { exclude(excludes) },
      fileTree(
        layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
      ) {
        exclude(excludes)
      },
    )
  )

  sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

  executionData.setFrom(
    fileTree(layout.buildDirectory) {
      include(
        "jacoco/testDebugUnitTest.exec",
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
      )
    }
  )
}
