package de.autoknigge.hauskontor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    var data by mutableStateOf(store.load())
        private set

    private fun commit(d: Data) {
        data = d
        store.save(d)
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun saveEntry(e: Entry) = commit(
        data.copy(entries = if (data.entries.any { it.id == e.id }) data.entries.map { if (it.id == e.id) e else it } else data.entries + e)
    )

    fun deleteEntry(id: String) = commit(data.copy(entries = data.entries.filter { it.id != id }))

    fun saveRecurring(r: Recurring) = commit(
        data.copy(recurrings = if (data.recurrings.any { it.id == r.id }) data.recurrings.map { if (it.id == r.id) r else it } else data.recurrings + r)
    )

    fun deleteRecurring(id: String) = commit(data.copy(recurrings = data.recurrings.filter { it.id != id }))

    fun saveMeter(m: Meter) = commit(
        data.copy(meters = if (data.meters.any { it.id == m.id }) data.meters.map { if (it.id == m.id) m else it } else data.meters + m)
    )

    fun deleteMeter(id: String) {
        data.readings.filter { it.meterId == id }.forEach { store.deletePhoto(it.photo) }
        commit(data.copy(meters = data.meters.filter { it.id != id }, readings = data.readings.filter { it.meterId != id }))
    }

    fun saveReading(r: Reading) = commit(
        data.copy(readings = if (data.readings.any { it.id == r.id }) data.readings.map { if (it.id == r.id) r else it } else data.readings + r)
    )

    fun deleteReading(id: String) {
        data.readings.firstOrNull { it.id == id }?.let { store.deletePhoto(it.photo) }
        commit(data.copy(readings = data.readings.filter { it.id != id }))
    }

    fun saveEntries(list: List<Entry>) = commit(data.copy(entries = data.entries + list))

    fun savePerson(p: Person) {
        if (data.persons.none { it.id == p.id } && data.persons.size >= Person.MAX) return
        commit(data.copy(persons = if (data.persons.any { it.id == p.id }) data.persons.map { if (it.id == p.id) p else it } else data.persons + p))
    }

    fun deletePerson(id: String) = commit(
        data.copy(
            persons = data.persons.filter { it.id != id },
            entries = data.entries.map { if (it.personId == id) it.copy(personId = null) else it },
            recurrings = data.recurrings.map { if (it.personId == id) it.copy(personId = null) else it }
        )
    )

    fun setHousehold(name: String, address: String) = commit(data.copy(household = name.trim(), address = address.trim()))

    fun setSplit(mode: SplitMode) = commit(data.copy(split = mode))

    /** Spielt eine Sicherung ein: entweder alles ersetzen oder fehlende Einträge hinzufügen. */
    fun importBackup(l: Backup.Loaded, replace: Boolean) {
        if (replace) store.clearPhotos()
        l.photos.forEach { (n, b) -> store.writePhoto(n, b) }
        commit(if (replace) l.data else Backup.merge(data, l.data))
    }

    /** Legt ein Foto verkleinert im App-Speicher ab und gibt den Dateinamen zurück. */
    fun keepPhoto(uri: Uri): String? = store.savePhoto(uri)

    fun wipe() {
        store.wipe()
        data = Data()
    }
}
