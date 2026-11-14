package space.manus.tgvidapp.t145521605804137

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

object DownloadStatus {
    const val QUEUED = "queued"
    const val RESOLVING = "resolving"
    const val DOWNLOADING = "downloading"
    const val RETRYING = "retrying"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELED = "canceled"

    fun isActive(status: String): Boolean = status in setOf(RESOLVING, DOWNLOADING, RETRYING)
}

data class DownloadItem(
    val id: String,
    val link: String,
    val status: String = DownloadStatus.QUEUED,
    val phase: String = "စောင့်နေပါတယ်",
    val progress: Int = -1,
    val copiedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Long = 0L,
    val attempts: Int = 0,
    val error: String = "",
    val resultUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val shortLabel: String
        get() = link.trimEnd('/').substringAfterLast('/').ifBlank { link }.take(72)
}

object DownloadQueueStore {
    private const val DB_NAME = "telegram_download_queue.db"
    private const val DB_VERSION = 2
    private const val TABLE = "downloads"
    private val lock = Any()
    @Volatile private var dbHelper: QueueDb? = null

    private class QueueDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $TABLE (
                    id TEXT PRIMARY KEY NOT NULL,
                    link TEXT NOT NULL,
                    status TEXT NOT NULL,
                    phase TEXT NOT NULL,
                    progress INTEGER NOT NULL DEFAULT -1,
                    copied_bytes INTEGER NOT NULL DEFAULT 0,
                    total_bytes INTEGER,
                    speed_bytes_per_second INTEGER NOT NULL DEFAULT 0,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    error TEXT NOT NULL DEFAULT '',
                    result_uri TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX downloads_status_created ON $TABLE(status, created_at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN speed_bytes_per_second INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    @Synchronized
    fun enqueue(context: Context, links: List<String>): List<DownloadItem> = synchronized(lock) {
        val db = database(context).writableDatabase
        val existingActiveLinks = db.query(
            TABLE,
            arrayOf("link"),
            "status IN (?, ?, ?, ?)",
            arrayOf(DownloadStatus.QUEUED, DownloadStatus.RESOLVING, DownloadStatus.DOWNLOADING, DownloadStatus.RETRYING),
            null,
            null,
            null,
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("link")).trim())
            }
        }
        val created = links
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { it in existingActiveLinks }
            .map { DownloadItem(id = UUID.randomUUID().toString(), link = it) }
        if (created.isEmpty()) return@synchronized emptyList()
        db.beginTransaction()
        try {
            created.forEach { db.insertOrThrow(TABLE, null, it.toContentValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        created
    }

    @Synchronized
    fun list(context: Context): List<DownloadItem> = synchronized(lock) {
        database(context).readableDatabase.rawQuery(
            "SELECT * FROM $TABLE ORDER BY created_at DESC",
            null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toDownloadItem()) } }
    }

