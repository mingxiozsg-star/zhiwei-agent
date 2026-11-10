package com.zhiwei.agent.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 知识库：用户手动喂进来的资料（笔记、网页正文、说明文档），
 * 检索时按段落切分后打分，只把最相关的几段喂给模型。
 */
data class KnowledgeItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val source: String = "手动添加",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("source", source)
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): KnowledgeItem {
            val tags = mutableListOf<String>()
            o.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) tags.add(arr.optString(i, ""))
            }
            return KnowledgeItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", ""),
                content = o.optString("content", ""),
                source = o.optString("source", "手动添加"),
                tags = tags.filter { it.isNotBlank() },
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

/** 检索命中的知识片段 */
data class KnowledgeHit(
    val title: String,
    val text: String,
    val score: Double
)

class KnowledgeStore(context: Context) {

    private val file = File(context.filesDir, "knowledge.json")

    fun load(): List<KnowledgeItem> = synchronized(this) {
        if (!file.exists()) return@synchronized emptyList()
        try {
            val arr = JSONArray(file.readText())
            val out = mutableListOf<KnowledgeItem>()
            for (i in 0 until arr.length()) out.add(KnowledgeItem.fromJson(arr.getJSONObject(i)))
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<KnowledgeItem>) {
        synchronized(this) {
            try {
                val arr = JSONArray()
                list.forEach { arr.put(it.toJson()) }
                val tmp = File(file.parentFile, "knowledge.json.tmp")
                tmp.writeText(arr.toString())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }

    /** 段落切分：按空行切，超长段落再按句号/换行硬切 */
    fun chunks(item: KnowledgeItem): List<String> {
        val raw = item.content.trim()
        if (raw.isEmpty()) return emptyList()
        val parts = raw.split(Regex("\\n\\s*\\n"))
        val out = mutableListOf<String>()
        parts.forEach { p ->
            val t = p.trim()
            if (t.isEmpty()) return@forEach
            if (t.length <= 700) {
                out.add(t)
            } else {
                var i = 0
                while (i < t.length) {
                    out.add(t.substring(i, minOf(i + 700, t.length)))
                    i += 700
                }
            }
        }
        return out
    }

    fun search(list: List<KnowledgeItem>, query: String, limit: Int): List<KnowledgeHit> {
        if (list.isEmpty() || limit <= 0) return emptyList()
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hits = mutableListOf<KnowledgeHit>()
        list.forEach { item ->
            chunks(item).forEach { c ->
                val titleBoost = if (item.title.isNotBlank()) TextRank.score(q, item.title) * 0.6 else 0.0
                val s = TextRank.score(q, c) + titleBoost
                if (s > 0.03) hits.add(KnowledgeHit(item.title.ifBlank { "未命名知识" }, c, s))
            }
        }
        return hits.sortedByDescending { it.score }.take(limit)
    }
}