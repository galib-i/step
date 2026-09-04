package com.galib.step.data.backup

import android.content.Context
import android.net.Uri
import com.galib.step.Graph
import com.galib.step.data.db.DailySummaryEntity
import com.galib.step.model.ColorStyle
import com.galib.step.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full-app backup as a single JSON document: settings and daily history.
 * Written/read through SAF so it survives device switches and factory resets.
 */
object BackupManager {

    suspend fun export(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = Graph.prefs.snapshot()
            val days = Graph.database.summaryDao().getAll()

            val root = JSONObject().apply {
                put("app", "step")
                put("version", 1)
                put("exportedAtEpochMs", System.currentTimeMillis())
                put("prefs", JSONObject().apply {
                    put("dailyGoal", prefs.dailyGoal)
                    put("weeklyGoal", prefs.weeklyGoal)
                    put("themeMode", prefs.themeMode.name)
                    put("dynamicColor", prefs.dynamicColor)
                    put("paletteId", prefs.paletteId)
                    put("amoled", prefs.amoled)
                    put("colorStyle", prefs.colorStyle.name)
                })
                put("days", JSONArray().apply {
                    days.forEach { d ->
                        put(JSONObject().apply {
                            put("epochDay", d.epochDay)
                            put("steps", d.steps)
                            put("goal", d.goal)
                            put("source", d.source)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open output")
        }
    }

    /** Returns the number of day-rows restored. */
    suspend fun import(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("Cannot open input")
            val root = JSONObject(text)
            require(root.optString("app") == "step") { "Not a Step backup" }

            val p = root.optJSONObject("prefs")
            if (p != null) {
                val prefs = Graph.prefs
                prefs.setDailyGoal(p.optInt("dailyGoal", 8000))
                prefs.setWeeklyGoal(p.optInt("weeklyGoal", 56000))
                runCatching { prefs.setThemeMode(ThemeMode.valueOf(p.optString("themeMode", "SYSTEM"))) }
                prefs.setDynamicColor(p.optBoolean("dynamicColor", false))
                prefs.setPaletteId(p.optString("paletteId", "tide"))
                prefs.setAmoled(p.optBoolean("amoled", false))
                runCatching { prefs.setColorStyle(ColorStyle.valueOf(p.optString("colorStyle", "TONAL_SPOT"))) }
            }

            val days = root.optJSONArray("days") ?: JSONArray()
            val entities = buildList {
                for (i in 0 until days.length()) {
                    val d = days.getJSONObject(i)
                    add(
                        DailySummaryEntity(
                            epochDay = d.getLong("epochDay"),
                            steps = d.getLong("steps"),
                            goal = d.optInt("goal", 8000),
                            source = d.optString("source", "SENSOR")
                        )
                    )
                }
            }
            // Merge: never lose local data that's ahead of the backup
            val dao = Graph.database.summaryDao()
            val existing = dao.getAll().associateBy { it.epochDay }
            dao.upsertAll(entities.filter { e ->
                (existing[e.epochDay]?.steps ?: -1L) < e.steps
            })

            entities.size
        }
    }
}
