package de.autoknigge.hauskontor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

const val PRIVACY_URL = "https://autoknigge.de/hauskontor/datenschutz.html"
const val IMPRINT_URL = "https://autoknigge.de/impressum.html"
const val HOME_URL = "https://autoknigge.de"

enum class Tab(val label: String, val icon: String) {
    Dash("Übersicht", "📊"),
    Book("Buch", "📒"),
    Years("Jahre", "📈"),
    Meters("Zähler", "⚡"),
    More("Mehr", "⚙️")
}

@Composable
fun Root(vm: MainViewModel, ads: AdsController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthIdx by rememberSaveable { mutableIntStateOf(YearMonth.now().let { it.year * 12 + it.monthValue - 1 }) }
    val ym = YearMonth.of(monthIdx / 12, monthIdx % 12 + 1)
    val onMonth: (Int) -> Unit = { monthIdx += it }

    Column(Modifier.fillMaxSize().background(Forest).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Image(
                painter = painterResource(R.drawable.logo_header),
                contentDescription = "Hauskontor",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(52.dp).aspectRatio(1143f / 511f).clip(RoundedCornerShape(10.dp))
            )
            Text(BuildConfig.VERSION_NAME, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
            val h = vm.data.household
            val a = vm.data.address
            if (h.isNotBlank() || a.isNotBlank()) {
                Column(Modifier.weight(1f).padding(start = 10.dp, bottom = 2.dp), horizontalAlignment = Alignment.End) {
                    if (h.isNotBlank() && a.isNotBlank()) {
                        Text(h, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gold, maxLines = 1)
                        Text(a, fontSize = 11.sp, color = Muted, maxLines = 1)
                    } else {
                        Text(h.ifBlank { a }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gold, maxLines = 2)
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (Tab.values()[tab]) {
                Tab.Dash -> DashboardScreen(vm, ym, onMonth)
                Tab.Book -> BookScreen(vm, ym, onMonth)
                Tab.Years -> YearsScreen(vm)
                Tab.Meters -> MetersScreen(vm)
                Tab.More -> MoreScreen(vm, ads)
            }
        }
        BannerBar(ads)
        NavigationBar {
            Tab.values().forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Text(t.icon, fontSize = 18.sp) },
                    label = { Text(t.label, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }
    }
}

// ---------- Bausteine ----------

/** Kachel mit Farbverlauf von oben links nach unten rechts. Bei hellen Farben ([dark] = true) steht dunkle Schrift darauf. */
@Composable
fun HCard(title: String?, modifier: Modifier = Modifier, bg: Color = Panel, dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val ink = if (dark) DarkInk else Color.White
    val muted = if (dark) DarkMuted else WarmMuted
    androidx.compose.runtime.CompositionLocalProvider(LocalInk provides ink, LocalMuted provides muted) {
        Column(
            modifier.fillMaxWidth().clip(shape).background(tileBrush(bg, dark)).border(1.dp, Line, shape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ink)
            content()
        }
    }
}

@Composable
fun Stat(label: String, value: String, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = LocalMuted.current)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (color == Color.Unspecified) LocalInk.current else color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthBar(ym: YearMonth, onMonth: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onMonth(-1) }) { Text("‹", fontSize = 22.sp) }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Calc.monthText(ym), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("  ▾", fontSize = 16.sp, color = Gold)
        }
        TextButton(onClick = { onMonth(1) }) { Text("›", fontSize = 22.sp) }
    }
    if (open) {
        var year by remember { mutableIntStateOf(ym.year) }
        val now = YearMonth.now()
        fun go(y: Int, m: Int) {
            onMonth((y * 12 + m) - (ym.year * 12 + ym.monthValue))
            open = false
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Panel, textContentColor = Color.White, titleContentColor = Gold,
            title = { Text("Monat wählen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { year-- }) { Text("‹", fontSize = 24.sp) }
                        Text(year.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        TextButton(onClick = { year++ }) { Text("›", fontSize = 24.sp) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..12).forEach { m ->
                            FilterChip(
                                colors = chipColors(),
                                selected = year == ym.year && m == ym.monthValue,
                                onClick = { go(year, m) },
                                label = { Text(Calc.monthShort(m), fontSize = 15.sp) }
                            )
                        }
                    }
                    TextButton(onClick = { go(now.year, now.monthValue) }) { Text("Heute: " + Calc.monthText(now), color = Gold) }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Schließen") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach {
            FilterChip(
                selected = it == selected,
                onClick = { onSelect(it) },
                label = { Text(label(it)) },
                colors = FilterChipDefaults.filterChipColors(labelColor = LocalInk.current, selectedLabelColor = Ink, selectedContainerColor = Panel)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, epochDay: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, border = BorderStroke(1.dp, Line)) { Text(label + ": " + Calc.dateText(epochDay)) }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onChange(Math.floorDiv(it, 86_400_000L)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } }
        ) { DatePicker(state = st) }
    }
}

@Composable
fun Scroller(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
fun Hint(text: String) = Text(text, fontSize = 12.5.sp, color = LocalMuted.current, lineHeight = 17.sp)

@Composable
fun Row2(left: String, right: String, rightColor: Color = Color.Unspecified, sub: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(left, fontSize = 14.sp, color = LocalInk.current)
            if (sub != null) Text(sub, fontSize = 11.5.sp, color = LocalMuted.current)
        }
        Text(right, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (rightColor == Color.Unspecified) LocalInk.current else rightColor)
    }
}

/** Textknopf in Kacheln: dunkle Schrift, damit sie auf Orange lesbar ist. */
@Composable
fun CardTextButton(onClick: () -> Unit, color: Color = DarkInk, content: @Composable () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = color)) { content() }
}

@Composable
fun CardOutlinedButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalInk.current),
        border = BorderStroke(1.dp, LocalInk.current)
    ) { content() }
}


/** Eingabefeld für eine Kategorie: frei tippen (✕ leert das Feld) oder eine bekannte Kategorie antippen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        trailingIcon = { if (value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("✕", fontSize = 16.sp) } }
    )
    val q = value.trim()
    // Beim Tippen nur passende Vorschläge: erst die mit gleichem Anfang, dann solche, die den Text enthalten.
    val shown = if (q.isEmpty()) options else options.filter { it.startsWith(q, ignoreCase = true) } + options.filter { !it.startsWith(q, ignoreCase = true) && it.contains(q, ignoreCase = true) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { o -> FilterChip(colors = chipColors(), selected = o.equals(q, ignoreCase = true), onClick = { onChange(o) }, label = { Text(o) }) }
    }
}
