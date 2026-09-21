package com.zenpulse.wear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zenpulse.wear.data.HeartError
import com.zenpulse.wear.data.HeartRateTracker
import com.zenpulse.wear.data.HeartSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Các pha màn hình HR trải qua. Màn hình chọn nội dung hiển thị theo pha này. */
enum class HeartPhase {
    /** Đang mở kết nối tới nền tảng Samsung Health. */
    CONNECTING,

    /** Đã kết nối nhưng chưa có nhịp hợp lệ (cảm biến đang dò / watch chưa khít). */
    WAITING_SIGNAL,

    /** Đang có nhịp tim thật, cập nhật real-time. */
    LIVE,

    /** Có trục trặc — xem [HeartRateUiState.error]. */
    ERROR,
}

/** Những gì màn hình nhịp tim cần để vẽ. */
data class HeartRateUiState(
    val phase: HeartPhase = HeartPhase.CONNECTING,
    val bpm: Int? = null,
    val error: HeartError? = null,
)

/**
 * Cầu nối giữa [HeartRateTracker] (luồng cảm biến) và UI.
 *
 * Watch giữ MỎNG: ở đây không có phân tích/ngưỡng/stress gì cả — chỉ nhận tín hiệu thô rồi quy
 * về một pha hiển thị. Mọi phân tích để dành cho phone ở stage sau.
 */
class HeartRateViewModel(app: Application) : AndroidViewModel(app) {

    private val tracker = HeartRateTracker(app)

    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState: StateFlow<HeartRateUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    /** Bắt đầu thu nhịp tim. CHỈ gọi sau khi đã có quyền BODY_SENSORS. */
    fun start() {
        if (collectJob?.isActive == true) return
        _uiState.value = HeartRateUiState(phase = HeartPhase.CONNECTING)
        collectJob = viewModelScope.launch {
            tracker.stream().collect { signal ->
                _uiState.update { current -> current.reduce(signal) }
            }
        }
    }

    /** Dừng thu — huỷ job kéo theo awaitClose gỡ listener + ngắt service (tiết kiệm pin). */
    fun stop() {
        collectJob?.cancel()
        collectJob = null
    }

    /** Thử lại từ đầu (dùng cho nút "Thử lại" khi gặp lỗi): ngắt sạch rồi kết nối lại. */
    fun restart() {
        stop()
        start()
    }

    private fun HeartRateUiState.reduce(signal: HeartSignal): HeartRateUiState = when (signal) {
        HeartSignal.Connected ->
            copy(phase = HeartPhase.WAITING_SIGNAL, error = null)

        is HeartSignal.Reading ->
            if (signal.valid) {
                copy(phase = HeartPhase.LIVE, bpm = signal.bpm, error = null)
            } else {
                // Không hiện số rác khi tín hiệu chưa tốt — quay về "đang chờ".
                copy(phase = HeartPhase.WAITING_SIGNAL, bpm = null)
            }

        is HeartSignal.Failed ->
            copy(phase = HeartPhase.ERROR, error = signal.reason)
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
