package de.autoknigge.hauskontor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun MetersScreen(vm: MainViewModel) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var meterDialog by remember { mutableStateOf<Meter?>(null) }
    var newMeter by remember { mutableStateOf(false) }
    var readingFor by remember { mutableStateOf<Meter?>(null) }
    var readingEdit by remember { mutableStateOf<Reading?>(null) }
    val d = vm.data
    val open = d.meters.firstOrNull { it.id == openId }
    BackHandler(enabled = open != null) { openId = null }

    if (open != null) {
        MeterDetail(vm, open, onBack = { openId = null }, onEditReading = { readingFor = open; readingEdit = it }, onNewReading = { readingFor = open; readingEdit = null })
    } else {
        Scroller {
            Button(onClick = { newMeter = true }) { Text("+ Zähler") }
            if (d.meters.isEmpty()) {
                HCard("Zähler ablesen", bg = O9, dark = true) {
                    Hint("Lege einen Zähler an (Strom, Gas, Wasser oder Wärme). Danach liest du den Stand per Foto ab: Die App erkennt die Zahl, du prüfst sie und speicherst. Das Foto bleibt als Beleg auf deinem Gerät.")
                }
            }
            d.meters.forEach { m ->
                val rs = Calc.readingsOf(d, m.id)
                HCard(
                    m.name + " (" + m.type.label + ")",
                    bg = when (m.type) { MeterType.STROM -> O5; MeterType.GAS -> TileToday; MeterType.WASSER -> CardTeal; MeterType.WAERME -> O2 },
                    dark = m.type == MeterType.STROM
                ) {
                    if (rs.isEmpty()) {
                        Hint("Noch keine Ablesung.")
                    } else {
                        val last = rs.last()
                        Row2("Letzter Stand", Calc.num(last.value, 1) + " " + m.type.unit, sub = Calc.dateText(last.date) + (if (last.viaOcr) " · per Foto" else ""))
                        if (rs.size >= 2) {
                            val prev = rs[rs.size - 2]
                            val days = (last.date - prev.date).coerceAtLeast(1)
                            val used = last.value - prev.value
                            Row2("Verbrauch seit " + Calc.dateText(prev.date), Calc.num(used, 1) + " " + m.type.unit, sub = "Ø " + Calc.num(used / days, 2) + " " + m.type.unit + " pro Tag")
                            if (Calc.hasPrice(m)) {
                                Row2("Kosten (geschätzt)", Calc.eur(Math.round(Calc.costRange(m, rs, prev.date, last.date, bonus = false) * 100)))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { readingFor = m; readingEdit = null }) { Text("Ablesen") }
                        CardOutlinedButton(onClick = { openId = m.id }) { Text("Verlauf") }
                        CardTextButton(onClick = { meterDialog = m }) { Text("Ändern") }
                    }
                }
            }
        }
    }

    if (newMeter || meterDialog != null) {
        MeterDialog(
            initial = meterDialog, newId = vm::newId,
            onSave = { vm.saveMeter(it); newMeter = false; meterDialog = null },
            onDelete = { meterDialog?.let { m -> vm.deleteMeter(m.id) }; if (openId == meterDialog?.id) openId = null; newMeter = false; meterDialog = null },
            onDismiss = { newMeter = false; meterDialog = null }
        )
    }
    readingFor?.let { m ->
        ReadingDialog(vm, m, readingEdit, onDone = { readingFor = null; readingEdit = null })
    }
}

