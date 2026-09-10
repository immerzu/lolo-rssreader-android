package de.lolo.rssreader.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import de.lolo.rssreader.BuildConfig
import de.lolo.rssreader.DebugLocaleManager
import de.lolo.rssreader.DebugLocaleOption
import de.lolo.rssreader.R
import de.lolo.rssreader.data.repository.FeedRepository
import de.lolo.rssreader.data.settings.AppPreferences
import de.lolo.rssreader.data.settings.EntrySortOrder
import de.lolo.rssreader.data.settings.SettingsRepository
import de.lolo.rssreader.data.settings.ThemeMode
import kotlinx.coroutines.launch

private val SettingsCardContentPadding = 12.dp
private val SettingsCardItemSpacing = 4.dp
private val SettingsTextSpacing = 0.dp
private val SettingsSelectableRowVerticalPadding = 0.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsRouteScreen(
    settings: AppPreferences,
    settingsRepository: SettingsRepository,
    onOpenOverview: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenOverview) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        SettingsScreen(
            settings = settings,
            settingsRepository = settingsRepository,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )
    }
}

@Composable
fun SettingsScreen(
    settings: AppPreferences,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = findHostActivity(context)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val refreshOptions = listOf(
        0 to stringResource(R.string.settings_interval_manual),
        15 to stringResource(R.string.settings_interval_15min),
        60 to stringResource(R.string.settings_interval_hourly),
        180 to stringResource(R.string.settings_interval_3hours),
        360 to stringResource(R.string.settings_interval_6hours),
        720 to stringResource(R.string.settings_interval_12hours),
        1440 to stringResource(R.string.settings_interval_daily)
    )
    val themeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
    )
    val entrySortOptions = listOf(
        EntrySortOrder.NEWEST_FIRST to stringResource(R.string.settings_sort_newest),
        EntrySortOrder.OLDEST_FIRST to stringResource(R.string.settings_sort_oldest)
    )
    val articleTextSizeOptions = listOf(
        -2 to "-2",
        -1 to "-1",
        0 to "0",
        1 to "+1",
        2 to "+2"
    )
    val appLanguageOptions = listOf(
        DebugLocaleOption.SYSTEM to stringResource(R.string.settings_language_system),
        DebugLocaleOption.GERMAN to stringResource(R.string.settings_language_german),
        DebugLocaleOption.ENGLISH to stringResource(R.string.settings_language_english)
    )
    var selectedAppLanguage by remember {
        mutableStateOf(DebugLocaleManager.getSelectedOption(context))
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsCard(title = stringResource(R.string.settings_section_notifications)) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_notifications_enable_title),
                subtitle = stringResource(R.string.settings_notifications_enable_subtitle),
                checked = settings.notificationsEnabled,
                onCheckedChange = {
                    scope.launch { settingsRepository.setNotificationsEnabled(it) }
                }
            )
        }

        SettingsCard(title = stringResource(R.string.settings_section_updates)) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_refresh_on_start_title),
                subtitle = stringResource(R.string.settings_refresh_on_start_subtitle),
                checked = settings.refreshOnStart,
                onCheckedChange = {
                    scope.launch { settingsRepository.setRefreshOnStart(it) }
                }
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_refresh_wifi_only_title),
                subtitle = stringResource(R.string.settings_refresh_wifi_only_subtitle),
                checked = settings.refreshOnlyOnWifi,
                onCheckedChange = {
                    scope.launch { settingsRepository.setRefreshOnlyOnWifi(it) }
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_background_interval_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.settings_background_interval_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                refreshOptions.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { settingsRepository.setRefreshIntervalMinutes(minutes) }
                            }
                            .padding(vertical = SettingsSelectableRowVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.refreshIntervalMinutes == minutes,
                            onClick = {
                                scope.launch { settingsRepository.setRefreshIntervalMinutes(minutes) }
                            }
                        )
                        Text(label)
                    }
                }
            }
        }

        SettingsCard(title = stringResource(R.string.settings_section_appearance)) {
            if (BuildConfig.DEBUG) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.settings_section_language_debug),
                        style = MaterialTheme.typography.titleSmall
                    )
                    appLanguageOptions.forEach { (option, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAppLanguage = option
                                    DebugLocaleManager.setSelectedOption(context, option)
                                    activity?.recreate()
                                }
                                .padding(vertical = SettingsSelectableRowVerticalPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAppLanguage == option,
                                onClick = {
                                    selectedAppLanguage = option
                                    DebugLocaleManager.setSelectedOption(context, option)
                                    activity?.recreate()
                                }
                            )
                            Text(label)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_color_scheme_title),
                    style = MaterialTheme.typography.titleSmall
                )
                themeOptions.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { settingsRepository.setThemeMode(mode) }
                            }
                            .padding(vertical = SettingsSelectableRowVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = {
                                scope.launch { settingsRepository.setThemeMode(mode) }
                            }
                        )
                        Text(label)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_entry_sort_title),
                    style = MaterialTheme.typography.titleSmall
                )
                entrySortOptions.forEach { (order, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { settingsRepository.setEntrySortOrder(order) }
                            }
                            .padding(vertical = SettingsSelectableRowVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.entrySortOrder == order,
                            onClick = {
                                scope.launch { settingsRepository.setEntrySortOrder(order) }
                            }
                        )
                        Text(label)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_article_text_size_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    articleTextSizeOptions.forEach { (offset, label) ->
                        SettingsChoiceChip(
                            label = label,
                            selected = settings.articleBodyTextSizeOffset == offset,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch { settingsRepository.setArticleBodyTextSizeOffset(offset) }
                            }
                        )
                    }
                }
            }
            SettingsToggleRow(
                title = stringResource(R.string.settings_show_images_title),
                subtitle = stringResource(R.string.settings_show_images_subtitle),
                checked = settings.showImages,
                onCheckedChange = {
                    scope.launch { settingsRepository.setShowImages(it) }
                }
            )
        }

    }
}

@Composable
private fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SettingsCardContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardItemSpacing),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                content()
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SettingsTextSpacing)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


