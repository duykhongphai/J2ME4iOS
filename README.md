# J2ME4iOS

Trình chạy game J2ME (MIDP 2.0 / CLDC 1.1) cho iOS, viết từ đầu:

- máy ảo Java thông dịch viết bằng C thuần, không dùng JIT nên hợp lệ trên iOS,
- thư viện CLDC/MIDP viết lại bằng Java,
- vỏ app iOS viết bằng Objective-C.

> Trạng thái: **bản thử nghiệm (MVP)**. Lõi máy ảo đã qua bộ test so sánh với JVM thật. Phần MIDP 2D đã chạy được MIDlet test trên Windows. App iOS chưa được chạy thử trên máy thật.

## Cấu trúc

| Thư mục | Nội dung |
|---|---|
| `core/` | Máy ảo (C11, chạy được trên mọi nền tảng): đọc JAR/class, bộ thông dịch, GC, green thread, natives, đồ họa 2D, giải mã PNG |
| `runtime/src/` | Thư viện Java: `java.lang/util/io`, `javax.microedition.*` (lcdui, game, rms, media, io), `com.nokia.mid.*`, cầu nối `j4i.*` |
| `platform/ios/` | App iOS: danh sách game, bàn phím ảo, cảm ứng, CoreText, AVFoundation; cấu hình XcodeGen trong `project.yml` |
| `platform/desktop/` | Bản chạy trên Windows có cửa sổ, kèm chế độ chạy không cửa sổ có kịch bản phím và chụp màn hình, dùng để phát triển/test |
| `platform/headless/` | Bản chạy dòng lệnh, dùng trên mọi OS, chạy trong CI |
| `tests/` | `lang/LangTest.java` (so kết quả với JVM thật), `midp/TestMidlet.java` (test hình ảnh) |

## Lấy bản IPA để cài lên iPhone

1. Push code lên GitHub. Workflow `.github/workflows/build.yml` sẽ build:
   - job **core-linux**: build lõi máy ảo và chạy test ngôn ngữ,
   - job **ios**: tạo file `J2ME4iOS-unsigned.ipa`.
2. Vào tab **Actions** → chọn lần chạy mới nhất → mục **Artifacts** → tải `J2ME4iOS-unsigned-ipa`.
3. Cài bằng **Sideloadly** hoặc **AltStore** (chúng tự ký IPA bằng Apple ID của bạn), hoặc **TrollStore** nếu máy hỗ trợ.

Nếu có máy Mac thì build trực tiếp:

```bash
bash tools/build_runtime.sh
```

```bash
cd platform/ios && xcodegen generate && open J2ME4iOS.xcodeproj
```

Sau đó chọn Team ký ứng dụng trong Xcode và bấm Run.

## Dùng app trên iPhone

- **Nhập game:** bấm **+** để chọn file `.jar`, hoặc chép `.jar` vào thư mục *J2ME4iOS* trong app **Tệp (Files)**, hoặc dùng "Mở bằng" từ app khác.
- **Chạm vào game** để chơi hoặc chỉnh riêng cho từng game:
  - độ phân giải (mặc định 240x320),
  - hướng màn hình: tự động / dọc / ngang,
  - kiểu phóng to: vừa khung / số nguyên (nét nhất) / kéo giãn,
  - ẩn bàn phím ảo (cho game cảm ứng),
  - xoá dữ liệu lưu.
- **Bàn phím ảo:** phím mềm trái/phải, D-pad, OK, 0–9, `*`, `#`, `C`. Có thể chạm trực tiếp lên màn hình game. Hỗ trợ bàn phím rời: phím mũi tên, Enter, số, F1/F2 (phím mềm).
- **Nút `☰`** mở menu trong game: thoát, ẩn/hiện bàn phím, đổi kiểu phóng to, bật/tắt làm mịn ảnh. Khi ẩn bàn phím, nút `☰` nằm ở góc trên bên phải.
- **Nhạc MIDI:** iOS cần soundfont. Chép một file `.sf2` hoặc `.dls` (ví dụ GeneralUser GS) vào thư mục J2ME4iOS trong app Tệp. Âm thanh WAV/MP3 chạy được ngay.
- **Dữ liệu lưu (RecordStore)** nằm trong `Library/Application Support/RMS/<tên jar>/`.

## Build và test trên Windows

Cần Visual Studio có C++ (MSVC), CMake, Ninja và JDK ≥ 9.

```bash
tools\build_win.bat
```

```bash
build\j4i.exe path\to\game.jar
```

- **Phím:** mũi tên; Enter/Space = OK; F1/Q = phím mềm trái; F2/W = phím mềm phải; 0–9; E = `*`; R = `#`; Backspace = C; F12 = chụp màn hình.
- **Tuỳ chọn:** `--size 176x208`, `--heap 64`, `--trace 1` (in log GC/exception), `--trace 2` (log nạp class).

Chạy không cửa sổ, có kịch bản phím và chụp màn hình (thời gian tính bằng mili giây):

```bash
build\j4i.exe --headless --script "1500=shot:a.png,2000=k:-5,3000=shot:b.png,4000=quit" game.jar
```

Chạy các bộ test:

```bash
bash tests/run_lang_test.sh
```

```bash
bash tests/run_io_test.sh
```

