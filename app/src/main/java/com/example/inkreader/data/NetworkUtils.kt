package com.example.inkreader.data

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

object NetworkUtils {

    val trustAllSSLSocketFactory: SSLSocketFactory by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        sslContext.socketFactory
    }

    val trustAllHostnameVerifier: HostnameVerifier by lazy {
        HostnameVerifier { _, _ -> true }
    }

    fun configureSsl(conn: HttpsURLConnection, acceptCustomCerts: Boolean = true) {
        if (acceptCustomCerts) {
            try {
                conn.sslSocketFactory = trustAllSSLSocketFactory
                conn.hostnameVerifier = trustAllHostnameVerifier
            } catch (_: Exception) {}
        }
    }
}
