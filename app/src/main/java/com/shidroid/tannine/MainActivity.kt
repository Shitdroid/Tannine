package com.shidroid.tannine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shidroid.tannine.ui.theme.TannineTheme
import com.shidroid.tannine.ui.theme.TeaPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            var themeMode by remember { mutableIntStateOf(prefs.themeMode) }
            var dynamic by remember { mutableStateOf(prefs.dynamicColor) }
            var paletteIndex by remember { mutableIntStateOf(prefs.palette) }
            val dark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            TannineTheme(
                darkTheme = dark,
                palette = TeaPalette.entries[paletteIndex],
                dynamicColor = dynamic
            ) {
                MainScreen(
                    themeMode, { themeMode = it; prefs.themeMode = it },
                    dynamic, { dynamic = it; prefs.dynamicColor = it },
                    paletteIndex, { paletteIndex = it; prefs.palette = it }
                )
            }
        }
    }
}

private data class AppEntry(val pkg: String, val label: String, val icon: Bitmap?)

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.packageName to it }
        .distinctBy { it.first }
        .filter { it.first != context.packageName }
        .map { (pkg, ri) ->
            AppEntry(pkg, ri.loadLabel(pm).toString(), runCatching { ri.loadIcon(pm).toBitmap(96, 96) }.getOrNull())
        }
        .sortedBy { it.label.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    themeMode: Int, onThemeMode: (Int) -> Unit,
    dynamicColor: Boolean, onDynamicColor: (Boolean) -> Unit,
    paletteIndex: Int, onPalette: (Int) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val controller = remember { TimeoutController(context) }

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val canWrite = remember(refresh) { controller.canWrite() }
    val hasUsage = remember(refresh) { ForegroundAppDetector.hasAccess(context) }
    val selectedModeReady = canWrite && hasUsage
    val universalModeReady = canWrite

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var universalMode by remember { mutableStateOf(prefs.universalMode) }
    var selected by remember { mutableStateOf(prefs.triggerApps) }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val modeReady = if (universalMode) universalModeReady else selectedModeReady

    LaunchedEffect(refresh) {
        enabled = prefs.enabled
        universalMode = prefs.universalMode
        selected = prefs.triggerApps
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Keep the service in step with the switch and permissions.
    LaunchedEffect(enabled, universalMode, selectedModeReady, universalModeReady) {
        if (enabled && modeReady) {
            WatcherService.start(context)
        } else {
            WatcherService.stop(context)
        }
    }

    val apps by produceState(emptyList<AppEntry>()) { value = withContext(Dispatchers.IO) { loadApps(context) } }
    val searching = query.isNotBlank()
    val matching = apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    val selectedApps = if (searching) emptyList() else matching.filter { it.pkg in selected }
    val otherApps = if (searching) matching else matching.filter { it.pkg !in selected }

    fun toggle(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
        prefs.triggerApps = selected
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton({ showSettings = true }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.appearance))
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "status") {
                StatusCard(enabled, modeReady, universalMode, selected.size) {
                    enabled = it
                    prefs.enabled = it
                    TannineTileService.requestRefresh(context)
                }
            }
            item(key = "mode") {
                ModeSelector(universalMode) {
                    universalMode = it
                    prefs.universalMode = it
                    TannineTileService.requestRefresh(context)
                }
            }
            if (!canWrite || (!universalMode && !hasUsage)) item(key = "setup") {
                SetupCard(canWrite, hasUsage, Modifier.padding(top = 12.dp))
            }
            item(key = "search") {
                SearchField(
                    query, { query = it },
                    Modifier.padding(top = 16.dp, bottom = 8.dp),
                    enabled = !universalMode
                )
            }
            if (!universalMode && selectedApps.isNotEmpty()) {
                item(key = "h_sel") { SectionHeader(stringResource(R.string.selected_apps, selectedApps.size)) }
                itemsIndexed(selectedApps, key = { _, a -> "s_" + a.pkg }) { i, app ->
                    AppRow(app, true, groupShape(i, selectedApps.size)) { toggle(app.pkg) }
                }
                if (otherApps.isNotEmpty()) item(key = "h_all") { SectionHeader(stringResource(R.string.all_apps)) }
            }
            if (!universalMode) itemsIndexed(otherApps, key = { _, a -> a.pkg }) { i, app ->
                AppRow(app, app.pkg in selected, groupShape(i, otherApps.size)) { toggle(app.pkg) }
            }
            if (!universalMode && apps.isNotEmpty() && matching.isEmpty()) item(key = "empty") {
                Text(
                    stringResource(R.string.no_results), Modifier.fillMaxWidth().padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showSettings) AppearanceDialog(themeMode, onThemeMode, dynamicColor, onDynamicColor, paletteIndex, onPalette) { showSettings = false }
}

private val LargeCorner = 20.dp
private val SmallCorner = 4.dp

/** Connected-list shape: large corners on the group's outer edges, small ones between items. */
private fun groupShape(index: Int, size: Int) = RoundedCornerShape(
    topStart = if (index == 0) LargeCorner else SmallCorner,
    topEnd = if (index == 0) LargeCorner else SmallCorner,
    bottomStart = if (index == size - 1) LargeCorner else SmallCorner,
    bottomEnd = if (index == size - 1) LargeCorner else SmallCorner,
)

@Composable
private fun StatusCard(
    enabled: Boolean,
    ready: Boolean,
    universalMode: Boolean,
    count: Int,
    onChange: (Boolean) -> Unit
) {
    val active = enabled && ready
    val colors = MaterialTheme.colorScheme
    val container = if (active) colors.primary else colors.surfaceContainerHigh
    val content = if (active) colors.onPrimary else colors.onSurface
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Row(
            Modifier
                .toggleable(
                    active,
                    enabled = enabled || ready,
                    role = Role.Switch,
                    onValueChange = onChange
                )
                .heightIn(min = 88.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                Modifier.size(48.dp), shape = CircleShape,
                color = if (active) colors.onPrimary.copy(alpha = 0.2f) else colors.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (active) Icons.Filled.CheckCircle else Icons.Filled.Info, null,
                        tint = if (active) colors.onPrimary else colors.onSurfaceVariant
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.enable), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !ready -> stringResource(R.string.status_setup)
                        !enabled -> stringResource(R.string.status_off)
                        universalMode -> stringResource(R.string.mode_universal)
                        count == 0 -> stringResource(R.string.status_none)
                        else -> pluralStringResource(R.plurals.status_active, count, count)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(active, null, enabled = ready)
        }
    }
}

