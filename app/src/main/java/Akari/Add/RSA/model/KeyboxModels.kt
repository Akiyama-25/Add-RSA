package Akari.Add.RSA.model

/**
 * Transient in-memory representation of imported ECDSA Keybox.
 * NEVER written to persistent storage.
 */
data class EcdsaKeyboxData(
    val deviceId: String,
    val ecdsaKeyXml: String,
    val certCount: Int,
    val hasExistingRsa: Boolean,
    val rawXml: String
)

/**
 * Metadata for an available RSA key.
 * The actual RSA key material is ALWAYS stored encrypted with AES-256.
 */
data class RsaKeyItem(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Result of blending ECDSA + RSA.
 */
data class BlendResult(
    val mixedXml: String,
    val deviceId: String,
    val certCount: Int
)