    @Synchronized
    fun queued(context: Context): List<DownloadItem> = synchronized(lock) {
        database(context).readableDatabase.rawQuery(
            "SELECT * FROM $TABLE WHERE status=? ORDER BY created_at ASC",
            arrayOf(DownloadStatus.QUEUED),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toDownloadItem()) } }
    }

    @Synchronized
    fun get(context: Context, id: String): DownloadItem? = synchronized(lock) {
        database(context).readableDatabase.query(TABLE, null, "id=?", arrayOf(id), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.toDownloadItem() else null
        }
    }

    @Synchronized
    fun update(context: Context, id: String, transform: (DownloadItem) -> DownloadItem): DownloadItem? = synchronized(lock) {
        val db = database(context).writableDatabase
        db.beginTransaction()
        try {
            val old = db.query(TABLE, null, "id=?", arrayOf(id), null, null, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.toDownloadItem() else null
            } ?: return@synchronized null
            val updated = transform(old).copy(updatedAt = System.currentTimeMillis())
            db.update(TABLE, updated.toContentValues(), "id=?", arrayOf(id))
            db.setTransactionSuccessful()
            updated
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun retry(context: Context, id: String): Boolean = synchronized(lock) {
        val item = get(context, id) ?: return@synchronized false
        if (item.status != DownloadStatus.FAILED) return@synchronized false
        update(context, id) {
            it.copy(
                status = DownloadStatus.QUEUED,
                phase = "ပြန်လည်စီစဉ်ထားပါတယ်",
                progress = -1,
                copiedBytes = 0L,
                totalBytes = null,
                speedBytesPerSecond = 0L,
                attempts = 0,
                error = "",
                resultUri = "",
            )
        } != null
    }

    @Synchronized
    fun remove(context: Context, id: String): Boolean = synchronized(lock) {
        database(context).writableDatabase.delete(TABLE, "id=?", arrayOf(id)) > 0
    }

    @Synchronized
    fun clearCompleted(context: Context): Int = synchronized(lock) {
        database(context).writableDatabase.delete(
            TABLE,
            "status IN (?, ?)",
            arrayOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELED),
        )
    }

    @Synchronized
    fun recoverInterrupted(context: Context) = synchronized(lock) {
        val db = database(context).writableDatabase
        val active = arrayOf(DownloadStatus.RESOLVING, DownloadStatus.DOWNLOADING, DownloadStatus.RETRYING)
        val items = db.query(TABLE, null, "status IN (?, ?, ?)", active, null, null, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toDownloadItem()) }
        }
        db.beginTransaction()
        try {
            items.forEach { item ->
                val recovered = item.copy(
                    status = DownloadStatus.QUEUED,
                    phase = "App ပြန်စတင်ပြီးနောက် ဆက်လက်စီစဉ်ထားပါတယ်",
                    progress = -1,
                    copiedBytes = 0L,
                    totalBytes = null,
                    speedBytesPerSecond = 0L,
                    updatedAt = System.currentTimeMillis(),
                )
                db.update(TABLE, recovered.toContentValues(), "id=?", arrayOf(item.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun hasPending(context: Context): Boolean = synchronized(lock) {
        database(context).readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE WHERE status IN (?, ?, ?, ?) LIMIT 1",
            arrayOf(DownloadStatus.QUEUED, DownloadStatus.RESOLVING, DownloadStatus.DOWNLOADING, DownloadStatus.RETRYING),
        ).use { it.moveToFirst() }
    }

    private fun database(context: Context): QueueDb = dbHelper ?: synchronized(lock) {
        dbHelper ?: QueueDb(context.applicationContext).also { dbHelper = it }
    }

    private fun DownloadItem.toContentValues() = ContentValues().apply {
        put("id", id)
        put("link", link)
        put("status", status)
        put("phase", phase)
        put("progress", progress)
        put("copied_bytes", copiedBytes)
        if (totalBytes == null) putNull("total_bytes") else put("total_bytes", totalBytes)
        put("speed_bytes_per_second", speedBytesPerSecond)
        put("attempts", attempts)
        put("error", error)
        put("result_uri", resultUri)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun Cursor.toDownloadItem() = DownloadItem(
        id = getString(getColumnIndexOrThrow("id")),
        link = getString(getColumnIndexOrThrow("link")),
        status = getString(getColumnIndexOrThrow("status")),
        phase = getString(getColumnIndexOrThrow("phase")),
        progress = getInt(getColumnIndexOrThrow("progress")),
        copiedBytes = getLong(getColumnIndexOrThrow("copied_bytes")),
        totalBytes = if (isNull(getColumnIndexOrThrow("total_bytes"))) null else getLong(getColumnIndexOrThrow("total_bytes")),
        speedBytesPerSecond = getLong(getColumnIndexOrThrow("speed_bytes_per_second")),
        attempts = getInt(getColumnIndexOrThrow("attempts")),
        error = getString(getColumnIndexOrThrow("error")),
        resultUri = getString(getColumnIndexOrThrow("result_uri")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
    )
}
