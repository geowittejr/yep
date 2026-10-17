package com.georgewitte.yep.data

import android.content.Context
import com.georgewitte.yep.logic.Check
import com.georgewitte.yep.logic.Checks
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Saves checks on the device only, as a small JSON document in SharedPreferences.
 * Android's Auto Backup includes it, so data follows the user to a new phone at no cost.
 */
class CheckStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("yep", Context.MODE_PRIVATE)

    fun load(): List<Check> {
        val raw = prefs.getString(KEY, null) ?: return Checks.defaults().also(::save)
        return try {
            decode(raw)
        } catch (e: JSONException) {
            Checks.defaults()
        }
    }

    fun save(checks: List<Check>) {
        prefs.edit().putString(KEY, encode(checks)).apply()
    }

    private fun encode(checks: List<Check>): String {
        val array = JSONArray()
        for (check in checks) {
            val history = JSONArray()
            check.history.forEach { history.put(it) }
            array.put(
                JSONObject()
                    .put("id", check.id)
                    .put("name", check.name)
                    .put("emoji", check.emoji)
                    .put("history", history),
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<Check> {
        val array = JSONArray(raw)
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val history = obj.optJSONArray("history") ?: JSONArray()
            Check(
                id = obj.getString("id"),
                name = obj.getString("name"),
                emoji = obj.optString("emoji", "✅"),
                history = List(history.length()) { h -> history.getLong(h) },
            )
        }
    }

    private companion object {
        const val KEY = "checks_v1"
    }
}
