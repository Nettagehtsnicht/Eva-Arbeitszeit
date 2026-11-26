package de.eva.arbeitszeit

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQ_FG = 1
        private const val REQ_BG = 2
        private const val REQ_NOTIF = 3
        private const val REQ_EXPORT = 10

        private const val BG = 0xFFF3F5F4.toInt()
        private const val DARK = 0xFF1C2B27.toInt()
        private const val GREY = 0xFF6B7672.toInt()
        private const val GREEN = 0xFF1F8A4C.toInt()
        private const val ACCENT = 0xFF1F6F5C.toInt()
        private const val WARN_BG = 0xFFFFF4E0.toInt()
        private const val WARN = 0xFF8A5300.toInt()
        private const val LINE = 0xFFE3E7E5.toInt()
    }

    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var monthOffset = 0
    private var geoError: String? = null
    private var geoActive = false
    private var exportFrom = 0L
    private var exportTo = 0L

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val dayFmt = SimpleDateFormat("EE dd.MM.", Locale.GERMANY)
    private val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val shortDate = SimpleDateFormat("dd.MM.", Locale.GERMANY)
    private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)

    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 60_000)
        }
    }

    // ---------------------------------------------------------------- Lebenszyklus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ACCENT
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(BG)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(40))
        }
        scroll.addView(root)
        setContentView(scroll)
        findMissingLocations(force = false)
    }

    override fun onResume() {
        super.onResume()
        registerGeo()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun registerGeo() {
        GeofenceHelper.register(this) { ok, msg ->
            runOnUiThread {
                geoActive = ok
                geoError = msg
                render()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BG && !GeofenceHelper.hasBackground(this)) {
            toast("Bitte bei „Standort“ auf „Immer zulassen“ stellen.")
            openAppSettings()
        }
        registerGeo()
    }

    // ---------------------------------------------------------------- Oberfläche

    private fun render() {
        root.removeAllViews()
        val now = System.currentTimeMillis()
        val db = Db.get(this)

        root.addView(text("Eva Arbeitszeit", 26f, DARK, bold = true))
        root.addView(
            text(
                if (geoActive) "● Automatische Erkennung läuft" else "○ Automatische Erkennung noch nicht aktiv",
                14f, if (geoActive) GREEN else WARN
            ).also { it.setPadding(0, dp(2), 0, dp(12)) }
        )

        renderWarnings(now, db)

        // --- Übersicht je Station
        val dayStart = Stats.startOfDay(now)
        val weekStart = Stats.startOfWeek(now)
        val monthStart = Stats.startOfMonth(now, 0)
        val recent = db.visitsOverlapping(minOf(weekStart, monthStart), now + 1)
        val open = db.openVisits()

        for (st in Stations.ALL) {
            val card = card()
            card.addView(text(st.name, 19f, st.color, bold = true))
            val o = open.firstOrNull { it.station == st.id }
            val status = if (o != null) {
                val since = if (o.start < dayStart) "${shortDate.format(Date(o.start))} ${timeFmt.format(Date(o.start))}"
                else timeFmt.format(Date(o.start))
                "● Gerade da seit $since Uhr (${Stats.fmt(now - o.start)})"
            } else "Gerade nicht da"
            card.addView(text(status, 14f, if (o != null) GREEN else GREY).also { it.setPadding(0, dp(2), 0, dp(8)) })
            card.addView(statRow("Heute", Stats.sum(recent, st.id, dayStart, now + 1, now)))
            card.addView(statRow("Diese Woche", Stats.sum(recent, st.id, weekStart, now + 1, now)))
            card.addView(statRow("Dieser Monat", Stats.sum(recent, st.id, monthStart, now + 1, now)))
            root.addView(card)
        }

        // --- Aktionen
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("+ Zeit nachtragen", ACCENT) { addManual() }, weightParams())
        actions.addView(spacer(dp(8)))
        actions.addView(button("Einstellungen", GREY) { showSettings() }, weightParams())
        root.addView(actions, blockParams(top = dp(4), bottom = dp(16)))

        renderMonth(now, db)
    }

    private fun renderWarnings(now: Long, db: Db) {
        val warnings = ArrayList<View>()

        if (!GeofenceHelper.hasForeground(this)) {
            warnings.add(warn("Die App braucht Zugriff auf den Standort, um Ankommen und Gehen zu erkennen.",
                "Standort erlauben") {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQ_FG
                )
            })
        } else if (!GeofenceHelper.hasBackground(this)) {
            warnings.add(warn("Wichtig: Standort auf „Immer zulassen“ stellen – sonst wird nur erfasst, solange die App offen ist.",
                "Immer zulassen") {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BG)
            })
        }

        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            warnings.add(warn("Empfohlen: Akku-Optimierung für diese App ausschalten, damit Android sie nicht schlafen legt.",
                "Akku-Optimierung aus") {
                try {
                    @SuppressLint("BatteryLife")
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    startActivity(i)
                } catch (e: Exception) {
                    openAppSettings()
                }
            })
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notif_asked", false)
        ) {
            warnings.add(warn("Optional: Kurze Mitteilung beim Ankommen und Gehen anzeigen.", "Mitteilungen erlauben") {
                getPreferences(MODE_PRIVATE).edit().putBoolean("notif_asked", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            })
        }

        for (st in Stations.ALL) {
            if (Stations.getLatLng(this, st.id) == null) {
                warnings.add(warn("Der Standort von ${st.name} wurde noch nicht gefunden. Wenn Eva gerade dort ist: einfach tippen.",
                    "Ich bin gerade hier") { setHere(st) })
            }
        }

        geoError?.let { warnings.add(warn(it, "Nochmal versuchen") { registerGeo() }) }

        for (o in db.openVisits()) {
            if (now - o.start > 14 * 3_600_000L) {
                val name = Stations.byId(o.station)?.name ?: ""
                warnings.add(warn("Bei $name läuft seit ${dayFmt.format(Date(o.start))} ${timeFmt.format(Date(o.start))} ein Besuch. Wahrscheinlich wurde das Gehen verpasst.",
                    "Ende eintragen") { editVisit(o) })
            }
        }

        warnings.forEach { root.addView(it, blockParams(bottom = dp(10))) }
    }

    private fun renderMonth(now: Long, db: Db) {
        val from = Stats.startOfMonth(now, monthOffset)
        val to = Stats.startOfMonth(now, monthOffset + 1)

        // Kopf mit Monatswahl
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(button("‹", ACCENT) { monthOffset--; render() })
        head.addView(
            text(monthFmt.format(Date(from)), 19f, DARK, bold = true).also { it.gravity = Gravity.CENTER },
            weightParams()
        )
        val next = button("›", if (monthOffset < 0) ACCENT else LINE) { if (monthOffset < 0) { monthOffset++; render() } }
        head.addView(next)
        root.addView(head, blockParams(bottom = dp(10)))

        val visits = db.visitsOverlapping(from, to)

        // Monatssumme
        val sumCard = card()
        sumCard.addView(text("Monat gesamt", 16f, DARK, bold = true).also { it.setPadding(0, 0, 0, dp(6)) })
        for (st in Stations.ALL) {
            val s = Stats.sum(visits, st.id, from, to, now)
            val d = Stats.days(visits, st.id, from, to)
            sumCard.addView(pairRow(st.name, "${Stats.fmt(s.ms)} · $d Tage · ${s.count}×", st.color))
        }
        val total = Stations.ALL.sumOf { Stats.sum(visits, it.id, from, to, now).ms }
        sumCard.addView(pairRow("Zusammen", Stats.fmt(total), DARK))
        root.addView(sumCard)

        // Wochen
        val weekCard = card()
        weekCard.addView(text("Wochen", 16f, DARK, bold = true).also { it.setPadding(0, 0, 0, dp(6)) })
        var ws = Stats.startOfWeek(from)
        val weekVisits = db.visitsOverlapping(ws, Stats.addDays(Stats.startOfWeek(to - 1), 7))
        while (ws < to) {
            val we = Stats.addDays(ws, 7)
            val label = "KW ${Stats.weekNumber(ws)}  ${shortDate.format(Date(ws))}–${shortDate.format(Date(Stats.addDays(ws, 6)))}"
            val parts = Stations.ALL.map { st ->
                "${st.shortName} ${Stats.fmt(Stats.sum(weekVisits, st.id, ws, we, now).ms).removeSuffix(" Std")}"
            }
            weekCard.addView(pairRow(label, parts.joinToString("  ·  ") + " Std", DARK))
            ws = we
        }
        root.addView(weekCard)

        // Einzelne Besuche
        val listCard = card()
        listCard.addView(text("Alle Besuche (antippen zum Ändern)", 16f, DARK, bold = true).also { it.setPadding(0, 0, 0, dp(6)) })
        if (visits.isEmpty()) {
            listCard.addView(text("Noch keine Einträge in diesem Monat.", 14f, GREY))
        } else {
            for (v in visits.sortedByDescending { it.start }) {
                val st = Stations.byId(v.station) ?: continue
                val endText = v.end?.let { timeFmt.format(Date(it)) } ?: "läuft"
                val dur = Stats.fmt((v.end ?: now) - v.start)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(8), 0, dp(8))
                    isClickable = true
                    setOnClickListener { editVisit(v) }
                }
                row.addView(text(dayFmt.format(Date(v.start)), 14f, DARK), LinearLayout.LayoutParams(dp(82), -2))
                row.addView(text(st.shortName, 14f, st.color, bold = true), LinearLayout.LayoutParams(dp(44), -2))
                row.addView(
                    text("${timeFmt.format(Date(v.start))}–$endText" + if (v.manual) "  ✎" else "", 14f, DARK),
                    weightParams()
                )
                row.addView(text(dur, 14f, DARK, bold = true))
                listCard.addView(row)
                listCard.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, 1))
            }
        }
        root.addView(listCard)

        root.addView(button("Monat als Tabelle speichern (Excel/CSV)", ACCENT) { export(from, to) },
            blockParams(top = dp(4)))
    }

    // ---------------------------------------------------------------- Bearbeiten

    private fun editVisit(v: Visit) {
        val st = Stations.byId(v.station) ?: return
        val items = arrayOf(
            "Beginn ändern (${timeFmt.format(Date(v.start))})",
            if (v.end != null) "Ende ändern (${timeFmt.format(Date(v.end))})" else "Ende eintragen",
            "Eintrag löschen"
        )
        AlertDialog.Builder(this)
            .setTitle("${st.name} – ${dayFmt.format(Date(v.start))}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickDateTime(v.start, "Neuer Beginn") { t ->
                        if (v.end != null && t >= v.end) toast("Beginn muss vor dem Ende liegen.")
                        else { Db.get(this).update(v.id, t, v.end); render() }
                    }
                    1 -> pickDateTime(v.end ?: minOf(System.currentTimeMillis(), v.start + 8 * 3_600_000L), "Ende") { t ->
                        if (t <= v.start) toast("Ende muss nach dem Beginn liegen.")
                        else { Db.get(this).update(v.id, v.start, t); render() }
                    }
                    2 -> AlertDialog.Builder(this)
                        .setMessage("Diesen Eintrag wirklich löschen?")
                        .setPositiveButton("Löschen") { _, _ -> Db.get(this).delete(v.id); render() }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun addManual() {
        val names = Stations.ALL.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Welche Station?")
            .setItems(names) { _, which ->
                val st = Stations.ALL[which]
                val c = Stats.cal()
                c.set(Calendar.HOUR_OF_DAY, 6)
                c.set(Calendar.MINUTE, 0)
                pickDateTime(c.timeInMillis, "Beginn") { start ->
                    pickDateTime(start + 8 * 3_600_000L, "Ende") { end ->
                        if (end <= start) toast("Ende muss nach dem Beginn liegen.")
                        else {
                            Db.get(this).insert(st.id, start, end, true)
                            toast("Nachgetragen: ${st.name}, ${Stats.fmt(end - start)}")
                            render()
                        }
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun pickDateTime(initial: Long, title: String, cb: (Long) -> Unit) {
        val c = Stats.cal()
        c.timeInMillis = initial
        val dpd = DatePickerDialog(this, { _, y, m, d ->
            val tp = TimePickerDialog(this, { _, h, min ->
                c.set(y, m, d, h, min, 0)
                c.set(Calendar.MILLISECOND, 0)
                cb(c.timeInMillis)
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
            tp.setTitle("$title – Uhrzeit")
            tp.show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        dpd.setTitle("$title – Datum")
        dpd.show()
    }

    // ---------------------------------------------------------------- Einstellungen

    private fun showSettings() {
        val items = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        for (st in Stations.ALL) {
            val state = when {
                Stations.getLatLng(this, st.id) == null -> "fehlt"
                Stations.isSetOnSite(this, st.id) -> "vor Ort gesetzt"
                else -> "aus Adresse"
            }
            items.add("${st.name}: Standort = hier ($state)")
            actions.add { setHere(st) }
        }
        items.add("Umkreis ändern (jetzt ${Stations.getRadius(this, 0)} m)")
        actions.add { chooseRadius() }
        items.add("Standorte neu aus den Adressen suchen")
        actions.add { findMissingLocations(force = true) }
        items.add("App-Berechtigungen öffnen")
        actions.add { openAppSettings() }
        items.add("Alle Daten als Tabelle speichern")
        actions.add { export(0L, Long.MAX_VALUE) }

        AlertDialog.Builder(this)
            .setTitle("Einstellungen")
            .setItems(items.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun chooseRadius() {
        val options = intArrayOf(100, 150, 200, 300)
        AlertDialog.Builder(this)
            .setTitle("Umkreis um die Stationen")
            .setItems(options.map { "$it m" + if (it == 150) " (empfohlen)" else "" }.toTypedArray()) { _, which ->
                Stations.ALL.forEach { Stations.setRadius(this, it.id, options[which]) }
                registerGeo()
                toast("Umkreis: ${options[which]} m")
            }
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun setHere(st: Station) {
        if (!GeofenceHelper.hasForeground(this)) {
            toast("Bitte zuerst den Standort erlauben.")
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_FG)
            return
        }
        toast("Standort wird ermittelt …")
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    toast("Kein Standort gefunden. Ist GPS eingeschaltet?")
                } else {
                    Stations.setLatLng(this, st.id, loc.latitude, loc.longitude, manual = true)
                    toast("${st.name}: Standort gespeichert")
                    registerGeo()
                }
            }
            .addOnFailureListener { toast("Standort konnte nicht ermittelt werden.") }
    }

    private fun findMissingLocations(force: Boolean) {
        val todo = Stations.ALL.filter { force || Stations.getLatLng(this, it.id) == null }
        if (todo.isEmpty()) return
        if (!Geocoder.isPresent()) {
            if (force) toast("Adresssuche ist auf diesem Handy nicht verfügbar – bitte vor Ort „hier“ setzen.")
            return
        }
        Thread {
            var found = 0
            val g = Geocoder(this, Locale.GERMANY)
            for (st in todo) {
                try {
                    @Suppress("DEPRECATION")
                    val a = g.getFromLocationName(st.address, 1)?.firstOrNull()
                    if (a != null) {
                        Stations.setLatLng(this, st.id, a.latitude, a.longitude, manual = false)
                        found++
                    }
                } catch (e: Exception) {
                }
            }
            runOnUiThread {
                if (force) toast("$found von ${todo.size} Standorten gefunden")
                registerGeo()
            }
        }.start()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    // ---------------------------------------------------------------- Export

    private fun export(from: Long, to: Long) {
        exportFrom = from
        exportTo = to
        val name = if (from == 0L) "Eva_Arbeitszeit_alles.csv"
        else "Eva_Arbeitszeit_${SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(Date(from))}.csv"
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_TITLE, name)
        try {
            startActivityForResult(i, REQ_EXPORT)
        } catch (e: Exception) {
            toast("Speichern ist auf diesem Handy nicht möglich.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(buildCsv(exportFrom, exportTo).toByteArray(Charsets.UTF_8)) }
            toast("Tabelle gespeichert")
        } catch (e: Exception) {
            toast("Fehler beim Speichern: ${e.message}")
        }
    }

    private fun buildCsv(from: Long, to: Long): String {
        val now = System.currentTimeMillis()
        val visits = if (from == 0L) Db.get(this).allVisits() else Db.get(this).visitsOverlapping(from, to)
        val sb = StringBuilder("﻿")
        sb.append("Datum;Wochentag;Station;Beginn;Ende;Dauer (Stunden);Dauer;Nachgetragen\r\n")
        val weekday = SimpleDateFormat("EEEE", Locale.GERMANY)
        for (v in visits) {
            val st = Stations.byId(v.station) ?: continue
            val end = v.end ?: now
            sb.append(dateFmt.format(Date(v.start))).append(';')
                .append(weekday.format(Date(v.start))).append(';')
                .append(st.name).append(';')
                .append(timeFmt.format(Date(v.start))).append(';')
                .append(if (v.end != null) timeFmt.format(Date(end)) else "läuft").append(';')
                .append(Stats.hoursDecimal(end - v.start)).append(';')
                .append(Stats.fmt(end - v.start).removeSuffix(" Std")).append(';')
                .append(if (v.manual) "ja" else "").append("\r\n")
        }
        sb.append("\r\n")
        val f = if (from == 0L) 0L else from
        val t = if (from == 0L) Long.MAX_VALUE else to
        for (st in Stations.ALL) {
            val s = Stats.sum(visits, st.id, f, t, now)
            val d = Stats.days(visits, st.id, f, t)
            sb.append("Summe;;").append(st.name).append(";;;")
                .append(Stats.hoursDecimal(s.ms)).append(';')
                .append(Stats.fmt(s.ms).removeSuffix(" Std")).append(';')
                .append("$d Tage, ${s.count} Besuche").append("\r\n")
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- Bausteine

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun rounded(color: Int, radius: Int = dp(14)) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(1).toFloat()
        }
        c.layoutParams = blockParams(bottom = dp(12))
        return c
    }

    private fun statRow(label: String, s: Stats.Sum): View =
        pairRow(label, "${Stats.fmt(s.ms)}  ·  ${s.count}×", DARK)

    private fun pairRow(label: String, value: String, valueColor: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(text(label, 15f, GREY), weightParams())
        row.addView(text(value, 15f, valueColor, bold = true).also { it.gravity = Gravity.END })
        return row
    }

    private fun button(label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(color, dp(12))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun warn(message: String, action: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(WARN_BG)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        box.addView(text(message, 14f, WARN))
        box.addView(button(action, WARN, onClick), blockParams(top = dp(8)))
        return box
    }

    private fun spacer(w: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(w, 1) }

    private fun weightParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun blockParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
            bottomMargin = bottom
        }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
