package de.autoknigge.hauskontor

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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

private fun share(ctx: Context, name: String, mime: String, text: String) {
    val dir = File(ctx.cacheDir, "teilen").also { it.mkdirs() }
    val f = File(dir, name)
    f.writeText(text)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Teilen"))
}

@Composable
fun MoreScreen(vm: MainViewModel, ads: AdsController) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    var confirmWipe by remember { mutableStateOf(false) }

    Scroller {
        HCard("Daten sichern und exportieren") {
            Hint("Deine Daten liegen nur auf diesem Gerät. Mit dem Export kannst du sie in Excel öffnen oder als Sicherung aufbewahren.")
            TextButton(onClick = { share(ctx, "Hauskontor.csv", "text/csv", Calc.csv(vm.data)) }) { Text("Haushaltsbuch und Zählerstände als CSV teilen") }
            TextButton(onClick = { share(ctx, "Hauskontor-Sicherung.json", "application/json", vm.data.toJson().toString(2)) }) { Text("Sicherung als Datei teilen (ohne Fotos)") }
        }

        HCard("Werbung") {
            Hint("Die App ist kostenlos und finanziert sich durch Werbung: eine kurze Anzeige beim Start und ein Banner am unteren Rand.")
            if (ads.privacyOptionsRequired) TextButton(onClick = { ads.showPrivacyOptions() }) { Text("Werbe-Einstellungen ändern") }
        }

        HCard("Datenschutz und Impressum") {
            TextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text("Datenschutzerklärung öffnen") }
            TextButton(onClick = { uri.openUri(IMPRINT_URL) }) { Text("Impressum öffnen") }
        }

        HCard("Info") {
            Text("Version " + BuildConfig.VERSION_NAME, color = Ink, fontSize = 14.sp)
            Hint("Zähler-Fotos werden auf dem Gerät ausgewertet und nicht hochgeladen. Die erkannte Zahl ist ein Vorschlag, bitte immer mit dem Zähler vergleichen. Die Kostenschätzungen ersetzen keine Abrechnung.")
            Text(
                "Eine Idee von autoknigge.de",
                color = Gold, fontSize = 14.sp, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { uri.openUri(HOME_URL) }.padding(vertical = 4.dp)
            )
        }

        HCard("Daten löschen") {
            TextButton(onClick = { confirmWipe = true }) { Text("Alle Daten und Fotos löschen", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Alles löschen?") },
            text = { Text("Buchungen, Fixkosten, Zähler, Ablesungen und Fotos werden unwiderruflich gelöscht.") },
            confirmButton = { TextButton(onClick = { vm.wipe(); confirmWipe = false }) { Text("Ja, löschen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Abbrechen") } }
        )
    }
}
