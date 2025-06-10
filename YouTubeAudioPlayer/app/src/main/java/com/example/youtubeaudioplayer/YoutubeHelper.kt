package com.example.youtubeaudioplayer

import android.content.Context
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Headers

object YoutubeHelper {

    fun initialize(context: Context) {
        NewPipe.init(getDownloader())
        // You can add application context if some parts of NewPipeExtractor require it
        // NewPipe.setContext(context) // If needed
    }

    private fun getDownloader(): Downloader {
        return object : Downloader() {
            val client = OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .build()

            override fun execute(request: Request): Response {
                val okHttpRequestBuilder = okhttp3.Request.Builder()
                    .url(request.url())
                    .method(request.httpMethod(), request.dataToSend()?.let { RequestBody.create(null, it) })

                val headers = Headers.Builder()
                for ((headerName, headerValueList) in request.headers()) {
                    for (headerValue in headerValueList) {
                        headers.add(headerName, headerValue)
                    }
                }
                okHttpRequestBuilder.headers(headers.build())

                val okHttpResponse = client.newCall(okHttpRequestBuilder.build()).execute()

                if (okHttpResponse.code == 429) { // Too Many Requests
                    throw ReCaptchaException("ReCaptcha Challenge requested", request.url())
                }

                return Response(
                    okHttpResponse.code,
                    okHttpResponse.message,
                    okHttpResponse.headers.toMultimap(),
                    okHttpResponse.body?.string(),
                    request.url()
                )
            }

            // The following methods are deprecated in newer Downloader versions but might be needed for compatibility
            // or if NewPipeExtractor internally still relies on some of their abstractions.
            // If using a very new NewPipeExtractor, these might not be required or need different implementation.

            @Throws(IOException::class, ReCaptchaException::class)
            override fun get(url: String?, headers: Map<String?, List<String?>?>?, dataToSend: ByteArray?): String? {
                 val request = Request.RequestInfo(url, "GET")
                    .setHeaders(headers)
                    .setDataToSend(dataToSend)
                 return execute(request).body
            }

            @Throws(IOException::class, ReCaptchaException::class)
            override fun get(url: String?): String? {
                return get(url, emptyMap(), null)
            }

            @Throws(IOException::class, ReCaptchaException::class)
            override fun post(url: String?, headers: Map<String?, List<String?>?>?, dataToSend: ByteArray?): String? {
                 val request = Request.RequestInfo(url, "POST")
                    .setHeaders(headers)
                    .setDataToSend(dataToSend)
                 return execute(request).body
            }

            @Throws(IOException::class, ReCaptchaException::class)
            override fun head(url: String?, headers: Map<String?, List<String?>?>?): String? {
                 val request = Request.RequestInfo(url, "HEAD")
                    .setHeaders(headers)
                 return execute(request).body // Or just return headers if that's what HEAD is for
            }
        }
    }
}
