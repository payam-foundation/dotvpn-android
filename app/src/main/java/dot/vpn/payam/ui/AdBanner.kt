// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dot.vpn.payam.AdInfo
import dot.vpn.payam.isSafeAdLink
import dot.vpn.payam.ui.theme.DotTheme

/**
 * Banner ads prefer a full-width cover image; card ads show icon + copy.
 * Only http(s) links are opened.
 */
@Composable
fun AdBanner(ad: AdInfo, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val c = DotTheme.colors
    val title = ad.title.trim()
    val desc = ad.desc.trim()
    val banner = ad.format == "banner"
    val imageUrl = ad.imageUrl.trim()
    val iconUrl = ad.iconUrl.trim()
    if (title.isEmpty() && desc.isEmpty() && imageUrl.isEmpty()) return
    val link = ad.link.trim()
    val clickable =
        if (isSafeAdLink(link)) {
            Modifier.clickable(role = Role.Button) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            }
        } else {
            Modifier
        }

    if (banner && imageUrl.isNotEmpty()) {
        Box(
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, c.border, DotRadius)
                .clip(DotRadius)
                .then(clickable),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title.ifEmpty { null },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentScale = ContentScale.Crop,
            )
        }
        return
    }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (banner) 72.dp else 56.dp)
            .border(1.dp, c.border, DotRadius)
            .clip(DotRadius)
            .background(c.surface)
            .then(clickable)
            .padding(horizontal = 16.dp, vertical = if (banner) 18.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!banner) {
            if (iconUrl.isNotEmpty()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = title.ifEmpty { null },
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(c.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title.firstOrNull()?.uppercase() ?: "·",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.fgMuted,
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title.isNotEmpty()) {
                Text(
                    title,
                    style = if (banner) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.fg,
                )
            }
            if (desc.isNotEmpty()) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
            }
        }
    }
}
