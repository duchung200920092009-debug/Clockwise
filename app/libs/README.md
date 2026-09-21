# app/libs — Samsung Health Sensor SDK

Đặt file **`.aar`** của Samsung Health Sensor SDK vào đúng thư mục này:

```
app/libs/samsung-health-sensor-api.aar
```

`app/build.gradle.kts` đã tự nạp mọi file `*.aar` trong thư mục này:

```kotlin
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
```

Nên chỉ cần thả file vào là build được — không cần sửa gì thêm.

## Vì sao file .aar KHÔNG nằm trong repo?

1. **Bản quyền:** SDK là của Samsung, không được phát hành lại (redistribute). Vì vậy `.aar`
   bị chặn trong `.gitignore` (`/app/libs/*.aar`), không commit lên repo.
2. Mỗi người tự tải từ Samsung Developer bằng tài khoản của mình sau khi đồng ý điều khoản.

## Lấy file .aar ở đâu

1. Vào **Samsung Developer → Health → Samsung Health Sensor SDK**
   (`developer.samsung.com/health/sensor`).
2. Đăng nhập tài khoản Samsung, đồng ý điều khoản, tải gói SDK về.
3. Giải nén, lấy file `samsung-health-sensor-api.aar`, chép vào `app/libs/`.

> MVP **không cần** đăng ký Samsung Partner: chạy bằng Developer Mode + sideload APK là đủ.
> Partner chỉ cần khi phân phối qua Galaxy Store.

## Yêu cầu thiết bị

- Galaxy Watch 4 trở lên (đang dùng **Galaxy Watch 6, Wear OS 4**).
- Trên đồng hồ phải có nền tảng **Samsung Health**. Nếu app báo "chưa kết nối được cảm biến",
  kiểm tra Samsung Health đã cài/cập nhật trên watch chưa.

## Sau khi thả .aar vào

Trên máy có Android Studio + đã `adb connect` tới watch (xem nghi thức trong CLAUDE.md):

```
./gradlew :app:installDebug
```

rồi mở app trên watch. Đeo watch → thấy số nhịp tim thật nhảy real-time là xong Stage 1.
