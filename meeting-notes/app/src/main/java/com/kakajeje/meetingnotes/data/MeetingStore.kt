package com.kakajeje.meetingnotes.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 회의는 앱 내부 저장소(폰 안)에 회의마다 JSON 파일 하나로 저장한다. 인터넷으로 나가지 않는다. */
class MeetingStore(context: Context) {
    private val dir = File(context.filesDir, "meetings").apply { mkdirs() }

    fun all(): List<Meeting> = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
        .mapNotNull { f -> runCatching { Meeting.fromJson(JSONObject(f.readText())) }.getOrNull() }
        .sortedByDescending { it.createdAt }

    fun get(id: String): Meeting? = File(dir, "$id.json").takeIf { it.exists() }
        ?.let { runCatching { Meeting.fromJson(JSONObject(it.readText())) }.getOrNull() }

    fun save(m: Meeting) {
        val tmp = File(dir, "${m.id}.json.tmp")
        tmp.writeText(m.toJson().toString())
        tmp.renameTo(File(dir, "${m.id}.json"))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}

data class Recipient(val name: String, val email: String, val byDefault: Boolean)

/** 자주 보내는 받는 사람 목록. */
class RecipientStore(context: Context) {
    private val prefs = context.getSharedPreferences("recipients", Context.MODE_PRIVATE)

    fun all(): List<Recipient> {
        val arr = runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Recipient(o.optString("name"), o.optString("email"), o.optBoolean("default"))
        }
    }

    fun saveAll(list: List<Recipient>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("email", it.email).put("default", it.byDefault))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    var attachFile: Boolean
        get() = prefs.getBoolean("attach", true)
        set(v) = prefs.edit().putBoolean("attach", v).apply()
}
