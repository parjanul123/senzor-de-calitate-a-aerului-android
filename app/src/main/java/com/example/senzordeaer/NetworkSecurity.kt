package com.example.senzordeaer

import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

/**
 * Configurare de rețea comună pentru toate clienții HTTP ai aplicației:
 * TLS 1.2+ forțat și certificate pinning (SPKI SHA-256) pentru domeniile backend.
 *
 * IMPORTANT despre pin-uri: valorile din [pinnedHosts] TREBUIE obținute dintr-o
 * rețea de încredere (fără proxy/antivirus care interceptează TLS, ex. AVG/Kaspersky
 * "Web Shield"), altfel se pinează certificatul falsului MITM local și aplicația
 * va refuza conexiuni reale în producție. Până când pin-urile reale sunt verificate,
 * [ENABLE_CERTIFICATE_PINNING] rămâne dezactivat pentru a nu bloca traficul legitim.
 *
 * Pași pentru activare:
 * 1. Pe o rețea curată, obține pin-ul SPKI SHA-256 pentru fiecare host (ex. cu
 *    `openssl s_client -connect host:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64`).
 * 2. Adaugă cel puțin 2 pin-uri per host (certificatul curent + un backup/intermediar).
 * 3. Setează [ENABLE_CERTIFICATE_PINNING] = true.
 */
object NetworkSecurity {
    const val ENABLE_CERTIFICATE_PINNING = false

    private val pinnedHosts: CertificatePinner = CertificatePinner.Builder()
        // TODO: înlocuiește cu pin-urile SPKI SHA-256 reale, verificate pe rețea curată.
        // .add("eakzxbfcwbgfxfujzote.supabase.co", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        // .add("ai-senzor-de-calitate-a-aerului-production.up.railway.app", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
        .build()

    private val modernTls = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        .build()

    fun hardenedClientBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectionSpecs(listOf(modernTls))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (ENABLE_CERTIFICATE_PINNING) {
            builder.certificatePinner(pinnedHosts)
        }
        return builder
    }
}
