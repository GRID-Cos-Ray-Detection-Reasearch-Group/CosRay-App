package com.grid.cosrayapp.data.telemetry.upload

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grid.cosrayapp.core.network.model.PacketUploadRequest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class UploadQueueStats(
  val size: Int,
  val dropped: Long,
)

data class UploadQueueItem(
  val id: Long,
  val request: PacketUploadRequest,
)

interface UploadQueue {
  suspend fun enqueue(requests: List<PacketUploadRequest>)

  suspend fun peekBatch(limit: Int): List<UploadQueueItem>

  suspend fun delete(ids: List<Long>)

  suspend fun size(): Int

  suspend fun dropCount(): Long

  suspend fun stats(): UploadQueueStats = UploadQueueStats(size = size(), dropped = dropCount())

  suspend fun clear()
}

private const val UPLOAD_QUEUE_PREFERENCES_NAME = "cosray_upload_queue"
private val Context.uploadQueueStore: DataStore<Preferences> by
  preferencesDataStore(name = UPLOAD_QUEUE_PREFERENCES_NAME)

class DataStoreUploadQueue(
  private val context: Context,
  private val json: Json,
  private val maxSize: Int,
) : UploadQueue {
  private val store: DataStore<Preferences> = context.uploadQueueStore

  override suspend fun enqueue(requests: List<PacketUploadRequest>) {
    if (requests.isEmpty()) return
    store.edit { prefs ->
      val nextId = prefs[Keys.NEXT_ID] ?: 1L
      var id = nextId
      requests.forEach { request ->
        prefs[Keys.itemKey(id)] = json.encodeToString(request)
        id++
      }
      prefs[Keys.NEXT_ID] = id

      val ids = idsAscending(prefs)
      val overflow = (ids.size - maxSize).coerceAtLeast(0)
      if (overflow > 0) {
        val toDrop = ids.take(overflow)
        toDrop.forEach { dropId -> prefs.remove(Keys.itemKey(dropId)) }
        val currentDrop = prefs[Keys.DROP_COUNT] ?: 0L
        prefs[Keys.DROP_COUNT] = currentDrop + overflow

      }
    }
  }

  override suspend fun peekBatch(limit: Int): List<UploadQueueItem> {
    if (limit <= 0) return emptyList()
    val prefs = store.data.first()
    val ids = idsAscending(prefs)
    if (ids.isEmpty()) return emptyList()

    val corruptIds = mutableListOf<Long>()
    val items = mutableListOf<UploadQueueItem>()
    ids.forEach { id ->
      if (items.size >= limit) return@forEach
      val key = Keys.itemKey(id)
      val encoded = prefs[key] ?: return@forEach
      val request =
        runCatching { json.decodeFromString(PacketUploadRequest.serializer(), encoded) }.getOrNull()
      if (request == null) {
        corruptIds += id
      } else {
        items += UploadQueueItem(id = id, request = request)
      }
    }

    if (corruptIds.isNotEmpty()) {
      store.edit { editPrefs ->
        corruptIds.forEach { id -> editPrefs.remove(Keys.itemKey(id)) }
        val currentDrop = editPrefs[Keys.DROP_COUNT] ?: 0L
        editPrefs[Keys.DROP_COUNT] = currentDrop + corruptIds.size
      }
    }

    return items
  }

  override suspend fun delete(ids: List<Long>) {
    if (ids.isEmpty()) return
    store.edit { prefs ->
      ids.distinct().forEach { id -> prefs.remove(Keys.itemKey(id)) }
    }
  }

  override suspend fun size(): Int {
    val prefs = store.data.first()
    return idsAscending(prefs).size
  }

  override suspend fun dropCount(): Long {
    val prefs = store.data.first()
    return prefs[Keys.DROP_COUNT] ?: 0L
  }

  override suspend fun clear() {
    store.edit { prefs ->
      idsAscending(prefs).forEach { id -> prefs.remove(Keys.itemKey(id)) }
    }
  }

  private fun idsAscending(prefs: Preferences): List<Long> {
    return prefs.asMap().keys
      .mapNotNull { key ->
        val name = key.name
        if (!name.startsWith(Keys.ITEM_PREFIX)) return@mapNotNull null
        name.removePrefix(Keys.ITEM_PREFIX).toLongOrNull()
      }
      .sorted()
  }

  private object Keys {
    const val ITEM_PREFIX = "item_"

    val NEXT_ID = longPreferencesKey("next_id")
    val DROP_COUNT = longPreferencesKey("drop_count")

    fun itemKey(id: Long) = stringPreferencesKey("$ITEM_PREFIX$id")
  }
}

class InMemoryUploadQueue(private val json: Json, private val maxSize: Int) : UploadQueue {
  private val items = linkedMapOf<Long, String>()
  private var nextId: Long = 1L
  private var dropped: Long = 0L

  override suspend fun enqueue(requests: List<PacketUploadRequest>) {
    if (requests.isEmpty()) return
    requests.forEach { request ->
      items[nextId] = json.encodeToString(request)
      nextId++
    }
    val overflow = (items.size - maxSize).coerceAtLeast(0)
    if (overflow > 0) {
      val keysToDrop = items.keys.take(overflow)
      keysToDrop.forEach { items.remove(it) }
      dropped += overflow
    }
  }

  override suspend fun peekBatch(limit: Int): List<UploadQueueItem> {
    if (limit <= 0 || items.isEmpty()) return emptyList()

    val corruptIds = mutableListOf<Long>()
    val result = mutableListOf<UploadQueueItem>()
    items.forEach { (id, encoded) ->
      if (result.size >= limit) return@forEach
      val request =
        runCatching { json.decodeFromString(PacketUploadRequest.serializer(), encoded) }.getOrNull()
      if (request == null) {
        corruptIds += id
      } else {
        result += UploadQueueItem(id = id, request = request)
      }
    }

    if (corruptIds.isNotEmpty()) {
      corruptIds.forEach { id -> items.remove(id) }
      dropped += corruptIds.size
    }

    return result
  }

  override suspend fun delete(ids: List<Long>) {
    ids.distinct().forEach { items.remove(it) }
  }

  override suspend fun size(): Int = items.size

  override suspend fun dropCount(): Long = dropped

  override suspend fun clear() {
    items.clear()
  }
}
