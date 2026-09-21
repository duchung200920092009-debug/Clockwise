package com.zenpulse.wear.data

import android.content.Context
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Đọc nhịp tim real-time từ Galaxy Watch qua **Samsung Health Sensor SDK**.
 *
 * PHẠM VI STAGE 1: CHỈ nhịp tim (HR). Tracker [HealthTrackerType.HEART_RATE_CONTINUOUS] thực ra
 * trả về cả IBI trong mỗi gói dữ liệu, nhưng ở stage này ta **bỏ qua IBI hoàn toàn** — IBI là
 * việc của Stage 2.
 *
 * ⚠️ Lưu ý chỉnh so với briefing: briefing ghi `HealthTrackerType.HEART_RATE`, nhưng loại tracker
 * cho luồng HR + IBI liên tục (1 Hz) đúng tên là `HEART_RATE_CONTINUOUS`. Đây là loại gắn với
 * `ValueKey.HeartRateSet`. (Xem CLAUDE.md, mục "Điều chỉnh so với plan".)
 *
 * Toàn bộ vòng đời được gói trong một [Flow] "lạnh" (cold flow): chỉ khi có người thu (collector)
 * thì mới kết nối service + đăng ký listener; khi người thu dừng thì [awaitClose] tự gỡ listener và
 * ngắt service. Nhờ vậy cảm biến KHÔNG chạy khi không ai nghe → đỡ tốn pin (ràng buộc pin là thật).
 */
class HeartRateTracker(private val context: Context) {

    /**
     * Phát [HeartSignal] cho tới khi collector huỷ.
     *
     * Trình tự: tạo [HealthTrackingService] → [HealthTrackingService.connectService] →
     * chờ [ConnectionListener.onConnectionSuccess] → lấy tracker → [HealthTracker.setEventListener].
     */
    fun stream(): Flow<HeartSignal> = callbackFlow {
        // Giữ tham chiếu để dọn dẹp trong awaitClose. Gán trước khi callback dùng tới.
        var service: HealthTrackingService? = null
        var tracker: HealthTracker? = null

        // Nhận từng gói dữ liệu cảm biến. Callback này chạy trên thread nền của SDK.
        val trackerListener = object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: List<DataPoint>) {
                for (point in dataPoints) {
                    val status = point.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
                    val bpm = point.getValue(ValueKey.HeartRateSet.HEART_RATE)
                    // status == 1: nhịp đo hợp lệ. 0: cảm biến đang khởi động. Số âm (vd -3 = chưa
                    // đeo lên tay): tín hiệu chưa đủ tốt → coi như chưa đọc được, đừng hiện số rác.
                    // (Chi tiết mã status: Samsung "Health Sensor Data Specifications".)
                    trySend(HeartSignal.Reading(bpm = bpm, valid = status == HR_STATUS_VALID))
                    // CỐ Ý không đọc ValueKey.HeartRateSet.IBI_LIST ở Stage 1.
                }
            }

            override fun onFlushCompleted() {
                // Stage 1 không chủ động flush dữ liệu — không cần làm gì.
            }

            override fun onError(trackerError: HealthTracker.TrackerError) {
                trySend(HeartSignal.Failed(trackerError.toHeartError()))
            }
        }

        // Nhận kết quả kết nối tới nền tảng Samsung Health trên watch.
        val connectionListener = object : ConnectionListener {
            override fun onConnectionSuccess() {
                val connected = service ?: return
                // Chắc chắn watch này hỗ trợ luồng HR liên tục trước khi tạo tracker.
                val supported = runCatching { connected.trackingCapability.supportHealthTrackerTypes }
                    .getOrDefault(emptyList())
                if (HealthTrackerType.HEART_RATE_CONTINUOUS !in supported) {
                    trySend(HeartSignal.Failed(HeartError.UNSUPPORTED))
                    return
                }
                trySend(HeartSignal.Connected)
                tracker = connected.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                    .apply { setEventListener(trackerListener) }
            }

            override fun onConnectionEnded() {
                // Ta chủ động ngắt trong awaitClose; ở đây không cần xử lý thêm.
            }

            override fun onConnectionFailed(e: HealthTrackerException) {
                trySend(HeartSignal.Failed(HeartError.CONNECTION))
            }
        }

        service = try {
            HealthTrackingService(connectionListener, context).apply { connectService() }
        } catch (e: Throwable) {
            // Thường gặp khi thiếu SDK/nền tảng Samsung Health, hoặc chạy trên máy ảo không có SDK.
            trySend(HeartSignal.Failed(HeartError.CONNECTION))
            null
        }

        awaitClose {
            // Dọn đúng lifecycle: gỡ listener rồi ngắt service (tránh rò rỉ + tốn pin).
            runCatching { tracker?.unsetEventListener() }
            runCatching { service?.disconnectService() }
        }
    }

    private fun HealthTracker.TrackerError.toHeartError(): HeartError = when (this) {
        HealthTracker.TrackerError.PERMISSION_ERROR -> HeartError.PERMISSION
        HealthTracker.TrackerError.SDK_POLICY_ERROR -> HeartError.SDK_POLICY
        else -> HeartError.UNKNOWN
    }

    private companion object {
        /** Mã trạng thái "nhịp tim đo hợp lệ" trong ValueKey.HeartRateSet.HEART_RATE_STATUS. */
        const val HR_STATUS_VALID = 1
    }
}

/** Một sự kiện đến từ luồng cảm biến nhịp tim. */
sealed interface HeartSignal {
    /** Đã kết nối nền tảng Samsung Health, cảm biến đang khởi động — chưa có nhịp hợp lệ. */
    data object Connected : HeartSignal

    /**
     * Một lần đọc nhịp tim. [valid] = false khi cảm biến còn đang dò hoặc watch chưa đeo khít —
     * lúc đó hiện trạng thái "đang chờ tín hiệu", đừng hiện [bpm].
     */
    data class Reading(val bpm: Int, val valid: Boolean) : HeartSignal

    /** Có trục trặc (kết nối lỗi, thiếu quyền, chính sách SDK, watch không hỗ trợ...). */
    data class Failed(val reason: HeartError) : HeartSignal
}

/** Loại trục trặc, để lớp trên hiển thị thông điệp phù hợp. */
enum class HeartError { CONNECTION, PERMISSION, SDK_POLICY, UNSUPPORTED, UNKNOWN }
