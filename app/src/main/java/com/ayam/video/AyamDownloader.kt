package com.ayam.video

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.HttpURLConnection
import java.net.URL

class AyamDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        conn.requestMethod = request.httpMethod()
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        )

        for ((name, values) in request.headers()) {
            conn.setRequestProperty(name, values.firstOrNull() ?: "")
            for (i in 1 until values.size) {
                conn.addRequestProperty(name, values[i])
            }
        }

        val data = request.dataToSend()
        if (data != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(data) }
        }

        val code = conn.responseCode
        if (code == 429) {
            conn.disconnect()
            throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

        val headers = HashMap<String, List<String>>()
        for ((k, v) in conn.headerFields) {
            if (k != null) headers[k] = v
        }

        val response = Response(
            code,
            conn.responseMessage ?: "",
            headers,
            body,
            conn.url.toString()
        )
        conn.disconnect()
        return response
    }
}
