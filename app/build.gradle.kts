import java.net.URL
import java.net.HttpURLConnection
import java.io.FileOutputStream
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.jovsecotech.nvyqzw"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.database)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

// Generates a beautiful synthesized elegant notification chime WAV at configuration/sync time
val rawDir = file("src/main/res/raw")
if (!rawDir.exists()) {
  rawDir.mkdirs()
}
val targetFile = file("src/main/res/raw/elegant_chime.wav")
if (!targetFile.exists()) {
  println("Generating elegant notification chime WAV...")
  val sampleRate = 22050
  val duration = 1.2
  val numSamples = (sampleRate * duration).toInt()
  val subChunk2Size = numSamples * 2
  val chunkSize = 36 + subChunk2Size

  FileOutputStream(targetFile).use { fos ->
    // RIFF header
    fos.write("RIFF".toByteArray(Charsets.US_ASCII))
    // Chunk Size
    fos.write(chunkSize and 0xFF)
    fos.write((chunkSize shr 8) and 0xFF)
    fos.write((chunkSize shr 16) and 0xFF)
    fos.write((chunkSize shr 24) and 0xFF)
    
    fos.write("WAVE".toByteArray(Charsets.US_ASCII))
    fos.write("fmt ".toByteArray(Charsets.US_ASCII))
    
    // Subchunk1Size (16)
    fos.write(16)
    fos.write(0)
    fos.write(0)
    fos.write(0)
    
    // AudioFormat (1)
    fos.write(1)
    fos.write(0)
    
    // NumChannels (1)
    fos.write(1)
    fos.write(0)
    
    // SampleRate (22050)
    fos.write(sampleRate and 0xFF)
    fos.write((sampleRate shr 8) and 0xFF)
    fos.write((sampleRate shr 16) and 0xFF)
    fos.write((sampleRate shr 24) and 0xFF)
    
    // ByteRate (44100)
    val byteRate = sampleRate * 2
    fos.write(byteRate and 0xFF)
    fos.write((byteRate shr 8) and 0xFF)
    fos.write((byteRate shr 16) and 0xFF)
    fos.write((byteRate shr 24) and 0xFF)
    
    // BlockAlign (2)
    fos.write(2)
    fos.write(0)
    
    // BitsPerSample (16)
    fos.write(16)
    fos.write(0)
    
    fos.write("data".toByteArray(Charsets.US_ASCII))
    
    // Subchunk2Size
    fos.write(subChunk2Size and 0xFF)
    fos.write((subChunk2Size shr 8) and 0xFF)
    fos.write((subChunk2Size shr 16) and 0xFF)
    fos.write((subChunk2Size shr 24) and 0xFF)

    // Mathematically generate a high-quality beautiful crystal chime chord
    // A chord of G6-A6 crystal bell:
    // Frequencies: 1568 Hz (G6), 1760 Hz (A6), 1975.5 Hz (B6) for a shimmering crystal chord.
    // Exponential decay envelope: exp(-4.5 * t) for a pure clean chime ring.
    val f1 = 1568.0
    val f2 = 1760.0
    val f3 = 1975.5
    val f4 = 3136.0 // Subtly higher overtone G7

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate.toDouble()
      // Exponential decay envelope
      val envelope = Math.exp(-4.5 * t)
      
      // Main crystal tones
      val wave = Math.sin(2.0 * Math.PI * f1 * t) * 0.45 +
                 Math.sin(2.0 * Math.PI * f2 * t) * 0.30 +
                 Math.sin(2.0 * Math.PI * f3 * t) * 0.15 +
                 Math.sin(2.0 * Math.PI * f4 * t) * 0.10
      
      val valueVal = (wave * envelope * 24000.0).toInt()
      
      // Clamp to short bounds just in case
      val clampedValue = Math.max(-32768, Math.min(32767, valueVal)).toShort()
      
      fos.write(clampedValue.toInt() and 0xFF)
      fos.write((clampedValue.toInt() shr 8) and 0xFF)
    }
  }
  println("Successfully generated the elegant notification chime.")
} else {
  println("Elegant notification chime already exists.")
}

