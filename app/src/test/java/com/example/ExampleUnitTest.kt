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
  fun jsonPayloadStructure_isCorrect() {
    val emptyDbJson = """{"activities":[],"members":[],"notifications":[],"enrollments":[]}"""
    assertTrue(emptyDbJson.contains("activities"))
    assertTrue(emptyDbJson.contains("members"))
    assertTrue(emptyDbJson.contains("notifications"))
    assertTrue(emptyDbJson.contains("enrollments"))
  }
}

