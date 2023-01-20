/**
 * https://medium.com/mobile-app-development-publication/download-file-in-android-with-kotlin-874d50bccaa2
 * https://github.com/elye/demo_android_pdf_reader_viewpager2
 * https://github.com/elye/demo_android_pdf_reader_viewpager2/blob/master/app/src/main/java/com/example/pdfreader/FileDownloader.kt
 */
package dev.csaba.armap.treewalk.helpers

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

class FileDownloader(okHttpClient: OkHttpClient) {
  companion object {
    private const val BUFFER_LENGTH_BYTES = 1024 * 8
    private const val HTTP_TIMEOUT = 30
  }

  private var okHttpClient: OkHttpClient

  init {
    val okHttpBuilder = okHttpClient.newBuilder()
      .connectTimeout(HTTP_TIMEOUT.toLong(), TimeUnit.SECONDS)
      .readTimeout(HTTP_TIMEOUT.toLong(), TimeUnit.SECONDS)
    this.okHttpClient = okHttpBuilder.build()
  }

  fun download(url: String, file: File): Long {
    var length = 0L
    val request = Request.Builder().url(url).build()
    val response = okHttpClient.newCall(request).execute()
    val body = response.body
    val responseCode = response.code
    if (responseCode >= HttpURLConnection.HTTP_OK &&
      responseCode < HttpURLConnection.HTTP_MULT_CHOICE &&
      body != null)
    {
      length = body.contentLength()
      body.byteStream().apply {
        file.outputStream().use { fileOut ->
          var bytesCopied = 0
          val buffer = ByteArray(BUFFER_LENGTH_BYTES)
          var bytes = read(buffer)
          while (bytes >= 0) {
            fileOut.write(buffer, 0, bytes)
            bytesCopied += bytes
            bytes = read(buffer)
          }
        }
      }
    }

    return length
  }
}