```bash
bash tests/build_test_midlet.sh
```

```bash
bash tests/build_m3g_test.sh
```

```bash
bash tests/build_mascot_test.sh
```

Các test hình ảnh tạo ra `build/tests/testmidlet.jar`, `m3gtest.jar` và `mascottest.jar`. Chạy chúng bằng `build\j4i.exe`. Trong `m3gtest.jar`, phím 1/2/3 để đổi cảnh.

```bash
build\j4i.exe build\tests\testmidlet.jar
```

## Đã hỗ trợ

- **CLDC 1.1:** toàn bộ bytecode (kể cả `jsr/ret` của class file cũ), exception, `synchronized`/`wait`/`notify`, thread, `Timer`, kiểu `float`/`double`, các class `String`/`StringBuffer`/`Vector`/`Hashtable`/`Calendar`.
- **MIDP 2.0 lcdui:**
  - `Canvas`, `Graphics` (đủ các hàm vẽ, `drawRegion` với 8 phép biến đổi, `drawRGB`, `copyArea`),
  - `Image` (PNG mọi định dạng; JPEG trên iOS), `Font`,
  - `Command` + phím mềm + menu,
  - `Form`, `List`, `Alert`, `TextBox`, `TextField` (dùng bàn phím iOS), `ChoiceGroup`, `Gauge`, `StringItem`, `ImageItem`, `DateField`, `Spacer`, `CustomItem`.
- **lcdui.game:** `GameCanvas`, `Sprite` (va chạm theo pixel), `TiledLayer`, `LayerManager`.
- **3D:**
  - **M3G (JSR-184):** scene graph đầy đủ, đọc file `.m3g` (kể cả phần nén zlib), animation (keyframe, SLERP), SkinnedMesh, MorphingMesh, Sprite3D, ánh sáng, sương mù, 2 lớp texture, hoà màu, `pick`.
  - **Mascot Capsule v3** (`com.mascotcapsule.micro3d.v3`): đọc model MBAC, animation MTRA, texture BMP 8-bit; xương, pattern, ánh sáng, toon shading, các chế độ trong suốt, `renderPrimitives`, `drawCommandList`.
  - Cả hai dùng chung bộ dựng hình phần mềm viết bằng C (`core/m3g.c`).
- **Âm thanh:**
  - `Manager`/`Player` cho WAV, MP3, MIDI.
  - Chuỗi tone (`ToneControl`, file `.jts`) và `Manager.playTone`.
  - Nhạc chuông Nokia (`Sound` dạng `FORMAT_TONE`/OTT).
- **Lưu trữ:**
  - **RMS:** `RecordStore` đầy đủ.
  - **File (JSR-75):** `FileConnection`, `FileSystemRegistry`. Game chỉ thấy thư mục riêng `file:///root1/`.
- **Mạng:**
  - `HttpConnection` (http) và `SocketConnection`.
  - `resource:` đọc file trong jar.
- **API riêng của hãng:**
  - **Nokia:** `FullCanvas`, `DirectUtils`, `DirectGraphics`, `DeviceControl`, `Sound`.
  - **Samsung:** `AudioClip`, `Vibration`, `LCDLight`, `SMS`.
  - **Siemens:**
    - game: `ExtendedImage`, `Sprite`, `TiledBackground`, `GraphicObjectManager`, `Melody`, `Sound`, `Light`, `Vibrator`;
    - `color_game.*`;
    - io: `File`;
    - lcdui: `Image`.
  - **Motorola:** `FunLight`, `Lighting`, `Vibrator`, `MidiPlayer`.
  - **LG:** `mmpp.media.MediaPlayer`.
- **Có lớp nhưng chỉ báo "không hỗ trợ":** SMS/MMS (`javax.wireless.messaging`, gửi tin sẽ lỗi) và Bluetooth (`javax.bluetooth`). Game dùng các API này vẫn mở được, chỉ tính năng đó không chạy.

## Chưa hỗ trợ / sẽ làm tiếp

- **Mạng:** HTTPS. Cần thêm phần TLS trên iOS.
- **Âm thanh:** AMR và SMAF/MMF (Samsung). Game vẫn chạy nhưng các âm thanh này sẽ im lặng.
- **Mascot Capsule:** sphere map (hiệu ứng phản chiếu). Primitive dạng điểm và đường chưa được vẽ.
- **Mascot Capsule và API hãng chưa thử với game thật:** mới chạy với dữ liệu tự sinh. Định dạng MBAC/MTRA không có tài liệu chính thức, nên có thể còn sai ở model thật. Nếu gặp lỗi, gửi kèm file game cho tôi.
- **Thiếu hẳn:** Sony Ericsson (ngoài Mascot Capsule), Motorola `com.motorola.game.*`, LG `mmpp.phone`, `MathFP`.

## Khi test, gửi lại cho tôi

- Tên game và file `.jar` (nếu chia sẻ được).
- Hiện tượng gặp phải: màn hình đen, lỗi, chạy chậm, sai hình, không có tiếng…
- Log:
  - **Trên Windows:** toàn bộ output khi chạy `build\j4i.exe --trace 1 game.jar`.
  - **Trên iOS:** log có trong Console.app của Mac (lọc theo `J2ME4iOS`), hoặc màn hình báo lỗi hiện trong game.
