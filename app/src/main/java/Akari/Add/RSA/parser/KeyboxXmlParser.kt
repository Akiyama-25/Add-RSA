package Akari.Add.RSA.parser

import Akari.Add.RSA.model.BlendResult
import Akari.Add.RSA.model.EcdsaKeyboxData
import java.util.regex.Pattern

object KeyboxXmlParser {

    private val DEVICE_ID_REGEX = Pattern.compile("""<Keybox[^>]*DeviceID=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
    private val ECDSA_BLOCK_REGEX = Pattern.compile("""(<Key[^>]*algorithm=["']ecdsa["'][^>]*>.*?</Key>)""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val RSA_BLOCK_REGEX = Pattern.compile("""(<Key[^>]*algorithm=["']rsa["'][^>]*>.*?</Key>)""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val CERT_COUNT_REGEX = Pattern.compile("""<Certificate[^>]*format=["']pem["'][^>]*>""", Pattern.CASE_INSENSITIVE)

    /**
     * Parses an input Keybox XML and extracts ECDSA certificate metadata.
     * All parsing is done in volatile memory.
     */
    fun parseEcdsaKeybox(xml: String): Result<EcdsaKeyboxData> = runCatching {
        val trimmed = xml.trim()
        require(trimmed.contains("<Keybox", ignoreCase = true)) {
            "Not a valid Keybox XML: Missing <Keybox> root element"
        }

        val deviceIdMatcher = DEVICE_ID_REGEX.matcher(trimmed)
        val deviceId = if (deviceIdMatcher.find()) deviceIdMatcher.group(1) ?: "Unknown" else "sw"

        val ecdsaMatcher = ECDSA_BLOCK_REGEX.matcher(trimmed)
        require(ecdsaMatcher.find()) {
            "No ECDSA key attestation block found in this XML"
        }
        val ecdsaBlock = ecdsaMatcher.group(1) ?: ""

        val certMatcher = CERT_COUNT_REGEX.matcher(ecdsaBlock)
        var certCount = 0
        while (certMatcher.find()) {
            certCount++
        }

        val hasRsa = RSA_BLOCK_REGEX.matcher(trimmed).find()

        EcdsaKeyboxData(
            deviceId = deviceId,
            ecdsaKeyXml = ecdsaBlock,
            certCount = certCount,
            hasExistingRsa = hasRsa,
            rawXml = trimmed
        )
    }

    /**
     * Extracts an RSA key block from an XML string for custom importing.
     */
    fun extractRsaBlock(xml: String): Result<String> = runCatching {
        val trimmed = xml.trim()
        val matcher = RSA_BLOCK_REGEX.matcher(trimmed)
        require(matcher.find()) {
            "No <Key algorithm=\"rsa\"> found in the provided XML"
        }
        val block = matcher.group(1)?.trim() ?: ""
        require(block.contains("-----BEGIN RSA PRIVATE KEY-----") || block.contains("-----BEGIN PRIVATE KEY-----")) {
            "Invalid RSA block: Missing RSA private key"
        }
        require(block.contains("-----BEGIN CERTIFICATE-----")) {
            "Invalid RSA block: Missing RSA certificate chain"
        }
        block
    }

    /**
     * Blends the ECDSA keybox with the provided RSA block.
     * Replaces existing RSA if present, or appends before </Keybox>.
     */
    fun blendKeybox(sourceEcdsaXml: String, rsaBlockXml: String): Result<BlendResult> = runCatching {
        val trimmed = sourceEcdsaXml.trim()
        val deviceIdMatcher = DEVICE_ID_REGEX.matcher(trimmed)
        val deviceId = if (deviceIdMatcher.find()) deviceIdMatcher.group(1) ?: "sw" else "sw"

        val ecdsaMatcher = ECDSA_BLOCK_REGEX.matcher(trimmed)
        require(ecdsaMatcher.find()) { "Source XML has no ECDSA keyblock" }
        val ecdsaBlock = ecdsaMatcher.group(1) ?: ""

        val certMatcher = CERT_COUNT_REGEX.matcher(ecdsaBlock)
        var certCount = 0
        while (certMatcher.find()) { certCount++ }

        val formattedRsa = "\n        " + rsaBlockXml.trim()

        val mixedXml = if (RSA_BLOCK_REGEX.matcher(trimmed).find()) {
            // Replace existing RSA block
            RSA_BLOCK_REGEX.matcher(trimmed).replaceFirst(formattedRsa)
        } else {
            // Insert right before </Keybox>
            val endTagIndex = trimmed.lastIndexOf("</Keybox>")
            require(endTagIndex != -1) { "Malformed Keybox XML: Missing </Keybox>" }
            trimmed.substring(0, endTagIndex) + formattedRsa + "\n    " + trimmed.substring(endTagIndex)
        }

        BlendResult(
            mixedXml = mixedXml,
            deviceId = deviceId,
            certCount = certCount
        )
    }
}
