package de.autoknigge.hauskontor

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

private fun shareFile(ctx: Context, f: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Teilen"))
}

private fun shareText(ctx: Context, name: String, mime: String, text: String) {
    val dir = File(ctx.cacheDir, "teilen").also { it.mkdirs() }
    val f = File(dir, name)
    f.writeText(text)
    shareFile(ctx, f, mime)
}

@Composable
fun MoreScreen(vm: MainViewModel, ads: AdsController) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    var confirmWipe by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf<Backup.Loaded?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var personDialog by remember { mutableStateOf<Person?>(null) }
    var newPerson by remember { mutableStateOf(false) }
    var houseDialog by remember { mutableStateOf(false) }
    val d = vm.data

    var wgCode by remember { mutableStateOf(Wg.code(ctx)) }
    var wgDialog by remember { mutableStateOf(false) }
    var wgMsg by remember { mutableStateOf<String?>(null) }
    val wgOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            when (val r = Wg.read(ctx, u, wgCode)) {
                is Wg.Result.Ok -> {
                    val before = vm.data.entries.size + vm.data.recurrings.size + vm.data.meters.size + vm.data.readings.size
                    vm.importBackup(Backup.Loaded(r.data, emptyMap()), replace = false)
                    val after = vm.data.entries.size + vm.data.recurrings.size + vm.data.meters.size + vm.data.readings.size
                    wgMsg = if (after > before) "Abgleich fertig: " + (after - before) + " neue Einträge übernommen." else "Abgleich fertig: du bist schon auf dem gleichen Stand."
                }
                Wg.Result.WrongCode -> wgMsg = "Die Datei gehört nicht zu deiner WG (anderer WG-Code) oder ist beschädigt. Es wurde nichts übernommen."
                Wg.Result.NotWg -> wgMsg = "Das ist keine WG-Abgleichsdatei von Hauskontor (.hkwg)."
            }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        if (u != null) {
            try {
                val f = Backup.write(ctx, vm.store, vm.data)
                ctx.contentResolver.openOutputStream(u)?.use { o -> f.inputStream().use { it.copyTo(o) } }
                importMsg = "Sicherung gespeichert."
            } catch (e: Exception) {
                importMsg = "Speichern hat nicht geklappt."
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            val l = Backup.read(ctx, u)
            if (l == null) importMsg = "Die Datei konnte nicht gelesen werden. Gewählt werden muss eine Sicherung von Hauskontor (.zip oder .json)."
            else {
                importMsg = null
                loaded = l
            }
        }
    }

    Scroller {
        HCard("Haushalt (Paar oder WG)", bg = O6, dark = true) {
            Row2(
                d.household.ifBlank { "Name des Haushalts" }, "ändern",
                sub = d.address.ifBlank { "Anschrift oder Zusatz, steht oben neben dem Logo" },
                onClick = { houseDialog = true }
            )
            Hint("Bis zu 4 Personen. Bei Buchungen kannst du dann eintragen, wer gezahlt hat oder wessen Einnahme es ist. Die Übersicht rechnet aus, wer wem wie viel überweist.")
            d.persons.forEach { p -> Row2(p.name, "ändern", onClick = { personDialog = p }) }
            if (d.persons.size < Person.MAX) CardTextButton(onClick = { newPerson = true }) { Text("+ Person hinzufügen") }
            if (d.persons.size >= 2) {
                Text("Kosten teilen", fontSize = 12.sp, color = LocalMuted.current)
                Choice(SplitMode.values().toList(), d.split, { it.label }) { vm.setSplit(it) }
            }
        }

        HCard("Sicherung und Export", bg = O7, dark = true) {
            Hint("Deine Daten liegen nur auf diesem Gerät. Mit der Sicherung (ZIP, mit Fotos) ziehst du auf ein neues Telefon um: Datei übertragen und dort einlesen.")
            CardTextButton(onClick = { saveFile.launch("Hauskontor-Sicherung.zip") }) { Text("Sicherung auf dem Gerät speichern") }
            CardTextButton(onClick = { shareFile(ctx, Backup.write(ctx, vm.store, vm.data), "application/zip") }) { Text("Sicherung senden an … (WhatsApp, Mail, Quick Share, Bluetooth)") }
            CardTextButton(onClick = { openFile.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }) { Text("Sicherung einlesen") }
            importMsg?.let { Hint(it) }
            CardTextButton(onClick = { shareText(ctx, "Hauskontor.csv", "text/csv", Calc.csv(vm.data)) }) { Text("Haushaltsbuch und Zählerstände als CSV teilen (Excel)") }
        }

        HCard("WG-Abgleich", bg = CardGreen, dark = true) {
            Hint("Alle Mitbewohner nutzen denselben WG-Code. Die Abgleichsdatei ist damit verschlüsselt: Nur wer den Code kennt, kann sie lesen. Ihr schickt sie euch per WhatsApp, Quick Share, Bluetooth oder Mail. Es gibt keinen Server, die Daten bleiben bei euch.")
            if (wgCode.isBlank()) {
                CardTextButton(onClick = { Wg.newCode().also { Wg.setCode(ctx, it); wgCode = it } }) { Text("Neuen WG-Code erzeugen") }
                CardTextButton(onClick = { wgDialog = true }) { Text("WG-Code von einem Mitbewohner eingeben") }
            } else {
                Row2("WG-Code", wgCode, sub = "Gib ihn nur an Mitbewohner weiter, am besten persönlich.")
                CardTextButton(onClick = { shareFile(ctx, Wg.write(ctx, vm.data, wgCode), "application/octet-stream") }) { Text("Abgleichsdatei senden an …") }
                CardTextButton(onClick = { wgOpen.launch(arrayOf("*/*")) }) { Text("Abgleichsdatei einlesen") }
                CardTextButton(onClick = { wgDialog = true }) { Text("WG-Code ändern oder entfernen") }
            }
            wgMsg?.let { Hint(it) }
            Hint("Beim Abgleich werden neue Buchungen, Fixkosten, Zähler und Ablesungen ergänzt. Bereits vorhandene Einträge und Fotos bleiben unverändert, Löschungen werden nicht übertragen.")
        }

        HCard("Werbung", bg = O8, dark = true) {
            Hint("Die App ist kostenlos und finanziert sich durch Werbung: eine kurze Anzeige beim Start und ein Banner am unteren Rand.")
            if (ads.privacyOptionsRequired) CardTextButton(onClick = { ads.showPrivacyOptions() }) { Text("Werbe-Einstellungen ändern") }
        }

        HCard("Datenschutz und Impressum", bg = O8, dark = true) {
            CardTextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text("Datenschutzerklärung öffnen") }
            CardTextButton(onClick = { uri.openUri(IMPRINT_URL) }) { Text("Impressum öffnen") }
        }

        HCard("Info", bg = O9, dark = true) {
            Text("Version " + BuildConfig.VERSION_NAME, color = LocalInk.current, fontSize = 14.sp)
            Hint("Fotos werden auf dem Gerät ausgewertet und nicht hochgeladen. Erkannte Zahlen und Beträge sind Vorschläge, bitte immer mit dem Original vergleichen. Die Kostenschätzungen ersetzen keine Abrechnung.")
            Text(
                "Eine Idee von autoknigge.de",
                color = TileToday, fontSize = 14.sp, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { uri.openUri(HOME_URL) }.padding(vertical = 4.dp)
            )
        }

        HCard("Daten löschen", bg = O9, dark = true) {
            CardTextButton(onClick = { confirmWipe = true }, color = BadRed) { Text("Alle Daten und Fotos löschen", color = BadRed) }
        }
    }

    loaded?.let { l ->
        AlertDialog(
            onDismissRequest = { loaded = null },
            title = { Text("Sicherung gefunden") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        l.data.entries.size.toString() + " Buchungen, " + l.data.recurrings.size + " regelmäßige Zahlungen, " + l.data.meters.size + " Zähler mit " +
                            l.data.readings.size + " Ablesungen, " + l.data.persons.size + " Personen und " + l.photos.size + " Fotos.",
                        fontSize = 14.sp
                    )
                    Text("„Hinzufügen“ ergänzt, was noch fehlt, und lässt Vorhandenes unverändert. „Ersetzen“ löscht die jetzigen Daten und nimmt die der Sicherung.", fontSize = 12.5.sp)
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { vm.importBackup(l, replace = false); loaded = null; importMsg = "Sicherung hinzugefügt." }) { Text("Hinzufügen") }
                    TextButton(onClick = { vm.importBackup(l, replace = true); loaded = null; importMsg = "Sicherung eingespielt, die bisherigen Daten wurden ersetzt." }) {
                        Text("Ersetzen", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { loaded = null }) { Text("Abbrechen") } }
        )
    }

    if (wgDialog) {
        var c by remember { mutableStateOf(wgCode) }
        val ok = Wg.valid(Wg.normalize(c))
        AlertDialog(
            onDismissRequest = { wgDialog = false },
            title = { Text("WG-Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("z. B. K7MQ-4XPD-92HB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Der Code besteht aus 12 Zeichen (Buchstaben und Ziffern).", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(enabled = ok, onClick = { Wg.setCode(ctx, c); wgCode = Wg.code(ctx); wgDialog = false; wgMsg = null }) { Text("Speichern") } },
            dismissButton = {
                Row {
                    if (wgCode.isNotBlank()) TextButton(onClick = { Wg.setCode(ctx, ""); wgCode = ""; wgDialog = false; wgMsg = null }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { wgDialog = false }) { Text("Abbrechen") }
                }
            }
        )
    }

    if (houseDialog) {
        var hn by remember { mutableStateOf(d.household) }
        var ha by remember { mutableStateOf(d.address) }
        AlertDialog(
            onDismissRequest = { houseDialog = false },
            title = { Text("Haushalt benennen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hn, onValueChange = { hn = it }, label = { Text("Name (z. B. Familie Meier, WG Nord)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ha, onValueChange = { ha = it }, label = { Text("Anschrift oder Zusatz (optional, zweite Zeile)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.setHousehold(hn, ha); houseDialog = false }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { houseDialog = false }) { Text("Abbrechen") } }
        )
    }

    if (newPerson || personDialog != null) {
        var name by remember(personDialog, newPerson) { mutableStateOf(personDialog?.name ?: "") }
        AlertDialog(
            onDismissRequest = { newPerson = false; personDialog = null },
            title = { Text(if (personDialog == null) "Neue Person" else "Person ändern") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    personDialog?.let { p ->
                        TextButton(onClick = { vm.deletePerson(p.id); newPerson = false; personDialog = null }) {
                            Text("Person entfernen", color = MaterialTheme.colorScheme.error)
                        }
                        Text("Ihre Buchungen bleiben erhalten und werden zu „Gemeinsam“.", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.savePerson(Person(personDialog?.id ?: vm.newId(), name.trim()))
                    newPerson = false
                    personDialog = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { newPerson = false; personDialog = null }) { Text("Abbrechen") } }
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Alles löschen?") },
            text = { Text("Buchungen, regelmäßige Zahlungen, Zähler, Ablesungen, Personen und Fotos werden unwiderruflich gelöscht.") },
            confirmButton = { TextButton(onClick = { vm.wipe(); confirmWipe = false }) { Text("Ja, löschen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Abbrechen") } }
        )
    }
}
