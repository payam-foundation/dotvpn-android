// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dot.vpn.payam.R
import dot.vpn.payam.REPORT_ADDRESS
import dot.vpn.payam.reportFileName
import dot.vpn.payam.reportIntent
import dot.vpn.payam.reportMail
import dot.vpn.payam.reportMeta
import dot.vpn.payam.sweepReports
import dot.vpn.payam.ui.theme.DotTheme
import dot.vpn.payam.ui.theme.DotType
import dot.vpn.payam.writeReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile
import mobile.Report

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("", text))
}

/**
 * Report-a-problem sheet: window, note, a plaintext preview, then a mail with the sealed file
 * attached. If no mail app takes the intent it falls back to Save file and Copy.
 */
@Composable
fun ReportSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = DotTheme.colors
    val scope = rememberCoroutineScope()
    var window by remember { mutableStateOf(60) }
    var note by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var built by remember { mutableStateOf<Report?>(null) }
    var noMail by remember { mutableStateOf(false) }

    val sent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { sweepReports(ctx) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val r = built ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(r.blob) } }
    }

    fun build(previewOnly: Boolean, then: (Report) -> Unit) {
        failed = false
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Mobile.buildReport(reportMeta(), window.toLong(), note, previewOnly) } }
                .onSuccess(then)
                .onFailure { failed = true }
        }
    }

    fun send() = build(false) { r ->
        built = r
        val uri = writeReport(ctx, r.id, r.blob)
        try {
            sent.launch(reportIntent(reportMail(r.id, r.armor), uri))
        } catch (_: ActivityNotFoundException) {
            noMail = true
        }
    }

    val dismiss = {
        sweepReports(ctx)
        onDismiss()
    }

    DotSheet(title = stringResource(R.string.report_title), onDismiss = dismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.report_hint), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
            if (noMail) {
                val r = built
                Text(stringResource(R.string.report_no_mail, REPORT_ADDRESS), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { copy(ctx, REPORT_ADDRESS) }) { Text(stringResource(R.string.report_copy_address)) }
                if (r != null) {
                    TextButton(onClick = { save.launch(reportFileName(r.id)) }) { Text(stringResource(R.string.report_save_file)) }
                    TextButton(onClick = { copy(ctx, r.armor) }) { Text(stringResource(R.string.report_copy_armor)) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(30 to R.string.report_w30, 60 to R.string.report_w60).forEach { (m, label) ->
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RadioButton(selected = window == m, onClick = { window = m; preview = null })
                            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                DotField(
                    value = note,
                    onValueChange = { note = it.take(1024); preview = null },
                    placeholder = stringResource(R.string.report_note),
                    singleLine = false,
                    minLines = 3,
                )
                TextButton(onClick = {
                    if (preview != null) preview = null else build(true) { preview = it.preview }
                }) { Text(stringResource(if (preview != null) R.string.report_hide else R.string.report_view)) }
                preview?.let {
                    Text(it, style = DotType.mono, color = c.fgMuted, modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.report_consent, REPORT_ADDRESS), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
                if (failed) Text(stringResource(R.string.report_failed), color = c.fgMuted)
                DotButton(stringResource(R.string.report_send), onClick = { send() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