@Composable
private fun MeterDetail(vm: MainViewModel, m: Meter, onBack: () -> Unit, onEditReading: (Reading) -> Unit, onNewReading: () -> Unit) {
    val ctx = LocalContext.current
    val rs = Calc.readingsOf(vm.data, m.id)
    Scroller {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Zurück") }
            Text(m.name, fontSize = 18.sp, color = Ink, modifier = Modifier.weight(1f))
        }
        Button(onClick = onNewReading) { Text("+ Neue Ablesung") }
        if (rs.size >= 2) {
            HCard("Verbrauch je Ablesezeitraum (" + m.type.unit + ")", bg = O8, dark = true) {
                val deltas = rs.zipWithNext { a, b -> b.value - a.value }.takeLast(12)
                val max = (deltas.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    deltas.forEach { v ->
                        Box(Modifier.weight(1f).height((80 * (v.coerceAtLeast(0.0) / max)).toFloat().dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(3.dp)).background(TileToday))
                    }
                }
            }
        }
        when (m.type) {
            MeterType.STROM -> { PartnerButton(Partner.STROM); PartnerNote() }
            MeterType.GAS -> { PartnerButton(Partner.GAS); PartnerNote() }
            else -> {}
        }
 
        if (Calc.hasPrice(m) && rs.size >= 2) {
            HCard("Tarife und Kosten", bg = O7, dark = true) {
                val span0 = rs.first().date
                val span1 = rs.last().date
                val periods = buildList {
                    val ts = m.tariffs.sortedBy { it.from }
                    if (m.priceCt > 0 || m.baseEurMonth > 0 || ts.isEmpty()) add(Tariff(Long.MIN_VALUE, m.priceCt, m.baseEurMonth, name = "Anfangstarif"))
                    addAll(ts)
                }
                periods.forEachIndexed { i, t ->
                    val start = maxOf(t.from, span0)
                    val end = minOf(periods.getOrNull(i + 1)?.from ?: Long.MAX_VALUE, span1)
                    if (end > start) {
                        val used = Calc.usedRange(rs, start, end)
                        val gross = Calc.costRange(m, rs, start, end, bonus = false)
                        val net = gross - t.newBonus - t.instantBonus
                        Row2(
                            (if (t.from == Long.MIN_VALUE) "Bis zur ersten Änderung" else "Ab " + Calc.dateText(t.from)) + (if (t.name.isNotBlank()) " · " + t.name else ""),
                            Calc.eur(Math.round(net * 100)),
                            sub = Calc.num(used, 1) + " " + m.type.unit + " · " + Calc.num(t.priceCt, 2) + " ct · " + Calc.num(t.baseEurMonth, 2) + " €/Monat · Kosten " + Calc.eur(Math.round(gross * 100)) +
                                (if (t.instantBonus > 0) " · Sofortbonus −" + Calc.eur(Math.round(t.instantBonus * 100)) else "") +
                                (if (t.newBonus > 0) " · Neukundenbonus −" + Calc.eur(Math.round(t.newBonus * 100)) else "")
                        )
                    }
                }
                Hint("Rechts steht der Betrag nach Abzug der Boni. Neuen Tarif oder Preisänderung unter „Ändern“ beim Zähler eintragen. Die Kosten sind eine Schätzung aus den Ablesungen.")
            }
        }
        HCard("Ablesungen", bg = O9, dark = true) {
            if (rs.isEmpty()) Hint("Noch keine Ablesung.")
            else {
                Row(Modifier.fillMaxWidth()) {
                    Text("Datum", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalInk.current, modifier = Modifier.weight(1.15f))
                    Text("Uhrzeit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalInk.current, modifier = Modifier.weight(0.8f))
                    Text("Zählernr.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalInk.current, modifier = Modifier.weight(1.4f))
                    Text("Stand", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalInk.current, modifier = Modifier.weight(1.1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Box(Modifier.size(48.dp))
                }
            }
            rs.reversed().forEachIndexed { idx, r ->
                val prev = rs.getOrNull(rs.size - 2 - idx)
                Column(Modifier.fillMaxWidth().clickable { onEditReading(r) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(Calc.dateText(r.date), fontSize = 12.5.sp, color = LocalInk.current, modifier = Modifier.weight(1.15f))
                        Text(Calc.timeText(r.timeMin).ifBlank { "–" }, fontSize = 12.5.sp, color = LocalInk.current, modifier = Modifier.weight(0.8f))
                        Text(r.meterNo.ifBlank { m.number }.ifBlank { "–" }, fontSize = 11.5.sp, color = LocalInk.current, modifier = Modifier.weight(1.4f), maxLines = 1)
                        Text(Calc.num(r.value, 3).trimEnd('0').trimEnd(','), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current, modifier = Modifier.weight(1.1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        PhotoThumb(vm, r.photo, 48)
                        if (r.photo == null) Box(Modifier.size(48.dp))
                    }
                    if (prev != null) Text("+" + Calc.num(r.value - prev.value, 1) + " " + m.type.unit + " seit " + Calc.dateText(prev.date) + (if (r.viaOcr) " · per Foto" else ""), fontSize = 11.sp, color = LocalMuted.current)
                }
                Box(Modifier.fillMaxWidth().height(6.dp))
            }
            if (rs.isNotEmpty()) TextButton(onClick = { shareText(ctx, "Zaehlerstaende-" + m.name.replace(" ", "_") + ".csv", "text/csv", Calc.readingsCsv(vm.data.copy(meters = listOf(m)))) }) {
                Text("Tabelle als CSV teilen (Excel)", color = LocalInk.current, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PhotoThumb(vm: MainViewModel, name: String?, sizeDp: Int) {
    if (name == null) return
    var big by remember { mutableStateOf(false) }
    val bmp = remember(name) {
        try {
            BitmapFactory.decodeFile(vm.store.photoFile(name).path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    } ?: return
    Image(bmp, contentDescription = "Foto", contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp)).clickable { big = true })
    if (big) {
        AlertDialog(
            onDismissRequest = { big = false },
            confirmButton = { TextButton(onClick = { big = false }) { Text("Schließen") } },
            text = { Image(bmp, contentDescription = "Foto", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
        )
    }
}

@Composable
private fun TariffDialog(initial: Tariff?, type: MeterType, onSave: (Tariff) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var from by remember { mutableStateOf(initial?.from ?: LocalDate.now().toEpochDay()) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.priceCt?.let { Calc.num(it, 2) } ?: "") }
    var base by remember { mutableStateOf(initial?.baseEurMonth?.let { Calc.num(it, 2) } ?: "") }
    var nb by remember { mutableStateOf(initial?.newBonus?.takeIf { it > 0 }?.let { Calc.num(it, 2) } ?: "") }
    var sb by remember { mutableStateOf(initial?.instantBonus?.takeIf { it > 0 }?.let { Calc.num(it, 2) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Tarif" else "Tarif ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Gültig ab", from) { from = it }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Anbieter / Tarif (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Arbeitspreis in ct je " + type.unit) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = base, onValueChange = { base = it }, label = { Text("Grundpreis in Euro je Monat") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nb, onValueChange = { nb = it }, label = { Text("Neukundenbonus in Euro") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sb, onValueChange = { sb = it }, label = { Text("Sofortbonus in Euro") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Hint("Die Boni verteilt die App für die Monatsschätzung auf die ersten 12 Monate ab dem Stichtag.")
                if (initial != null) TextButton(onClick = onDelete) { Text("Tarif entfernen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Tariff(from, Calc.parseNumber(price) ?: 0.0, Calc.parseNumber(base) ?: 0.0, Calc.parseNumber(nb) ?: 0.0, Calc.parseNumber(sb) ?: 0.0, name.trim()))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun MeterDialog(initial: Meter?, newId: () -> String, onSave: (Meter) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(initial?.type ?: MeterType.STROM) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var number by remember { mutableStateOf(initial?.number ?: "") }
    var price by remember { mutableStateOf(initial?.priceCt?.takeIf { it > 0 }?.let { Calc.num(it, 2) } ?: "") }
    var base by remember { mutableStateOf(initial?.baseEurMonth?.takeIf { it > 0 }?.let { Calc.num(it, 2) } ?: "") }
    var dec by remember { mutableStateOf(initial?.decimals ?: type.defaultDecimals) }
    var tariffs by remember { mutableStateOf(initial?.tariffs ?: emptyList()) }
    var tariffEdit by remember { mutableStateOf<Tariff?>(null) }
    var tariffNew by remember { mutableStateOf(false) }
    if (tariffNew || tariffEdit != null) {
        TariffDialog(
            tariffEdit, type,
            onSave = { t -> tariffs = (tariffs.filter { it != tariffEdit } + t).sortedBy { it.from }; tariffNew = false; tariffEdit = null },
            onDelete = { tariffs = tariffs.filter { it != tariffEdit }; tariffNew = false; tariffEdit = null },
            onDismiss = { tariffNew = false; tariffEdit = null }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Zähler" else "Zähler ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(MeterType.values().toList(), type, { it.label + " (" + it.unit + ")" }) { type = it; if (initial == null) dec = it.defaultDecimals }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (z. B. Strom Haus)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Zählernummer (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = price, onValueChange = { price = it }, label = { Text("Anfangstarif: Arbeitspreis in ct je " + type.unit) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = base, onValueChange = { base = it }, label = { Text("Anfangstarif: Grundpreis in Euro je Monat") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Hint("Mit Preisen schätzt die App die Kosten aus dem Verbrauch. Der Anfangstarif gilt bis zur ersten Tarifänderung.")
                Text("Tarifänderungen", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                tariffs.sortedBy { it.from }.forEach { t ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { tariffEdit = t }) {
                        Text("ab " + Calc.dateText(t.from) + " · " + Calc.num(t.priceCt, 2) + " ct · " + Calc.num(t.baseEurMonth, 2) + " €/Monat" +
                            (if (t.newBonus > 0 || t.instantBonus > 0) " · Bonus " + Calc.num(t.newBonus + t.instantBonus, 0) + " €" else ""), fontSize = 12.sp, maxLines = 2)
                    }
                }
                Button(onClick = { tariffNew = true }) { Text("+ Neuer Tarif ab Stichtag") }
                Text("Nachkommastellen auf dem Zählwerk (rote Ziffern)", fontSize = 12.sp)
                Choice(listOf(0, 1, 2, 3), dec, { it.toString() }) { dec = it }
                Hint("Zeigt das Zählwerk zum Beispiel 00123 und rot 456, wähle 3. Dann liest die App aus 123456 den Stand 123,456. Steht im Foto ein Komma, gilt das Komma.")
                if (initial != null) TextButton(onClick = onDelete) { Text("Zähler mit allen Ablesungen löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Meter(initial?.id ?: newId(), name.trim().ifBlank { type.label }, type, number.trim(), Calc.parseNumber(price) ?: 0.0, Calc.parseNumber(base) ?: 0.0, dec, tariffs.sortedBy { it.from }))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingDialog(vm: MainViewModel, m: Meter, initial: Reading?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(initial?.let { Calc.num(it.value, 3).replace(".", "").trimEnd('0').trimEnd(',') } ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toEpochDay()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var photo by remember { mutableStateOf(initial?.photo) }
    var viaOcr by remember { mutableStateOf(initial?.viaOcr ?: false) }
    var cands by remember { mutableStateOf<List<String>>(emptyList()) }
    var nums by remember { mutableStateOf<List<String>>(emptyList()) }
    var meterNo by remember { mutableStateOf(initial?.meterNo?.ifBlank { m.number } ?: m.number) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cropBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val prev = Calc.readingsOf(vm.data, m.id).filter { it.id != initial?.id && it.date <= date }.lastOrNull()

    fun process(uri: Uri) {
        scope.launch {
            busy = true
            msg = null
            vm.keepPhoto(uri)?.let { newName ->
                if (photo != null && photo != initial?.photo) vm.store.deletePhoto(photo)
                photo = newName
            }
            val bmp = withContext(Dispatchers.Default) { Ocr.loadBitmap(ctx, uri) }
            busy = false
            if (bmp == null) msg = "Das Foto konnte nicht geladen werden." else cropBmp = bmp
        }
    }

    fun read(full: android.graphics.Bitmap, crop: android.graphics.Bitmap?) {
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.Default) { Ocr.readMeter(full, crop ?: full, prev?.value, m.decimals) }
            cands = r.values
            nums = r.numbers
            if (r.numbers.isNotEmpty() && (meterNo.isBlank() || m.number.isBlank())) meterNo = r.numbers.first()
            if (r.values.isEmpty()) msg = "Keine Zahl erkannt. Nimm das Foto aus der Nähe, ohne Spiegelung, oder wähle den Ausschnitt enger."
            else {
                value = r.values.first().replace('.', ',')
                viaOcr = true
                msg = "Erkannt: " + value + ". Bitte mit dem Zähler vergleichen."
            }
            busy = false
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pending?.let { process(it) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) process(uri) }

    val v = Calc.parseNumber(value)
    fun cleanup() {
        if (photo != initial?.photo) vm.store.deletePhoto(photo)
    }

    cropBmp?.let { b ->
        CropDialog(b, onCancel = { cropBmp = null }, onDone = { c -> cropBmp = null; read(b, c) })
    }

    AlertDialog(
        onDismissRequest = { cleanup(); onDone() },
        title = { Text(if (initial == null) "Ablesen: " + m.name else "Ablesung ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = {
                        val dir = File(ctx.cacheDir, "aufnahmen").also { it.mkdirs() }
                        val f = File(dir, "z_" + System.currentTimeMillis() + ".jpg")
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        pending = u
                        take.launch(u)
                    }) { Text("📷 Foto", maxLines = 1) }
                    Button(modifier = Modifier.weight(1f), onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼 Galerie", maxLines = 1) }
                }
                Hint("Tipp: Nach dem Foto rahmst du die Ziffern ein. Das macht die Erkennung deutlich besser. Bei Spiegelung im Display leicht schräg fotografieren, bei dunklem Display ohne Blitz.")
                if (busy) Text("Wird gelesen …", fontSize = 13.sp, color = Gold)
                msg?.let { Text(it, fontSize = 12.5.sp) }
                if (cands.size > 1) {
                    Text("Weitere Möglichkeiten:", fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cands.drop(1).forEach { c -> AssistChip(onClick = { value = c.replace('.', ','); viaOcr = true }, label = { Text(c.replace('.', ',')) }) }
                    }
                }
                PhotoThumb(vm, photo, 96)
                OutlinedTextField(
                    value = value, onValueChange = { value = it; viaOcr = false }, label = { Text("Zählerstand in " + m.type.unit) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                if (v != null && prev != null && v < prev.value) {
                    Text("Der Stand liegt unter dem letzten Stand (" + Calc.num(prev.value, 1) + "). Zählerwechsel oder Tippfehler?", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(value = meterNo, onValueChange = { meterNo = it }, label = { Text("Zählernummer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (nums.size > 1 || (nums.isNotEmpty() && nums.first() != meterNo)) {
                    Text("Zählernummer im Foto gefunden:", fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        nums.forEach { n -> AssistChip(onClick = { meterNo = n }, label = { Text(n) }) }
                    }
                }
                if (m.number.isNotBlank() && meterNo.isNotBlank() && !meterNo.equals(m.number, ignoreCase = true)) {
                    Text("Die Nummer weicht von der des Zählers (" + m.number + ") ab. Zählerwechsel? Dann wird die neue Nummer beim Speichern übernommen.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                DateField("Datum", date) { date = it }
                Text("Uhrzeit: " + (Calc.timeText(initial?.timeMin ?: if (date == LocalDate.now().toEpochDay()) java.time.LocalTime.now().let { it.hour * 60 + it.minute } else -1).ifBlank { "keine Angabe" }), fontSize = 12.5.sp)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (initial != null) TextButton(onClick = { vm.deleteReading(initial.id); onDone() }) { Text("Ablesung löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = v != null && !busy, onClick = {
                if (initial != null && initial.photo != null && initial.photo != photo) vm.store.deletePhoto(initial.photo)
                val now = java.time.LocalTime.now()
                val t = initial?.timeMin ?: if (date == LocalDate.now().toEpochDay()) now.hour * 60 + now.minute else -1
                val no = meterNo.trim()
                if (no.isNotBlank() && !no.equals(m.number, ignoreCase = true)) vm.saveMeter(m.copy(number = no))
                vm.saveReading(Reading(initial?.id ?: vm.newId(), m.id, date, v ?: 0.0, photo, viaOcr, note.trim(), t, no))
                onDone()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = { cleanup(); onDone() }) { Text("Abbrechen") } }
    )
}
