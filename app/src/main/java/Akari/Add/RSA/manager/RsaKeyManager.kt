package Akari.Add.RSA.manager

import Akari.Add.RSA.crypto.AesCipherHelper
import Akari.Add.RSA.crypto.DefaultAospKey
import Akari.Add.RSA.model.RsaKeyItem
import Akari.Add.RSA.parser.KeyboxXmlParser
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object RsaKeyManager {

    private const val PREFS_NAME = "rsa_keys_meta"
    private const val KEY_ACTIVE_ID = "active_rsa_id"
    private const val KEY_ITEMS_JSON = "rsa_items_list"
    const val DEFAULT_AOSP_ID = "default_aosp"

    private val _rsaKeysState = MutableStateFlow<List<RsaKeyItem>>(emptyList())
    val rsaKeysState: StateFlow<List<RsaKeyItem>> = _rsaKeysState.asStateFlow()

    private val _activeKeyIdState = MutableStateFlow(DEFAULT_AOSP_ID)
    val activeKeyIdState: StateFlow<String> = _activeKeyIdState.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeId = prefs.getString(KEY_ACTIVE_ID, DEFAULT_AOSP_ID) ?: DEFAULT_AOSP_ID
        _activeKeyIdState.value = activeId
        loadKeysList(context)
    }

    private fun loadKeysList(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ITEMS_JSON, "[]") ?: "[]"
        val customItems = mutableListOf<RsaKeyItem>()

        runCatching {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                // Ensure encrypted file actually exists
                val file = File(context.filesDir, "encrypted_rsa/$id.bin")
                if (file.exists()) {
                    customItems.add(RsaKeyItem(id = id, name = name, isDefault = false, createdAt = createdAt))
                }
            }
        }

        val allItems = mutableListOf<RsaKeyItem>()
        // Default AOSP key is always first
        allItems.add(RsaKeyItem(id = DEFAULT_AOSP_ID, name = "AOSP Software Attestation Root", isDefault = true))
        allItems.addAll(customItems)

        _rsaKeysState.value = allItems
    }

    fun setActiveKey(context: Context, id: String) {
        _activeKeyIdState.value = id
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_ID, id)
            .apply()
    }

    /**
     * Decrypts and retrieves the currently active RSA XML block in volatile memory.
     */
    fun getActiveRsaXml(context: Context): Result<String> = runCatching {
        val activeId = _activeKeyIdState.value
        if (activeId == DEFAULT_AOSP_ID) {
            DefaultAospKey.getRsaXml()
        } else {
            val file = File(context.filesDir, "encrypted_rsa/$activeId.bin")
            require(file.exists()) { "Encrypted RSA key file not found for ID: $activeId" }
            val encryptedBytes = file.readBytes()
            val decryptedBytes = AesCipherHelper.decryptStorageData(context, encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        }
    }

    /**
     * Imports a custom RSA key from XML, encrypts it with AES-256, and stores it in app-private storage.
     */
    fun importCustomRsaKey(context: Context, name: String, xmlContent: String): Result<RsaKeyItem> = runCatching {
        val rsaXml = KeyboxXmlParser.extractRsaBlock(xmlContent).getOrThrow()
        val id = UUID.randomUUID().toString()

        // Encrypt with AES-256 before writing to storage
        val encryptedData = AesCipherHelper.encryptStorageData(context, rsaXml.toByteArray(Charsets.UTF_8))
        val rsaDir = File(context.filesDir, "encrypted_rsa").apply { mkdirs() }
        val targetFile = File(rsaDir, "$id.bin")
        targetFile.writeBytes(encryptedData)

        val newItem = RsaKeyItem(id = id, name = name.ifBlank { "Custom RSA ($id)" }, isDefault = false)

        // Save metadata
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ITEMS_JSON, "[]") ?: "[]"
        val array = JSONArray(jsonStr)
        val newObj = JSONObject().apply {
            put("id", newItem.id)
            put("name", newItem.name)
            put("createdAt", newItem.createdAt)
        }
        array.put(newObj)
        prefs.edit().putString(KEY_ITEMS_JSON, array.toString()).apply()

        loadKeysList(context)
        // Automatically switch to the newly imported key
        setActiveKey(context, id)
        newItem
    }

    /**
     * Deletes a custom RSA key and its encrypted storage file.
     */
    fun deleteCustomRsaKey(context: Context, id: String): Boolean {
        if (id == DEFAULT_AOSP_ID) return false

        val file = File(context.filesDir, "encrypted_rsa/$id.bin")
        if (file.exists()) {
            file.delete()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ITEMS_JSON, "[]") ?: "[]"
        val array = JSONArray(jsonStr)
        val newArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") != id) {
                newArray.put(obj)
            }
        }
        prefs.edit().putString(KEY_ITEMS_JSON, newArray.toString()).apply()

        if (_activeKeyIdState.value == id) {
            setActiveKey(context, DEFAULT_AOSP_ID)
        }

        loadKeysList(context)
        return true
    }
}
