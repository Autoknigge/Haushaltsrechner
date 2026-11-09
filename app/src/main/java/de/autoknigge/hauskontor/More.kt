package de.autoknigge.hauskontor

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    val d = vm.data

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
            Hint("Bis zu 4 Personen. Bei Buchungen kannst du dann eintragen, wer gezahlt hat oder wessen Einnahme es ist. Die Übersicht rechnet aus, wer wem wie viel überweist.")
            d.persons.forEach { p -> Row2(p.name, "ändern", onClick = { personDialog = p }) }
            if (d.persons.size < Person.MAX) TextButton(onClick = { newPerson = true }) { Text("+ Person hinzufügen") }
            if (d.persons.size >= 2) {
                Text("Kosten teilen", fontSize = 12.sp, color = LocalMuted.current)
                Choice(SplitMode.values().toList(), d.split, { it.label }) { vm.setSplit(it) }
            }
        }

        HCard("Sicherung und Export", bg = O7, dark = true) {
            Hint("Deine Daten liegen nur auf diesem Gerät. Mit der Sicherung (ZIP, mit Fotos) ziehst du auf ein neues Telefon um: Datei übertragen und dort einlesen.")
            TextButton(onClick = { shareFile(ctx, Backup.write(ctx, vm.store, vm.data), "application/zip") }) { Text("Sicherung erstellen und teilen") }
            TextButton(onClick = { openFile.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }) { Text("Sicherung einlesen") }
            importMsg?.let { Hint(it) }
            TextButton(onClick = { shareText(ctx, "Hauskontor.csv", "text/csv", Calc.csv(vm.data)) }) { Text("Haushaltsbuch und Zählerstände als CSV teilen (Excel)") }
        }

        HCard("Werbung", bg = O8, dark = true) {
            Hint("Die App ist kostenlos und finanziert sich durch Werbung: eine kurze Anzeige beim Start und ein Banner am unteren Rand.")
            if (ads.privacyOptionsRequired) TextButton(onClick = { ads.showPrivacyOptions() }) { Text("Werbe-Einstellungen ändern") }
        }

        HCard("Datenschutz und Impressum", bg = O8, dark = true) {
            TextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text("Datenschutzerklärung öffnen") }
            TextButton(onClick = { uri.openUri(IMPRINT_URL) }) { Text("Impressum öffnen") }
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
            TextButton(onClick = { confirmWipe = true }) { Text("Alle Daten und Fotos löschen", color = MaterialTheme.colorScheme.error) }
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
