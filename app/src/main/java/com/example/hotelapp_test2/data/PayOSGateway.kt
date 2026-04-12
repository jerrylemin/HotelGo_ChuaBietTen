package com.example.hotelapp_test2.data

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.hotelapp_test2.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PayOSGateway {
    data class CreateLinkResult(
        val orderCode: Long,
        val checkoutUrl: String,
        val paymentLinkId: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    fun createPaymentLinkDemo(
        bookingId: String,
        amountVnd: Long,
        itemName: String,
        onSuccess: (CreateLinkResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Thread {
            try {
                val result = createPaymentLinkInternal(bookingId, amountVnd, itemName)
                mainHandler.post { onSuccess(result) }
            } catch (error: Exception) {
                mainHandler.post { onError(error) }
            }
        }.start()
    }

    private fun createPaymentLinkInternal(
        bookingId: String,
        amountVnd: Long,
        itemName: String
    ): CreateLinkResult {
        val clientId = BuildConfig.PAYOS_CLIENT_ID
        val apiKey = BuildConfig.PAYOS_API_KEY
        val checksumKey = BuildConfig.PAYOS_CHECKSUM_KEY
        val baseUrl = BuildConfig.PAYOS_BASE_URL.trimEnd('/')
        if (
            clientId.isBlank() ||
            apiKey.isBlank() ||
            checksumKey.isBlank() ||
            baseUrl.isBlank()
        ) {
            throw IllegalStateException("Chua cau hinh PAYOS_CLIENT_ID / PAYOS_API_KEY / PAYOS_CHECKSUM_KEY")
        }

        val safeAmount = amountVnd.coerceAtLeast(1000L)
        val orderCode = System.currentTimeMillis()
        val description = buildDescription(bookingId)

        val returnUrl = appendQueryParams(
            BuildConfig.PAYOS_RETURN_URL,
            mapOf(
                "bookingId" to bookingId,
                "amount" to safeAmount.toString(),
                "orderCode" to orderCode.toString()
            )
        )
        val cancelUrl = appendQueryParams(
            BuildConfig.PAYOS_CANCEL_URL,
            mapOf(
                "bookingId" to bookingId,
                "amount" to safeAmount.toString(),
                "orderCode" to orderCode.toString()
            )
        )

        val signatureData = "amount=$safeAmount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl"
        val signature = hmacSha256Hex(checksumKey, signatureData)

        val payload = JSONObject()
            .put("orderCode", orderCode)
            .put("amount", safeAmount)
            .put("description", description)
            .put("returnUrl", returnUrl)
            .put("cancelUrl", cancelUrl)
            .put("items", JSONArray().put(
                JSONObject()
                    .put("name", itemName)
                    .put("quantity", 1)
                    .put("price", safeAmount)
            ))
            .put("signature", signature)

        val connection = (URL("$baseUrl/v2/payment-requests").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-client-id", clientId)
            setRequestProperty("x-api-key", apiKey)
        }
        connection.outputStream.use { out ->
            out.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
        }

        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.use { input -> BufferedReader(InputStreamReader(input)).readText() }
            .orEmpty()

        if (status !in 200..299) {
            throw IllegalStateException("Tao link payOS that bai: HTTP $status $responseBody")
        }

        val root = JSONObject(responseBody)
        val code = root.optString("code")
        if (code != "00") {
            throw IllegalStateException(root.optString("desc", "payOS tra ve loi"))
        }
        val data = root.optJSONObject("data")
            ?: throw IllegalStateException("Khong nhan duoc data tu payOS")
        val checkoutUrl = data.optString("checkoutUrl")
        if (checkoutUrl.isBlank()) {
            throw IllegalStateException("payOS khong tra ve checkoutUrl")
        }
        return CreateLinkResult(
            orderCode = orderCode,
            checkoutUrl = checkoutUrl,
            paymentLinkId = data.optString("paymentLinkId", "")
        )
    }

    private fun buildDescription(bookingId: String): String {
        val raw = "BOOK ${bookingId.takeLast(8)}"
            .replace(Regex("[^A-Za-z0-9 ]"), "")
            .trim()
        return raw.ifBlank { "BOOKING" }.take(25)
    }

    private fun appendQueryParams(base: String, params: Map<String, String>): String {
        val builder = Uri.parse(base).buildUpon()
        params.forEach { (key, value) ->
            builder.appendQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun hmacSha256Hex(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { b -> String.format(Locale.US, "%02x", b) }
    }
}
