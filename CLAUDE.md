# CLAUDE.md — ZenPulse (Đồng Hồ Sinh Học)

Hướng dẫn cho Claude Code khi làm việc trong repo này. Đọc file này trước khi viết code.

> File này được dựng lại từ hai tài liệu `SESSION_SUMMARY_stage_1.md` và `STAGE_1_BRIEFING.md`
> (bản CLAUDE.md gốc chưa được commit vào repo). Nếu bạn có bản gốc đầy đủ hơn, thay thế file này.

---

## 1. Dự án là gì

App đeo tay giúp nhận biết sớm trạng thái căng thẳng để nhắc người dùng thư giãn — hướng
**wellness**, không phải thiết bị y tế. Lõi phát hiện dựa trên **nhịp tim (HR) + IBI/HRV**.

- **Thiết bị:** Galaxy Watch 6 (40mm), **Wear OS 4**.
- **App:** Kotlin + **Jetpack Compose for Wear OS**.

## 2. Stack đã CHỐT — đừng tự đổi

- **Cảm biến:** **Samsung Health Sensor SDK** cho HR + IBI.
  **KHÔNG dùng Google Health Services** — nó không cho IBI/HRV (lõi phát hiện stress).
- **Kiến trúc A:** watch **mỏng** (chỉ thu tín hiệu + hiển thị). Mọi phân tích/ngưỡng/stress
  chạy trên **phone** ở stage sau. Đừng nhét phân tích vào watch.
- **Không cần Samsung Partner** cho MVP: Developer Mode + sideload APK là đủ.
- **Đã CẮT khỏi MVP:** ML/TFLite, baseline dài.

> **Hỏi trước khi thêm dependency / SDK / kiến trúc mới.** Stack đã chốt.

## 3. Ranh giới các Stage — đừng làm vượt

| Việc | Thuộc stage |
|---|---|
| Đọc HR + hiện real-time lên watch | **Stage 1 (hiện tại)** |
| IBI / RR-interval (`ValueKey.HeartRateSet.IBI_LIST`), accelerometer, ghi log ra file | Stage 2 |
| Gửi dữ liệu sang phone (Wearable Data Layer), Room database | Stage 3+ |
| Logic phát hiện stress / baseline / threshold | Stage 4 |
| ML / TFLite | ĐÃ CẮT |

Lý do tách nhỏ: rủi ro deadline nằm ở phần cảm biến (IBI nhiễu). Mỗi lần chỉ thêm một biến để
khi lỗi thì biết lỗi ở đâu.

## 4. Ràng buộc xuyên suốt

- **Pin là ràng buộc thật.** Đọc cảm biến liên tục tốn pin. Luôn gỡ listener + ngắt service khi
  không dùng (code hiện dùng cold `Flow` + `awaitClose` để tự dọn). Để ý watch nóng / tụt pin.
- **Watch giữ mỏng:** chỉ capture + render.
- **Code cho người mới Kotlin đọc được:** idiomatic, hàm nhỏ, comment rõ (tiếng Việt được).
- **UI copy tiếng Việt, giọng wellness — KHÔNG dùng thuật ngữ y tế.** Copy tập trung ở
  `app/src/main/res/values/strings.xml`.

## 5. Điều chỉnh so với plan (ghi lại thay vì chôn trong commit)

- **Loại tracker HR:** briefing ghi `HealthTrackerType.HEART_RATE`. Thực tế loại cho luồng HR
  liên tục 1 Hz (gắn với `ValueKey.HeartRateSet`) tên đúng là **`HEART_RATE_CONTINUOUS`**. Code
  dùng `HEART_RATE_CONTINUOUS`.
- **Mã trạng thái HR:** đọc `ValueKey.HeartRateSet.HEART_RATE_STATUS`. `1` = hợp lệ, `0` = đang
  khởi động, số âm (vd `-3` = chưa đeo) = tín hiệu chưa đủ tốt → không hiện số. (Chi tiết: Samsung
  "Health Sensor Data Specifications".)
- **Repo có sẵn code Google Health Services** (từ commit "Week 1") — đã **thay hoàn toàn** bằng
  Samsung SDK cho đúng stack đã chốt ở §2.

## 6. Samsung SDK — file .aar

`.aar` là proprietary, **không commit** (đã chặn trong `.gitignore`). Tải từ Samsung Developer và
thả vào `app/libs/`. Xem `app/libs/README.md`.

## 7. Nghi thức mỗi buổi làm việc (ma sát cố định, không phải lỗi)

IP:port của watch **đổi mỗi phiên**. Mỗi buổi:
1. Xem lại `IP:port` trên watch (Wireless debugging).
2. `adb connect <IP:port>` — **viết liền, KHÔNG dấu cách**.
3. Connect fail → pair lại bằng mã 6 số (cổng pairing KHÁC cổng connect).
4. `adb` không trong PATH → dùng đường dẫn đầy đủ, hoặc `cd` vào `platform-tools` rồi `.\adb`.

## 8. "Done" của Stage 1

- [ ] Xin + xử lý được quyền `BODY_SENSORS` (kể cả khi user từ chối).
- [ ] `HealthTrackingService` connect thành công trên watch thật.
- [ ] Số HR thật hiện real-time trên watch, nhảy theo tim đập.
- [ ] Listener + service được dọn đúng lifecycle.
- [ ] KHÔNG có IBI, accel, log file, hay code phone lẫn vào.
- [ ] Đã liếc mức tiêu pin sau vài phút chạy.
