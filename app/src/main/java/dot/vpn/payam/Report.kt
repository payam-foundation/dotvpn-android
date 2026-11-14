// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

const val REPORT_ADDRESS = "report@dotvpn.org"

// ponytail: Binder caps an intent near 1 MB; above this the armor text is left out of the body
// and the attachment (or "Copy armor text") carries the report.
private const val MAX_BODY_ARMOR = 64 * 1024

/** What goes into the mail, free of Android types so LogicTest can assert it. */
data class ReportMail(val to: String, val subject: String, val text: String)

fun reportFileName(id: String) = "r-$id.dat"

fun reportMail(id: String, armor: String): ReportMail = ReportMail(
    to = REPORT_ADDRESS,
    subject = "Support request $id",
    text = if (armor.length <= MAX_BODY_ARMOR) "Report $id\n\n$armor" else "Report $id",
)

/** Device facts for the report. No network state: that would need a new permission. */
fun reportMeta(): String = JSONObject(
    mapOf("sdk" to Build.VERSION.SDK_INT.toString(), "model" to Build.MODEL, "abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "")),
).toString()

private fun reportDir(ctx: Context) = File(ctx.cacheDir, "reports")

/** Deletes every report file left in the cache. */
fun sweepReports(ctx: Context) {
    reportDir(ctx).listFiles()?.forEach { it.delete() }
}

/** Writes the sealed blob to cache and returns its content URI. */
fun writeReport(ctx: Context, id: String, blob: ByteArray): Uri {
    val dir = reportDir(ctx).apply { mkdirs() }
    val f = File(dir, reportFileName(id))
    f.writeBytes(blob)
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.reports", f)
}

fun reportIntent(mail: ReportMail, file: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "message/rfc822"
    putExtra(Intent.EXTRA_EMAIL, arrayOf(mail.to))
    putExtra(Intent.EXTRA_SUBJECT, mail.subject)
    putExtra(Intent.EXTRA_TEXT, mail.text)
    putExtra(Intent.EXTRA_STREAM, file)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
