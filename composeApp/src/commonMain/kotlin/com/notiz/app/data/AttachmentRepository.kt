package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Photos on notices and complaints, in the private "attachments" bucket (see the photos migration). */
class AttachmentRepository(private val supabase: SupabaseClient) {

    enum class Kind(val folder: String) { NOTICE("notices"), COMPLAINT("complaints") }

    private val bucket get() = supabase.storage.from(BUCKET)

    /** Recently viewed photos, so scrolling back and forth doesn't download them again. */
    private val cache = LinkedHashMap<String, ByteArray>()
    private val cacheLock = Mutex()

    /** Uploads a JPEG and returns its storage path, to save in the notice / complaint. */
    suspend fun upload(societyId: String, kind: Kind, jpeg: ByteArray): String {
        val path = "$societyId/${kind.folder}/${randomId()}.jpg"
        bucket.upload(path, jpeg) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        cacheLock.withLock { remember(path, jpeg) }
        return path
    }

    suspend fun download(path: String): ByteArray {
        cacheLock.withLock { cache[path]?.let { return it } }
        val bytes = bucket.downloadAuthenticated(path)
        cacheLock.withLock { remember(path, bytes) }
        return bytes
    }

    /** Best-effort clean-up (photos removed before posting, or a deleted notice). */
    suspend fun delete(paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching { bucket.delete(paths) }
        cacheLock.withLock { paths.forEach { cache.remove(it) } }
    }

    private fun remember(path: String, bytes: ByteArray) {
        cache.remove(path)
        cache[path] = bytes
        var total = cache.values.sumOf { it.size }
        val iterator = cache.entries.iterator()
        while (total > MAX_CACHE_BYTES && iterator.hasNext()) {
            total -= iterator.next().value.size
            iterator.remove()
        }
    }

    /** A random version-4 UUID string (the storage policies expect this shape). */
    private fun randomId(): String {
        val hex = CharArray(32) { "0123456789abcdef"[Random.nextInt(16)] }
        hex[12] = '4'
        hex[16] = "89ab"[Random.nextInt(4)]
        val s = hex.concatToString()
        return "${s.substring(0, 8)}-${s.substring(8, 12)}-${s.substring(12, 16)}-${s.substring(16, 20)}-${s.substring(20)}"
    }

    companion object {
        const val BUCKET = "attachments"
        const val MAX_PHOTOS = 3
        private const val MAX_CACHE_BYTES = 20 * 1024 * 1024
    }
}
