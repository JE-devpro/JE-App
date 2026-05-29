package com.example

import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun generateNewJsonBlob() {
    val client = OkHttpClient()
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val emptyDbJson = """{"activities":[],"members":[],"notifications":[],"enrollments":[]}"""
    
    val request = Request.Builder()
        .url("https://jsonblob.com/api/jsonBlob")
        .post(emptyDbJson.toRequestBody(mediaType))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .build()
        
    client.newCall(request).execute().use { response ->
        assertTrue(response.isSuccessful)
        val location = response.header("Location") ?: response.header("location")
        println("GENERATED_URL_START: " + location + " :GENERATED_URL_END")
        assertNotNull(location)
    }
  }
}

