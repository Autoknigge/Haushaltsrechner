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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
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
                            if (m.priceCt > 0 || m.baseEurMonth > 0) {
                                Row2("Kosten (geschätzt)", Calc.eur(Math.round((used * m.priceCt / 100.0 + m.baseEurMonth * days / 30.4) * 100)))
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
    val rs = Calc.readingsOf(vm.data, m.id)
    Scroller {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Zurück") }
            Text(m.name, fontSize = 18.sp, color = Ink, modifier = Modifier.weight(1f))
        }
        Button(onClick = onNewReading) { Text("+ Neue Ablesung") }
        when (m.type) {
            MeterType.STROM -> PartnerButton(Partner.STROM)
            MeterType.GAS -> PartnerButton(Partner.GAS)
            else -> {}
        }
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
        HCard("Ablesungen", bg = O9, dark = true) {
            if (rs.isEmpty()) Hint("Noch keine Ablesung.")
            rs.reversed().forEachIndexed { idx, r ->
                val prev = rs.getOrNull(rs.size - 2 - idx)
                Row(Modifier.fillMaxWidth().clickable { onEditReading(r) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Calc.num(r.value, 1) + " " + m.type.unit, fontSize = 14.sp, color = LocalInk.current)
                        Text(
                            Calc.dateText(r.date) + (if (prev != null) " · +" + Calc.num(r.value - prev.value, 1) + " seit " + Calc.dateText(prev.date) else "") + (if (r.viaOcr) " · Foto" else ""),
                            fontSize = 11.5.sp, color = LocalMuted.current
                        )
                    }
                    PhotoThumb(vm, r.photo, 48)
                }
                Box(Modifier.fillMaxWidth().height(6.dp))
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
private fun MeterDialog(initial: Meter?, newId: () -> String, onSave: (Meter) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(initial?.type ?: MeterType.STROM) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var number by remember { mutableStateOf(initial?.number ?: "") }
    var price by remember { mutableStateOf(initial?.priceCt?.takeIf { it > 0 }?.let { Calc.num(it, 2) } ?: "") }
    var base by remember { mutableStateOf(initial?.baseEurMonth?.takeIf { it > 0 }?.let { Calc.num(it, 2) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Zähler" else "Zähler ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(MeterType.values().toList(), type, { it.label + " (" + it.unit + ")" }) { type = it }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (z. B. Strom Haus)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Zählernummer (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = price, onValueChange = { price = it }, label = { Text("Arbeitspreis in ct je " + type.unit + " (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = base, onValueChange = { base = it }, label = { Text("Grundpreis in Euro je Monat (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Hint("Mit Preisen schätzt die App die Kosten aus dem Verbrauch.")
                if (initial != null) TextButton(onClick = onDelete) { Text("Zähler mit allen Ablesungen löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Meter(initial?.id ?: newId(), name.trim().ifBlank { type.label }, type, number.trim(), Calc.parseNumber(price) ?: 0.0, Calc.parseNumber(base) ?: 0.0))
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
    var value by remember { mutableStateOf(initial?.let { Calc.num(it.value, 2).replace(".", "") } ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toEpochDay()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var photo by remember { mutableStateOf(initial?.photo) }
    var viaOcr by remember { mutableStateOf(initial?.viaOcr ?: false) }
    var cands by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun process(uri: Uri) {
        scope.launch {
            busy = true
            msg = null
            val c = Ocr.candidates(ctx, uri)
            vm.keepPhoto(uri)?.let { newName ->
                if (photo != null && photo != initial?.photo) vm.store.deletePhoto(photo)
                photo = newName
            }
            cands = c
            if (c.isEmpty()) msg = "Keine Zahl erkannt. Gib den Stand von Hand ein oder mache ein neues Foto aus der Nähe."
            else {
                value = c.first().replace('.', ',')
                viaOcr = true
                msg = "Erkannt: " + c.first().replace('.', ',') + ". Bitte mit dem Zähler vergleichen."
            }
            busy = false
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pending?.let { process(it) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) process(uri) }

    val v = Calc.parseNumber(value)
    val prev = Calc.readingsOf(vm.data, m.id).filter { it.id != initial?.id && it.date <= date }.lastOrNull()
    fun cleanup() {
        if (photo != initial?.photo) vm.store.deletePhoto(photo)
    }

    AlertDialog(
        onDismissRequest = { cleanup(); onDone() },
        title = { Text(if (initial == null) "Ablesen: " + m.name else "Ablesung ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val dir = File(ctx.cacheDir, "aufnahmen").also { it.mkdirs() }
                        val f = File(dir, "z_" + System.currentTimeMillis() + ".jpg")
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        pending = u
                        take.launch(u)
                    }) { Text("📷 Foto") }
                    OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galerie") }
                }
                if (busy) Text("Zahl wird gelesen …", fontSize = 13.sp, color = Gold)
                msg?.let { Text(it, fontSize = 12.5.sp, color = Muted) }
                if (cands.size > 1) {
                    Text("Weitere erkannte Zahlen:", fontSize = 12.sp)
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
                DateField("Datum", date) { date = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (initial != null) TextButton(onClick = { vm.deleteReading(initial.id); onDone() }) { Text("Ablesung löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = v != null && !busy, onClick = {
                if (initial != null && initial.photo != null && initial.photo != photo) vm.store.deletePhoto(initial.photo)
                vm.saveReading(Reading(initial?.id ?: vm.newId(), m.id, date, v ?: 0.0, photo, viaOcr, note.trim()))
                onDone()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = { cleanup(); onDone() }) { Text("Abbrechen") } }
    )
}
