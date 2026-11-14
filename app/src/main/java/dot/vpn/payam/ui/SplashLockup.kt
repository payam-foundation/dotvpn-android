// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dot.vpn.payam.R
import dot.vpn.payam.ui.theme.DotTheme
import dot.vpn.payam.ui.theme.Geist

@Composable
fun SplashLockup(modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(c.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            HopMark(HopState.Connecting, stringResource(R.string.app_name), size = 96.dp)
            Text(
                stringResource(R.string.app_name),
                color = c.fg,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                letterSpacing = (-0.03).em,
            )
        }
    }
}
