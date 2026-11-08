package Akari.Add.RSA

import Akari.Add.RSA.manager.RsaKeyManager
import Akari.Add.RSA.manager.TransientEcdsaManager
import Akari.Add.RSA.model.BlendResult
import Akari.Add.RSA.parser.KeyboxXmlParser
import Akari.Add.RSA.ui.components.*
import Akari.Add.RSA.ui.theme.AddRSATheme
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

class MainActivity : ComponentActivity() {

    private var pendingXmlUri by mutableStateOf<Uri?>(null)
    private var pendingExportXml by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        RsaKeyManager.init(this)
        handleIntent(intent)

        setContent {
            AddRSATheme {
                MainAppScreen(
                    onImportUri = { uri -> loadXmlFromUri(uri) },
                    onSaveMixedXml = { xml ->
                        pendingExportXml = xml
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri: Uri? = when (action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    ?: intent.clipData?.getItemAt(0)?.uri
                    ?: intent.data
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) {
            loadXmlFromUri(uri)
        }
    }

    private fun loadXmlFromUri(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val xmlContent = stream.bufferedReader(Charsets.UTF_8).readText()
                TransientEcdsaManager.loadEcdsa(xmlContent).onSuccess {
                    Toast.makeText(this, R.string.status_ready_to_blend, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(this, "${getString(R.string.error_invalid_xml)}: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.onFailure { e ->
            Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure all transient ECDSA certificates in memory/disk cache are purged when activity is destroyed
        TransientEcdsaManager.purge(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    onImportUri: (Uri) -> Unit,
    onSaveMixedXml: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val ecdsaData by TransientEcdsaManager.ecdsaState.collectAsStateWithLifecycle()
    val rsaKeys by RsaKeyManager.rsaKeysState.collectAsStateWithLifecycle()
    val activeKeyId by RsaKeyManager.activeKeyIdState.collectAsStateWithLifecycle()

    val activeKey = remember(rsaKeys, activeKeyId) {
        rsaKeys.firstOrNull { it.id == activeKeyId } ?: rsaKeys.firstOrNull()
        ?: Akari.Add.RSA.model.RsaKeyItem(RsaKeyManager.DEFAULT_AOSP_ID, "AOSP", true)
    }

    var blendResultDialog by remember { mutableStateOf<BlendResult?>(null) }
    var showImportRsaDialog by remember { mutableStateOf(false) }
    var pendingImportXmlContent by remember { mutableStateOf<String?>(null) }

    // SAF file picker for ECDSA XML
    val pickEcdsaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportUri(it) }
    }

    // SAF file picker for Custom RSA XML
    val pickRsaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    pendingImportXmlContent = content
                    showImportRsaDialog = true
                }
            }.onFailure { err ->
                Toast.makeText(context, "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SAF CreateDocument for saving mixed keybox
    var xmlToSave by remember { mutableStateOf<String?>(null) }
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml")
    ) { uri: Uri? ->
        if (uri != null && xmlToSave != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(xmlToSave!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, R.string.toast_save_success, Toast.LENGTH_SHORT).show()
                // Purge transient cache after successful export
                TransientEcdsaManager.purge(context)
                xmlToSave = null
            }.onFailure { e ->
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (ecdsaData != null) {
                        IconButton(onClick = {
                            TransientEcdsaManager.purge(context)
                            Toast.makeText(context, R.string.toast_purged, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.btn_purge_now))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_blend)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_rsa)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_about)) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> QuickBlendTab(
                    ecdsaData = ecdsaData,
                    activeKey = activeKey,
                    onSelectEcdsa = { pickEcdsaLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                    onManageRsa = { selectedTab = 1 },
                    onBlend = {
                        val currentEcdsa = ecdsaData
                        if (currentEcdsa == null) {
                            Toast.makeText(context, R.string.toast_blend_first, Toast.LENGTH_SHORT).show()
                            return@QuickBlendTab
                        }
                        val rsaXmlResult = RsaKeyManager.getActiveRsaXml(context)
                        if (rsaXmlResult.isFailure) {
                            Toast.makeText(context, "Error reading RSA key: ${rsaXmlResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            return@QuickBlendTab
                        }
                        val rsaXml = rsaXmlResult.getOrThrow()
                        KeyboxXmlParser.blendKeybox(currentEcdsa.rawXml, rsaXml)
                            .onSuccess { result ->
                                blendResultDialog = result
                            }
                            .onFailure { err ->
                                Toast.makeText(context, "Blend failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                )
                1 -> RsaKeysTab(
                    rsaKeys = rsaKeys,
                    activeKeyId = activeKeyId,
                    onSetActive = { id -> RsaKeyManager.setActiveKey(context, id) },
                    onDelete = { id -> RsaKeyManager.deleteCustomRsaKey(context, id) },
                    onImportClick = { pickRsaLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) }
                )
                2 -> SecurityTab()
            }
        }
    }

    // Blend Result Dialog
    if (blendResultDialog != null) {
        val result = blendResultDialog!!
        AlertDialog(
            onDismissRequest = { blendResultDialog = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.dialog_blend_success_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_blend_success_msg))
                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(stringResource(R.string.label_device_id), result.deviceId)
                    DetailRow(stringResource(R.string.label_cert_count), "${result.certCount} level(s)")
                }
            },
            confirmButton = {
                Button(onClick = {
                    xmlToSave = result.mixedXml
                    blendResultDialog = null
                    saveFileLauncher.launch("keybox.xml")
                }) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_save_file))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("keybox.xml", result.mixedXml))
                        Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
                        TransientEcdsaManager.purge(context)
                        blendResultDialog = null
                    }) {
                        Text(stringResource(R.string.btn_copy_clipboard))
                    }
                    Button(onClick = {
                        shareKeyboxXml(context, result.mixedXml)
                        TransientEcdsaManager.purge(context)
                        blendResultDialog = null
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_share))
                    }
                }
            }
        )
    }

    // Import Custom RSA Dialog
    if (showImportRsaDialog && pendingImportXmlContent != null) {
        var aliasName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showImportRsaDialog = false
                pendingImportXmlContent = null
            },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_import_rsa_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.desc_rsa_management), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = aliasName,
                        onValueChange = { aliasName = it },
                        label = { Text(stringResource(R.string.input_rsa_alias_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = aliasName.ifBlank { "Custom RSA Key" }
                        RsaKeyManager.importCustomRsaKey(context, name, pendingImportXmlContent!!)
                            .onSuccess {
                                Toast.makeText(context, R.string.toast_rsa_imported, Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { e ->
                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        showImportRsaDialog = false
                        pendingImportXmlContent = null
                    }
                ) {
                    Text(stringResource(R.string.btn_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportRsaDialog = false
                    pendingImportXmlContent = null
                }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun QuickBlendTab(
    ecdsaData: Akari.Add.RSA.model.EcdsaKeyboxData?,
    activeKey: Akari.Add.RSA.model.RsaKeyItem,
    onSelectEcdsa: () -> Unit,
    onManageRsa: () -> Unit,
    onBlend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = stringResource(R.string.section_quick_blend),
            subtitle = stringResource(R.string.desc_quick_blend),
            icon = Icons.Default.AutoFixHigh
        )

        EcdsaStatusCard(
            ecdsaData = ecdsaData,
            onSelectFile = onSelectEcdsa
        )

        RsaActiveCard(
            activeKey = activeKey,
            onManageKeys = onManageRsa
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onBlend,
            enabled = ecdsaData != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.btn_blend_export),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RsaKeysTab(
    rsaKeys: List<Akari.Add.RSA.model.RsaKeyItem>,
    activeKeyId: String,
    onSetActive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        SectionHeader(
            title = stringResource(R.string.section_rsa_management),
            subtitle = stringResource(R.string.desc_rsa_management),
            icon = Icons.Default.Key
        )

        Button(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_import_custom_rsa))
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(rsaKeys, key = { it.id }) { item ->
                val isActive = item.id == activeKeyId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (item.isDefault) stringResource(R.string.default_aosp_name) else item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            ExpressiveBadge(
                                text = stringResource(R.string.badge_aes_encrypted),
                                color = Color(0xFF047857),
                                containerColor = Color(0xFFD1FAE5),
                                icon = Icons.Default.Lock
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isActive) {
                                TextButton(onClick = { onSetActive(item.id) }) {
                                    Text(stringResource(R.string.btn_set_active))
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            if (!item.isDefault) {
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.btn_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = stringResource(R.string.section_security_privacy),
            icon = Icons.Default.Shield
        )

        SecurityFeatureCard(
            title = stringResource(R.string.security_ecdsa_title),
            desc = stringResource(R.string.security_ecdsa_desc),
            icon = Icons.Default.Memory,
            color = MaterialTheme.colorScheme.primary
        )

        SecurityFeatureCard(
            title = stringResource(R.string.security_rsa_title),
            desc = stringResource(R.string.security_rsa_desc),
            icon = Icons.Default.Lock,
            color = MaterialTheme.colorScheme.secondary
        )

        SecurityFeatureCard(
            title = stringResource(R.string.privacy_open_source),
            desc = stringResource(R.string.privacy_open_source_desc),
            icon = Icons.Default.Code,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
fun SecurityFeatureCard(
    title: String,
    desc: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun shareKeyboxXml(context: Context, xmlContent: String) {
    runCatching {
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(sharedDir, "keybox.xml")
        file.writeText(xmlContent, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share)))
    }.onFailure { err ->
        Toast.makeText(context, "Share failed: ${err.message}", Toast.LENGTH_SHORT).show()
    }
}
