package com.zenpulse.wear.data

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * One sampled row of session data — heart rate, HRV proxy, and accelerometer merged at a fixed
 * cadence (see the view model's sampling loop). `null` fields mean "not available yet this row"
 * and are written as empty CSV cells, not zero.
 */
data class SessionRow(
    val elapsedMs: Long,
    val bpm: Double?,
    val availability: String,
    val hrvProxyMs: Double?,
    val accelX: Float?,
    val accelY: Float?,
    val accelZ: Float?,
)

/**
 * Writes [SessionRow]s to a CSV file under app-internal storage, for offline analysis later
 * (Week 3+: pulled off the watch — e.g. `adb pull` — and explored with NeuroKit2, or shipped to
 * the phone companion app once the Wearable Data Layer exists).
 *
 * The app declares no `INTERNET` permission, so this file never leaves the device on its own —
 * pulling it off is a deliberate, explicit step.
 *
 * Writes happen on a dedicated coroutine draining an unbounded [Channel], so [log] never blocks
 * the caller (normally the main thread) on disk I/O.
 */
class SessionLogger(private val context: Context) {

    companion object {
        private const val CSV_HEADER = "elapsed_ms,bpm,availability,hrv_proxy_ms,accel_x,accel_y,accel_z\n"
    }

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private var channel: Channel<SessionRow>? = null
    private var writerJob: Job? = null

    /** The file the current session is being written to, or null if no session is active. */
    var sessionFile: File? = null
        private set

    /** Opens a new timestamped CSV file and starts the background writer. Call once per session. */
    fun start(scope: CoroutineScope) {
        stop() // defensive: never leak a previous session's channel/writer if start() is re-called

        val dir = File(context.filesDir, "sessions").apply { mkdirs() }
        val file = File(dir, "session_${fileNameFormat.format(Date())}.csv")
        val ch = Channel<SessionRow>(Channel.UNLIMITED)

        writerJob = scope.launch(Dispatchers.IO) {
            BufferedWriter(FileWriter(file)).use { writer: BufferedWriter ->
                writer.write(CSV_HEADER)
                for (row in ch) {
                    writer.write(
                        "${row.elapsedMs},${row.bpm ?: ""},${row.availability}," +
                            "${row.hrvProxyMs ?: ""},${row.accelX ?: ""},${row.accelY ?: ""},${row.accelZ ?: ""}\n"
                    )
                    writer.flush()
                }
            }
        }

        channel = ch
        sessionFile = file
    }

    /** Enqueues a row for writing. Safe to call from the main thread; a no-op if not started. */
    fun log(row: SessionRow) {
        channel?.trySend(row)
    }

    /** Closes the channel so the writer coroutine flushes and closes the file, then clears state. */
    fun stop() {
        channel?.close()
        channel = null
        writerJob = null
        sessionFile = null
    }
}
