package com.zhiwei.agent.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** 单个模型的用量 */
data class ModelUsage(
    val requests: Int = 0,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L
) {
    val totalTokens: Long get() = promptTokens + completionTokens

    fun plus(prompt: Int, completion: Int): ModelUsage = copy(
        requests = requests + 1,
        promptTokens = promptTokens + prompt,
        completionTokens = completionTokens + completion
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("requests", requests)
        put("prompt", promptTokens)
        put("completion", completionTokens)
    }

    companion object {
        fun fromJson(o: JSONObject) = ModelUsage(
            requests = o.optInt("requests", 0),
            promptTokens = o.optLong("prompt", 0L),
            completionTokens = o.optLong("completion", 0L)
        )
    }
}

/** 本机累计的用量快照 */
data class UsageSnapshot(
    val models: Map<String, ModelUsage> = emptyMap(),
    val lastAt: Long = 0L,
    val lastModel: String = ""
) {
    val total: ModelUsage
        get() = models.values.fold(ModelUsage()) { acc, u ->
            ModelUsage(
                requests = acc.requests + u.requests,
                promptTokens = acc.promptTokens + u.promptTokens,
                completionTokens = acc.completionTokens + u.completionTokens
            )
        }

    val isEmpty: Boolean get() = models.isEmpty()
}

/**
 * 用量统计：filesDir/stats.json
 * 只在服务端返回 usage 时记录，全部留在本机。
 */
class StatsStore(context: Context) {

    private val file = File(context.filesDir, "stats.json")

    fun load(): UsageSnapshot = synchronized(this) {
        if (!file.exists()) return@synchronized UsageSnapshot()
        try {
            val root = JSONObject(file.readText())
            val map = LinkedHashMap<String, ModelUsage>()
            val models = root.optJSONObject("models")
            if (models != null) {
                models.keys().forEach { k ->
                    models.optJSONObject(k)?.let { map[k] = ModelUsage.fromJson(it) }
                }
            }
            UsageSnapshot(
                models = map,
                lastAt = root.optLong("lastAt", 0L),
                lastModel = root.optString("lastModel", "")
            )
        } catch (e: Exception) {
            UsageSnapshot()
        }
    }

    fun save(s: UsageSnapshot) {
        synchronized(this) {
            try {
                val models = JSONObject()
                s.models.forEach { (k, v) -> models.put(k, v.toJson()) }
                val root = JSONObject()
                    .put("models", models)
                    .put("lastAt", s.lastAt)
                    .put("lastModel", s.lastModel)
                val tmp = File(file.parentFile, "stats.json.tmp")
                tmp.writeText(root.toString())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }

    /** 记一笔用量，返回新快照 */
    fun add(current: UsageSnapshot, model: String, prompt: Int, completion: Int): UsageSnapshot {
        if (prompt <= 0 && completion <= 0) return current
        val key = model.ifBlank { "未知模型" }
        val old = current.models[key] ?: ModelUsage()
        val next = current.copy(
            models = current.models + (key to old.plus(prompt, completion)),
            lastAt = System.currentTimeMillis(),
            lastModel = key
        )
        save(next)
        return next
    }

    fun reset(): UsageSnapshot {
        val empty = UsageSnapshot()
        save(empty)
        return empty
    }
}