@Composable
private fun ModeSelector(universalMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.mode),
            Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(R.string.mode_selected, R.string.mode_universal)
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = universalMode == (index == 1),
                    onClick = { onModeChange(index == 1) },
                    shape = SegmentedButtonDefaults.itemShape(index, labels.size)
                ) {
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
private fun SetupCard(canWrite: Boolean, hasUsage: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            PermissionRow(stringResource(R.string.need_write_settings), canWrite) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
            PermissionRow(stringResource(R.string.need_usage_access), hasUsage) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
    }
}

@Composable
private fun PermissionRow(text: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning, null)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!granted) FilledTonalButton(onGrant) { Text(stringResource(R.string.grant)) }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextField(
        query, onQuery, modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = { Text(stringResource(R.string.search_apps)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton({ onQuery("") }) {
                Icon(Icons.Filled.Close, stringResource(R.string.clear))
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, shape: RoundedCornerShape, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = shape,
        color = if (checked) colors.secondaryContainer else colors.surfaceContainerLow
    ) {
        ListItem(
            modifier = Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle() }),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                app.icon?.let {
                    Image(it.asImageBitmap(), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                }
            },
            headlineContent = { Text(app.label, maxLines = 1) },
            supportingContent = { Text(app.pkg, maxLines = 1) },
            trailingContent = { Checkbox(checked, null) }
        )
    }
}

@Composable
private fun AppearanceDialog(
    themeMode: Int, onThemeMode: (Int) -> Unit,
    dynamicColor: Boolean, onDynamicColor: (Boolean) -> Unit,
    paletteIndex: Int, onPalette: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = themeMode == i,
                            onClick = { onThemeMode(i) },
                            shape = SegmentedButtonDefaults.itemShape(i, labels.size)
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TeaPalette.entries.forEachIndexed { i, p ->
                        Swatch(p, i == paletteIndex && !dynamicColor) { onPalette(i); onDynamicColor(false) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        Modifier
                            .toggleable(dynamicColor, role = Role.Switch, onValueChange = onDynamicColor)
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.dynamic_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(dynamicColor, null)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

@Composable
private fun Swatch(palette: TeaPalette, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(palette.seed)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = palette.label },
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = Color.White)
    }
}
