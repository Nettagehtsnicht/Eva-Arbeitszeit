package de.eva.arbeitszeit

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Visit(val id: Long, val station: Int, val start: Long, val end: Long?, val manual: Boolean)

class Db private constructor(c: Context) : SQLiteOpenHelper(c, "eva.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE visits (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "station INTEGER NOT NULL, " +
                "start INTEGER NOT NULL, " +
                "ende INTEGER, " +
                "manual INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    companion object {
        /** Kürzere Aufenthalte (z. B. nur vorbeigefahren) werden verworfen */
        const val MIN_MS = 3 * 60_000L
        /** Kurz raus und wieder rein (z. B. GPS-Wackler) zählt als ein Besuch */
        const val MERGE_MS = 10 * 60_000L

        private const val COLS = "id, station, start, ende, manual"

        @Volatile private var inst: Db? = null

        fun get(c: Context): Db =
            inst ?: synchronized(this) { inst ?: Db(c.applicationContext).also { inst = it } }
    }

    private fun read(cursor: Cursor): List<Visit> {
        val out = ArrayList<Visit>()
        cursor.use {
            while (it.moveToNext()) {
                out.add(
                    Visit(
                        id = it.getLong(0),
                        station = it.getInt(1),
                        start = it.getLong(2),
                        end = if (it.isNull(3)) null else it.getLong(3),
                        manual = it.getInt(4) == 1
                    )
                )
            }
        }
        return out
    }

    fun openVisits(): List<Visit> =
        read(readableDatabase.rawQuery("SELECT $COLS FROM visits WHERE ende IS NULL ORDER BY start", null))

    /** Alle Besuche, die den Zeitraum [from, to) berühren */
    fun visitsOverlapping(from: Long, to: Long): List<Visit> =
        read(
            readableDatabase.rawQuery(
                "SELECT $COLS FROM visits WHERE start < ? AND (ende IS NULL OR ende > ?) ORDER BY start",
                arrayOf(to.toString(), from.toString())
            )
        )

    fun allVisits(): List<Visit> =
        read(readableDatabase.rawQuery("SELECT $COLS FROM visits ORDER BY start", null))

    private fun lastClosed(station: Int): Visit? =
        read(
            readableDatabase.rawQuery(
                "SELECT $COLS FROM visits WHERE station = ? AND ende IS NOT NULL ORDER BY ende DESC LIMIT 1",
                arrayOf(station.toString())
            )
        ).firstOrNull()

    fun insert(station: Int, start: Long, end: Long?, manual: Boolean): Long {
        val cv = ContentValues()
        cv.put("station", station)
        cv.put("start", start)
        if (end == null) cv.putNull("ende") else cv.put("ende", end)
        cv.put("manual", if (manual) 1 else 0)
        return writableDatabase.insert("visits", null, cv)
    }

    fun update(id: Long, start: Long, end: Long?) {
        val cv = ContentValues()
        cv.put("start", start)
        if (end == null) cv.putNull("ende") else cv.put("ende", end)
        writableDatabase.update("visits", cv, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("visits", "id = ?", arrayOf(id.toString()))
    }

    private fun closeVisit(v: Visit, t: Long) {
        if (!v.manual && t - v.start < MIN_MS) delete(v.id) else update(v.id, v.start, t)
    }

    /** Ankunft an einer Station. Gibt true zurück, wenn ein neuer Besuch beginnt. */
    @Synchronized
    fun arrive(station: Int, t: Long): Boolean {
        val open = openVisits()
        if (open.any { it.station == station }) return false
        // Wer bei der anderen Station noch "offen" ist, ist dort offensichtlich weg
        open.forEach { closeVisit(it, t) }
        val last = lastClosed(station)
        val lastEnd = last?.end
        if (last != null && lastEnd != null && !last.manual && t - lastEnd in 0 until MERGE_MS) {
            update(last.id, last.start, null)
            return false
        }
        insert(station, t, null, false)
        return true
    }

    /** Abfahrt. Gibt den abgeschlossenen Besuch zurück (oder null). */
    @Synchronized
    fun leave(station: Int, t: Long): Visit? {
        val v = openVisits().firstOrNull { it.station == station } ?: return null
        closeVisit(v, t)
        return v.copy(end = t)
    }
}
