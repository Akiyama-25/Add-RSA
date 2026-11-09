package Akari.Add.RSA

import Akari.Add.RSA.manager.LanguageManager
import Akari.Add.RSA.manager.RsaKeyManager
import Akari.Add.RSA.manager.TransientEcdsaManager
import Akari.Add.RSA.manager.UpdateCheckResult
import Akari.Add.RSA.manager.UpdateManager
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen {
    Main,
    LanguageSettings,
    UpdateCheck,
    SecurityDetails
}

class MainActivity : AppCompatActivity() {

    private var pendingXmlUri by mutableStateOf<Uri?>(null)
    private var pendingExportXml by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val savedLang = LanguageManager.getSavedLanguage(this)
        LanguageManager.applyLanguage(this, savedLang)

        RsaKeyManager.init(this)
        handleIntent(intent)

        setContent {
            AddRSATheme {
                MainAppHost(
                    onImportUri = { uri -> loadXmlFromUri(uri) }
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
        TransientEcdsaManager.purge(this)
    }
}

@Composable
fun MainAppHost(
    onImportUri: (Uri) -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.Main) }
    var selectedTab by remember { mutableIntStateOf(0) }

    PredictiveBackContainer(
        isOverlayOpen = currentScreen != AppScreen.Main,
        onCloseOverlay = { currentScreen = AppScreen.Main },
        backgroundContent = {
            MainTabsScreen(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onImportUri = onImportUri,
                onNavigateScreen = { currentScreen = it }
            )
        },
        overlayContent = { requestClose ->
            when (currentScreen) {
                AppScreen.LanguageSettings -> LanguageSettingsScreen(onBack = requestClose)
                AppScreen.UpdateCheck -> UpdateCheckScreen(onBack = requestClose)
                AppScreen.SecurityDetails -> SecurityDetailsScreen(onBack = requestClose)
                AppScreen.Main -> Unit
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onImportUri: (Uri) -> Unit,
    onNavigateScreen: (AppScreen) -> Unit
) {
    val context = LocalContext.current
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
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (ecdsaData != null) {
                        IconButton(onClick = {
                            TransientEcdsaManager.purge(context)
                            Toast.makeText(context, R.string.toast_purged, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.btn_purge_now)
                            )
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
                    onClick = { onTabSelected(0) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_blend)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_rsa)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onTabSelected(2) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                label = "TabAnimatedTransition"
            ) { tab ->
                when (tab) {
                    0 -> QuickBlendTab(
                        ecdsaData = ecdsaData,
                        activeKey = activeKey,
                        onSelectEcdsa = { pickEcdsaLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                        onManageRsa = { onTabSelected(1) },
                        onBlend = {
                            val currentEcdsa = ecdsaData
                            if (currentEcdsa == null) {
                                Toast.makeText(context, R.string.toast_blend_first, Toast.LENGTH_SHORT).show()
                                return@QuickBlendTab
                            }
                            val rsaXmlResult = RsaKeyManager.getActiveRsaXml(context)
                            if (rsaXmlResult.isFailure) {
                                Toast.makeText(context, "Error reading RSA: ${rsaXmlResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
                    2 -> SettingsTab(
                        onOpenLanguage = { onNavigateScreen(AppScreen.LanguageSettings) },
                        onOpenUpdate = { onNavigateScreen(AppScreen.UpdateCheck) },
                        onOpenSecurity = { onNavigateScreen(AppScreen.SecurityDetails) }
                    )
                }
            }
        }
    }

    // Adaptive Blend Result Dialog (Solves horizontal overflow completely)
    if (blendResultDialog != null) {
        val result = blendResultDialog!!
        Dialog(onDismissRequest = { blendResultDialog = null }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.dialog_blend_success_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.dialog_blend_success_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DetailRow(stringResource(R.string.label_device_id), result.deviceId)
                            DetailRow(stringResource(R.string.label_cert_count), "${result.certCount} level(s)")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Adaptive button layout: stacks vertically when width is narrow, or clean stack
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isNarrow = maxWidth < 340.dp
                        if (isNarrow) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        xmlToSave = result.mixedXml
                                        blendResultDialog = null
                                        saveFileLauncher.launch("keybox.xml")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_save_file))
                                }
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("keybox.xml", result.mixedXml))
                                        Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
                                        TransientEcdsaManager.purge(context)
                                        blendResultDialog = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_copy_clipboard))
                                }
                                OutlinedButton(
                                    onClick = {
                                        shareKeyboxXml(context, result.mixedXml)
                                        TransientEcdsaManager.purge(context)
                                        blendResultDialog = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_share))
                                }
                                TextButton(
                                    onClick = { blendResultDialog = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.btn_close))
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        xmlToSave = result.mixedXml
                                        blendResultDialog = null
                                        saveFileLauncher.launch("keybox.xml")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_save_file))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("keybox.xml", result.mixedXml))
                                            Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
                                            TransientEcdsaManager.purge(context)
                                            blendResultDialog = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(stringResource(R.string.btn_copy_clipboard), maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            shareKeyboxXml(context, result.mixedXml)
                                            TransientEcdsaManager.purge(context)
                                            blendResultDialog = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(stringResource(R.string.btn_share), maxLines = 1)
                                    }
                                }
                                TextButton(
                                    onClick = { blendResultDialog = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.btn_close))
                                }
                            }
                        }
                    }
                }
            }
        }
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
            icon = Icons.Default.Key
        )

        Spacer(modifier = Modifier.height(8.dp))

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
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        val isNarrow = maxWidth < 320.dp
                        if (isNarrow) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (item.isDefault) stringResource(R.string.default_aosp_name) else item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                ExpressiveBadge(
                                    text = stringResource(R.string.badge_aes_encrypted),
                                    color = Color(0xFF047857),
                                    containerColor = Color(0xFFD1FAE5),
                                    icon = Icons.Default.Lock
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (item.isDefault) stringResource(R.string.default_aosp_name) else item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
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
    }
}

