package Akari.Add.RSA.manager

import Akari.Add.RSA.model.EcdsaKeyboxData
import Akari.Add.RSA.parser.KeyboxXmlParser
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Manages user's ECDSA certificates strictly in volatile memory.
 * NEVER writes ECDSA credentials to persistent storage.
 * Automatically unloads and purges all cached certificates upon completion.
 */
object TransientEcdsaManager {

    private val _ecdsaState = MutableStateFlow<EcdsaKeyboxData?>(null)
    val ecdsaState: StateFlow<EcdsaKeyboxData?> = _ecdsaState.asStateFlow()

    @Volatile
    private var transientData: EcdsaKeyboxData? = null

    /**
     * Loads ECDSA Keybox XML into volatile memory and parses its structure.
     */
    fun loadEcdsa(xml: String): Result<EcdsaKeyboxData> {
        return KeyboxXmlParser.parseEcdsaKeybox(xml).onSuccess { data ->
            transientData = data
            _ecdsaState.value = data
        }
    }

    /**
     * Gets the current in-memory ECDSA data.
     */
    fun getCurrentEcdsa(): EcdsaKeyboxData? = transientData

    /**
     * Completely purges all transient ECDSA data from memory and deletes temporary cache files.
     */
    fun purge(context: Context) {
        transientData = null
        _ecdsaState.value = null

        // Clean any temporary shared files in cache
        runCatching {
            val sharedDir = File(context.cacheDir, "shared")
            if (sharedDir.exists()) {
                sharedDir.deleteRecursively()
            }
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".xml") || file.name.startsWith("keybox_")) {
                    file.delete()
                }
            }
        }

        // Trigger memory cleanup
        System.gc()
    }
}
