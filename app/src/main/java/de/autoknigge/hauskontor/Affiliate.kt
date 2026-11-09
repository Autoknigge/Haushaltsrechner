package de.autoknigge.hauskontor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Partnerlinks (Werbung). */
enum class Partner(val label: String, val url: String) {
    STROM("Stromanbieter vergleichen", "https://www.c24n.de/Y7HNRTP"),
    GAS("Gasanbieter vergleichen", "https://www.c24n.de/UGw1V8X"),
    DSL("DSL-Anbieter vergleichen", "https://www.c24n.de/A5gdbr7"),
    MOBIL("Handytarife vergleichen", "https://www.c24n.de/Ed3wQYg"),
    HAFTPFLICHT("Haftpflichtversicherung vergleichen", "https://www.tarifcheck.com/GbaGtrA"),
    HAUSRAT("Hausratversicherung vergleichen", "https://a.partner-versicherung.de/click.php?partner_id=203308&ad_id=15&deep=hausratversicherung")
}

/** Goldener Knopf mit 3D-Wirkung (Verlauf, helle Oberkante, dunkle Unterkante, Schatten) und Hinweis „Werbepartner". */
@Composable
fun PartnerButton(p: Partner) {
    val uri = LocalUriHandler.current
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(8.dp, shape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFE9A0), Color(0xFFE3B341), Color(0xFFB8860B))), shape)
                .border(2.dp, Brush.verticalGradient(listOf(Color(0xFFFFF6D0), Color(0xFF8A6508))), shape)
                .clickable { try { uri.openUri(p.url) } catch (_: Exception) {} }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("★  " + p.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B1D00), textAlign = TextAlign.Center)
        }
        Text("Werbepartner · Anzeige", fontSize = 10.5.sp, color = LocalMuted.current, modifier = Modifier.padding(top = 3.dp))
    }
}