@Composable
fun SettingsTab(
    onOpenLanguage: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenSecurity: () -> Unit
) {
    val context = LocalContext.current
    val currentLangCode = remember { LanguageManager.getSavedLanguage(context) }
    val currentLangTitle = remember(currentLangCode) {
        LanguageManager.supportedLanguages.firstOrNull { it.code == currentLangCode }?.nativeName ?: "Default"
    }
    val currentChannel = remember { UpdateManager.getSelectedChannel(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = stringResource(R.string.section_preferences),
            icon = Icons.Default.Tune
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingClickableItem(
                    title = stringResource(R.string.setting_language),
                    subtitle = currentLangTitle,
                    icon = Icons.Default.Translate,
                    onClick = onOpenLanguage
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickableItem(
                    title = stringResource(R.string.setting_update),
                    subtitle = "${BuildConfig.VERSION_NAME} • ${if (currentChannel == UpdateManager.CHANNEL_ACTION) "Action" else "Release"}",
                    icon = Icons.Default.SystemUpdate,
                    onClick = onOpenUpdate
                )
            }
        }

        SectionHeader(
            title = stringResource(R.string.section_security),
            icon = Icons.Default.Shield
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingClickableItem(
                title = stringResource(R.string.section_security),
                subtitle = stringResource(R.string.privacy_open_source_desc),
                icon = Icons.Default.Security,
                onClick = onOpenSecurity
            )
        }

        SectionHeader(
            title = stringResource(R.string.section_about),
            icon = Icons.Default.Info
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(stringResource(R.string.label_current_version), BuildConfig.VERSION_NAME)
                DetailRow(stringResource(R.string.label_built_release_tag), BuildConfig.BUILT_LATEST_RELEASE_TAG)
                if (BuildConfig.GIT_COMMIT_SHA.isNotBlank()) {
                    DetailRow("Commit", BuildConfig.GIT_COMMIT_SHA)
                }
            }
        }
    }
}

@Composable
fun SettingClickableItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

// Secondary Page: Language Settings
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedLang by remember { mutableStateOf(LanguageManager.getSavedLanguage(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(LanguageManager.supportedLanguages) { item ->
                val isSelected = selectedLang == item.code
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedLang = item.code
                            LanguageManager.applyLanguage(context, item.code)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = item.nativeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(item.titleRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedLang = item.code
                                LanguageManager.applyLanguage(context, item.code)
                            }
                        )
                    }
                }
            }
        }
    }
}

// Secondary Page: Update Check
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var selectedChannel by remember { mutableStateOf(UpdateManager.getSelectedChannel(context)) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult>(UpdateCheckResult.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_update), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Version Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow(stringResource(R.string.label_current_version), BuildConfig.VERSION_NAME)
                    DetailRow(stringResource(R.string.label_built_release_tag), BuildConfig.BUILT_LATEST_RELEASE_TAG)
                    if (BuildConfig.GIT_COMMIT_SHA.isNotBlank()) {
                        DetailRow("Git Commit", BuildConfig.GIT_COMMIT_SHA)
                    }
                }
            }

            // Channel Selector
            Text(
                text = stringResource(R.string.update_channel_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = selectedChannel == UpdateManager.CHANNEL_RELEASE,
                    onClick = {
                        selectedChannel = UpdateManager.CHANNEL_RELEASE
                        UpdateManager.setSelectedChannel(context, UpdateManager.CHANNEL_RELEASE)
                        updateResult = UpdateCheckResult.Idle
                    },
                    label = { Text(stringResource(R.string.channel_release)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedChannel == UpdateManager.CHANNEL_ACTION,
                    onClick = {
                        selectedChannel = UpdateManager.CHANNEL_ACTION
                        UpdateManager.setSelectedChannel(context, UpdateManager.CHANNEL_ACTION)
                        updateResult = UpdateCheckResult.Idle
                    },
                    label = { Text(stringResource(R.string.channel_action)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        updateResult = UpdateCheckResult.Checking
                        updateResult = UpdateManager.checkUpdate(selectedChannel)
                    }
                },
                enabled = updateResult !is UpdateCheckResult.Checking,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (updateResult is UpdateCheckResult.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.update_checking))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_check_update))
                }
            }

            // Update Check Result Status
            when (val res = updateResult) {
                is UpdateCheckResult.UpToDate -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.update_latest),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${res.currentVersion} (${if (res.channel == UpdateManager.CHANNEL_ACTION) "Action" else "Release"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is UpdateCheckResult.Available -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${stringResource(R.string.update_available)}: ${res.info.newVersion}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (res.info.title.isNotBlank() && res.info.title != res.info.newVersion) {
                                Text(text = res.info.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            if (res.info.description.isNotBlank()) {
                                Text(
                                    text = res.info.description.take(200),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Adaptive Action Buttons
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val isNarrow = maxWidth < 300.dp
                                if (isNarrow) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { uriHandler.openUri(res.info.downloadUrl) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.update_btn_download))
                                        }
                                        OutlinedButton(
                                            onClick = { uriHandler.openUri(res.info.pageUrl) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(stringResource(R.string.update_btn_view))
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { uriHandler.openUri(res.info.downloadUrl) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.update_btn_download))
                                        }
                                        OutlinedButton(
                                            onClick = { uriHandler.openUri(res.info.pageUrl) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(stringResource(R.string.update_btn_view))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is UpdateCheckResult.Error -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = res.errorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                UpdateCheckResult.Idle, UpdateCheckResult.Checking -> Unit
            }
        }
    }
}

// Secondary Page: Security Details
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityDetailsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_security), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
