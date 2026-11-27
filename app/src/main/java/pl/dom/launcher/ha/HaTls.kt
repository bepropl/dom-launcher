package pl.dom.launcher.ha

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Połączenie z HA, który ma własny certyfikat (przeglądarka pokazuje ostrzeżenie).
 * Zaufanie „przy pierwszym połączeniu”: na stronie w telefonie widać odcisk certyfikatu
 * i przycisk „Ufaj”. Potem akceptujemy wyłącznie ten jeden certyfikat (i normalne, zaufane certyfikaty).
 */
object HaTls {

    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    /** Ostatnio widziany certyfikat, którego nie zaufaliśmy – pokazujemy go na stronie w telefonie. */
    @Volatile var lastUntrusted: String? = null

    fun client(pin: String?, readTimeoutSeconds: Long = 0): OkHttpClient {
        val system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()

        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
                system.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val fingerprint = sha256(chain[0])
                if (pin != null && fingerprint.equals(pin, ignoreCase = true)) return
                try {
                    system.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    lastUntrusted = fingerprint
                    throw UntrustedCertificateException(fingerprint)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { host, session ->
                // Zaufany certyfikat zwykle nie zawiera adresu IP – wystarczy, że to dokładnie ten certyfikat.
                val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
                (pin != null && leaf != null && sha256(leaf).equals(pin, ignoreCase = true)) || defaultVerifier.verify(host, session)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }
}

class UntrustedCertificateException(val fingerprint: String) :
    CertificateException("Certyfikat Home Assistanta nie jest zaufany")
