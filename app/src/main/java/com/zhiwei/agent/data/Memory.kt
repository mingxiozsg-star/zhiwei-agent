package com.zhiwei.agent.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 记忆：从对话里自动沉淀的长期事实，跨对话可用。
 * 存本地 filesDir/memories.json，纯文本，不联网。
 */
data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val tags: List<String> = emptyList(),
    val source: String = "对话",
    val conversationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
        put("source", source)
        if (conversationId != null) put("conversationId", conversationId)
        put("createdAt", createdAt)
        put("pinned", pinned)
    }

    companion object {
        fun fromJson(o: JSONObject): MemoryItem {
            val tags = mutableListOf<String>()
            o.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) tags.add(arr.optString(i, ""))
            }
            return MemoryItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                text = o.optString("text", ""),
                tags = tags.filter { it.isNotBlank() },
                source = o.optString("source", "对话"),
                conversationId = if (o.isNull("conversationId")) null else o.optString("conversationId", "").ifEmpty { null },
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                pinned = o.optBoolean("pinned", false)
            )
        }
    }
}

class MemoryStore(context: Context) {

    private val file = File(context.filesDir, "memories.json")

    fun load(): List<MemoryItem> = synchronized(this) {
        if (!file.exists()) return@synchronized emptyList()
        try {
            val arr = JSONArray(file.readText())
            val out = mutableListOf<MemoryItem>()
            for (i in 0 until arr.length()) out.add(MemoryItem.fromJson(arr.getJSONObject(i)))
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<MemoryItem>) {
        synchronized(this) {
            try {
                val arr = JSONArray()
                list.forEach { arr.put(it.toJson()) }
                val tmp = File(file.parentFile, "memories.json.tmp")
                tmp.writeText(arr.toString())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }

    /** 关键词/字重叠打分检索，取前 limit 条 */
    fun search(list: List<MemoryItem>, query: String, limit: Int): List<MemoryItem> {
        if (list.isEmpty()) return emptyList()
        val q = query.trim()
        if (q.isEmpty()) return list.sortedByDescending { it.pinned }.take(limit.coerceAtLeast(0))
        return list
            .map { it to TextRank.score(q, it.text) + (if (it.pinned) 0.15 else 0.0) }
            .filter { it.second > 0.02 }
            .sortedByDescending { it.second }
            .take(limit.coerceAtLeast(0))
            .map { it.first }
    }

    fun upsert(list: List<MemoryItem>, item: MemoryItem): List<MemoryItem> {
        val t = item.text.trim()
        if (t.isEmpty()) return list
        val dup = list.firstOrNull { TextRank.score(t, it.text) > 0.85 || it.text.trim() == t }
        if (dup != null) return list
        return listOf(item.copy(text = t)) + list
    }
}

/**
 * 极简检索打分：中文按二元字组重叠，英文按单词重叠。
 * 没有向量模型也能用，纯本地、零依赖。
 */
object TextRank {

    fun tokens(text: String): Set<String> {
        val out = HashSet<String>()
        val lower = text.lowercase()
        Regex("[a-z0-9_]{2,}").findAll(lower).forEach { out.add(it.value) }
        val cjk = lower.filter { it.code in 0x4E00..0x9FFF }
        if (cjk.length == 1) {
            out.add(cjk)
        } else if (cjk.length > 1) {
            for (i in 0 until cjk.length - 1) out.add(cjk.substring(i, i + 2))
        }
        return out
    }

    fun score(query: String, text: String): Double {
        if (query.isBlank() || text.isBlank()) return 0.0
        val q = tokens(query)
        val t = tokens(text)
        if (q.isEmpty() || t.isEmpty()) return 0.0
        var hit = 0
        q.forEach { if (t.contains(it)) hit++ }
        if (hit == 0) return 0.0
        // 命中越集中分越高
        return hit.toDouble() / kotlin.math.sqrt(t.size.toDouble())
    }
}
