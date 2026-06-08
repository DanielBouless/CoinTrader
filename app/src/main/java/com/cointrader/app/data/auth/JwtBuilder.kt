package com.cointrader.app.data.auth

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID

object JwtBuilder {

    fun buildJwt(apiKeyName: String, privateKeyPem: String, method: String, path: String): String {
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val headerJson = """{"alg":"ES256","kid":"$apiKeyName","nonce":"$nonce"}"""
        val now = System.currentTimeMillis() / 1000L
        val uri = "$method api.coinbase.com$path"
        val payloadJson = """{"iss":"cdp","nbf":$now,"exp":${now + 120},"sub":"$apiKeyName","uri":"$uri"}"""

        val header = base64url(headerJson.toByteArray())
        val payload = base64url(payloadJson.toByteArray())
        val signingInput = "$header.$payload"

        val privateKey = parsePrivateKey(privateKeyPem)
        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
            sign()
        }

        val rawSig = derToRaw(sig)
        val sigStr = base64url(rawSig)
        return "$signingInput.$sigStr"
    }

    private fun base64url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun parsePrivateKey(pem: String): java.security.PrivateKey {
        val cleaned = pem
            .replace("-----BEGIN EC PRIVATE KEY-----", "")
            .replace("-----END EC PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "\n")
            .replace("\n", "").replace("\r", "").replace(" ", "")

        val keyBytes = Base64.decode(cleaned, Base64.DEFAULT)

        val pkcs8Bytes = if (pem.contains("BEGIN EC PRIVATE KEY")) {
            // SEC1 → PKCS8 wrapping for P-256 (secp256r1)
            wrapSec1ToPkcs8(keyBytes)
        } else {
            keyBytes
        }

        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
    }

    // Wraps a SEC1 EC private key (prime256v1 / P-256) into PKCS#8 format.
    // The fixed prefix encodes: AlgorithmIdentifier { ecPublicKey, prime256v1 }
    private fun wrapSec1ToPkcs8(sec1: ByteArray): ByteArray {
        // PKCS8 AlgorithmIdentifier for EC / P-256:
        // 30 13 06 07 2A 86 48 CE 3D 02 01  (id-ecPublicKey OID)
        //       06 08 2A 86 48 CE 3D 03 01 07 (secp256r1 OID)
        val algId = byteArrayOf(
            0x30, 0x13,
            0x06, 0x07, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x02, 0x01,
            0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x03, 0x01, 0x07
        )
        // OCTET STRING wrapping the SEC1 key
        val octetLen = sec1.size + 2
        val totalLen = algId.size + octetLen
        return byteArrayOf(0x30) + derLen(totalLen) + algId +
               byteArrayOf(0x04) + derLen(sec1.size) + sec1
    }

    private fun derLen(len: Int): ByteArray = when {
        len < 0x80 -> byteArrayOf(len.toByte())
        len < 0x100 -> byteArrayOf(0x81.toByte(), len.toByte())
        else -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte())
    }

    // Converts a DER-encoded ECDSA signature to the raw 64-byte format required by ES256.
    private fun derToRaw(der: ByteArray): ByteArray {
        var i = 2 // skip SEQUENCE tag (0x30) and length
        i++ // skip INTEGER tag (0x02) for r
        val rLen = der[i++].toInt() and 0xFF
        val r = der.copyOfRange(i, i + rLen)
        i += rLen
        i++ // skip INTEGER tag (0x02) for s
        val sLen = der[i++].toInt() and 0xFF
        val s = der.copyOfRange(i, i + sLen)

        val raw = ByteArray(64)
        val rStart = if (r[0] == 0.toByte() && r.size == 33) 1 else 0
        val rCopy = r.copyOfRange(rStart, r.size)
        rCopy.copyInto(raw, 32 - rCopy.size)

        val sStart = if (s[0] == 0.toByte() && s.size == 33) 1 else 0
        val sCopy = s.copyOfRange(sStart, s.size)
        sCopy.copyInto(raw, 64 - sCopy.size)
        return raw
    }
}
