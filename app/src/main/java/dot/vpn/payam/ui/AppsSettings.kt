// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dot.vpn.payam.R
import dot.vpn.payam.SplitMode
import dot.vpn.payam.Store
import dot.vpn.payam.ui.theme.DotTheme

private data class AppRow(val info: ApplicationInfo, val label: String)

@Composable
internal fun AppsSettingsPanel(store: Store, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val c = DotTheme.colors
    val apps = remember {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != ctx.packageName }
            .map { AppRow(it, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }
    var mode by remember { mutableStateOf(store.splitMode) }
    var picked by remember { mutableStateOf(store.splitApps) }
    var query by remember { mutableStateOf("") }

    fun save(nextMode: SplitMode = mode, nextPicked: Set<String> = picked) {
        mode = nextMode
        picked = nextPicked
        store.splitMode = nextMode
        store.splitApps = nextPicked
    }

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { it.label.lowercase().contains(q) || it.info.packageName.lowercase().contains(q) }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.split_intro_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.split_intro_body), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
        }

        DotCard {
            ModeOption(
                selected = mode == SplitMode.ALL,
                title = stringResource(R.string.split_all),
                hint = stringResource(R.string.split_all_hint),
                onClick = { save(SplitMode.ALL) },
            )
            DividerLine()
            ModeOption(
                selected = mode == SplitMode.ONLY,
                title = stringResource(R.string.split_only),
                hint = stringResource(R.string.split_only_hint),
                onClick = { save(SplitMode.ONLY) },
            )
            DividerLine()
            ModeOption(
                selected = mode == SplitMode.EXCEPT,
                title = stringResource(R.string.split_except),
                hint = stringResource(R.string.split_except_hint),
                onClick = { save(SplitMode.EXCEPT) },
            )
        }

        Text(
            stringResource(
                when (mode) {
                    SplitMode.ALL -> R.string.split_list_all
                    SplitMode.ONLY -> R.string.split_list_only
                    SplitMode.EXCEPT -> R.string.split_list_except
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = c.fgMuted,
        )

        if (mode != SplitMode.ALL) {
            DotField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.split_search),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.split_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.fgMuted,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    items(filtered, key = { it.info.packageName }) { app ->
                        val on = app.info.packageName in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    save(
                                        nextPicked = if (on) picked - app.info.packageName else picked + app.info.packageName,
                                    )
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val bitmap = remember(app.info.packageName) {
                                runCatching { app.info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                            }
                            if (bitmap != null) {
                                Image(bitmap, contentDescription = null, modifier = Modifier.size(32.dp))
                            } else {
                                BoxMark()
                            }
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(app.info.packageName, style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
                            }
                            Checkbox(checked = on, onCheckedChange = null)
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.settings_apply_next), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = DotTheme.colors.fgMuted)
        }
    }
}

@Composable
private fun BoxMark() {
    Box(
        Modifier
            .size(32.dp)
            .border(1.dp, DotTheme.colors.border, DotRadius)
            .clip(DotRadius)
            .background(DotTheme.colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text("·", style = MaterialTheme.typography.titleLarge, color = DotTheme.colors.fgMuted)
    }
}
