# J2ME4iOS

A J2ME (MIDP 2.0 / CLDC 1.1) game runner for iOS, written from scratch:

- a Java bytecode **interpreter in portable C** (no JIT, so it is allowed on iOS),
- the CLDC/MIDP class library **reimplemented in Java**,
- an **Objective-C** iOS app around it.

> Status: **early preview**. The VM passes a test suite compared against a real JVM, and the MIDP, M3G and
> Mascot Capsule test MIDlets render correctly on the Windows development build. The iOS app builds in CI but
> has had little testing on real devices yet. The app's interface is in Vietnamese.

## Install on iPhone

1. Download `J2ME4iOS-unsigned.ipa` from the latest [release](../../releases)
   (or from the **Artifacts** of the latest run in the **Actions** tab).
2. Install it with **Sideloadly** or **AltStore** (they sign the IPA with your Apple ID), or **TrollStore**
   on devices that support it.

Notes on sideloading:

- On iOS 16 and later, apps signed with a free Apple ID (development certificate) only start after enabling
  **Settings → Privacy & Security → Developer Mode**. This is an iOS rule for every sideloaded app; J2ME4iOS
  needs no special entitlement. TrollStore installs or Ad Hoc signing with a paid developer account do not need it.
- With a free Apple ID the app expires after 7 days and has to be refreshed (AltStore can do this
  automatically). Saved games are kept when the app is reinstalled over itself.

Building on a Mac instead:

```bash
bash tools/prepare_ios.sh
```

```bash
cd platform/ios && xcodegen generate && open J2ME4iOS.xcodeproj
```

Then choose a signing team in Xcode and press Run.

## Using the app

- **Adding games:**
  - tap **+** and pick a `.jar` (pick its `.jad` too if the game has one);
  - or copy files into the *J2ME4iOS* folder in the **Files** app;
  - or use "Open in…" from another app.

  A `.jad` is matched to a `.jar` with the same name, or through its `MIDlet-Jar-URL` line.
- **Tap a game** for its menu: play, **Cài đặt game…** (game settings), rename, delete saved data.
- **Game settings** (stored per game):
  - **Screen:**
    - resolution (any size can be typed in);
    - orientation;
    - scaling: fit / whole multiples / stretch / native 1×;
    - smoothing, background color, FPS counter.
  - **Keypad:**
    - shown / hidden / automatic (hidden while a controller or hardware keyboard is connected);
    - phone layout or gamepad layout (8-way joystick);
    - key opacity in landscape, haptic feedback.
  - **Text:** small / medium / large font sizes, anti-aliasing on or off.
  - **System:** emulated phone model (`microedition.platform`, e.g. Nokia, Sony Ericsson, Samsung) and extra
    system properties (`key=value; key2=value2`).
  - **Network:** a proxy for each save slot (see below).
- **On-screen keypad:**
  - fingers can slide between keys: the old key is released and the new one pressed;
  - the gamepad layout has an 8-way joystick (diagonals press two arrow keys);
  - the game screen itself is touch enabled.
- **Hardware keyboard:**
  - arrows; Enter/Space = fire; Q/F1 and W/F2 = soft keys;
  - 0–9; E = `*`; R = `#`; Backspace = clear;
  - Esc or M opens the game menu.
- **Game controllers** (MFi, Xbox, PlayStation):
  - D-pad / left stick = arrows; right stick = 2/4/6/8;
  - the Home button opens the game menu;
  - the other buttons can be remapped in **⚙︎ Settings** on the game list.
- **The `☰` button** opens the in-game menu:
  - show/hide the keypad, switch keypad layout;
  - scaling, smoothing;
  - volume, FPS counter;
  - **Xem log** (view log: copy or share it when reporting a problem);
  - back to the game list, switch game, quit.
- **MIDI music:**
  - played by the built-in synthesizer by default, nothing to install;
  - if a `.sf2`/`.dls` soundfont (for example GeneralUser GS) is copied into the J2ME4iOS folder, the
    "automatic" mode uses it for better sound;
  - the mode is chosen in **⚙︎ Settings**.
- **Several games at once:**
  - `☰` → **Về danh sách (game vẫn chạy)** returns to the game list while the game keeps running
    (network connections stay open). The **Đang chạy** (running) section at the top of the list brings it back.
  - **Chuyển sang game khác…** switches directly between running games.
  - Each game is its own VM using about 64 MB of memory; the app warns when more than 4 are open.
  - Only the game on screen plays sound, unless **⚙︎ Settings → background games keep playing sound** is on.
- **Save slots:**
  - each game has 5 save slots, each with its own RecordStores and files (for example one account per slot);
  - pick a slot with **Chơi ở khe lưu khác…** (play in another slot); several slots of the same game can run at once;
  - slot 1 is the game's original save data.
- **Proxy per save slot:** in **Game settings → Network**, each slot can have its own proxy. All socket and HTTP
  connections of the game in that slot go through it. Supported forms:
  - `socks5://user:pass@host:port`,
  - `socks5h://...` (the proxy resolves host names),
  - `http://host:port` (HTTP CONNECT).
- **Saved data** lives in `Library/Application Support/RMS/<jar name>/`; slots 2–5 in `<jar name>#2` … `<jar name>#5`.

## Layout

| Folder | Contents |
|---|---|
| `core/` | The VM (portable C11): JAR/class loading, interpreter, GC, green threads, natives, 2D rasterizer, PNG decoder, 3D rasterizer, MIDI synthesizer, sockets and proxies |
| `runtime/src/` | Java library: `java.lang/util/io`, `javax.microedition.*`, `javax.microedition.m3g`, `com.mascotcapsule.micro3d.v3`, vendor APIs, `j4i.*` bridge |
| `platform/ios/` | iOS app; XcodeGen spec in `project.yml` |
| `platform/desktop/` | Windows runner with a window, plus a headless mode with key scripts and screenshots for development |
| `platform/headless/` | Portable command-line runner used by CI (can run several VMs at once) |
| `tests/` | VM tests compared against a real JVM, self-checking I/O tests, visual test MIDlets |
| `tools/` | Build scripts |

## Build and test on Windows

Requires Visual Studio with C++ (MSVC), CMake, Ninja and a JDK ≥ 9.

```bash
tools\build_win.bat
```

```bash
build\j4i.exe path\to\game.jar
```

- **Keys:** arrows; Enter/Space = fire; F1/Q = left soft key; F2/W = right soft key; 0–9; E = `*`; R = `#`;
  Backspace = clear; F12 = screenshot.
- **Options:**
  - `--size 176x208`, `--heap 64`;
  - `--trace 1` (GC/exception log), `--trace 2` (class loading log);
  - `--jad game.jad`, `--prop microedition.platform=SonyEricssonK800i` (repeatable);
  - `--font 10,13,18` (small, medium, large font sizes), `--no-aa` (no text anti-aliasing);
  - `--host-midi` (disable the built-in MIDI synthesizer);
  - `--proxy socks5://user:pass@host:port` (also `socks5h://` and `http://`).
- **Several VMs at once** (`j4i-headless`): `--instances 4 --log-prefix out` runs 4 VMs in parallel; each logs to
  `out.N.txt` and keeps its data in `<data>/slotN`.
- **Sound on Windows:** one sound at a time (WAV, or MIDI through the synthesizer), enough for testing.

Headless run with a key script and screenshots (times in milliseconds):

```bash
build\j4i.exe --headless --script "1500=shot:a.png,2000=k:-5,3000=shot:b.png,4000=quit" game.jar
```

Test suites:

```bash
bash tests/run_lang_test.sh
```

```bash
bash tests/run_io_test.sh
```

```bash
bash tests/run_multi_test.sh
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

The visual tests produce `build/tests/testmidlet.jar`, `m3gtest.jar` and `mascottest.jar`; run them with
`build\j4i.exe`. In `m3gtest.jar`, keys 1/2/3 switch scenes.

```bash
build\j4i.exe build\tests\testmidlet.jar
```

## Supported

- **CLDC 1.1:** the whole instruction set (including `jsr/ret` from old compilers), exceptions,
  `synchronized`/`wait`/`notify`, threads, `Timer`, `float`/`double`, `String`/`StringBuffer`/`Vector`/
  `Hashtable`/`Calendar`.
- **MIDP 2.0 lcdui:**
  - `Canvas`, `Graphics` (all drawing calls, `drawRegion` with the 8 transforms, `drawRGB`, `copyArea`);
  - `Image` (all PNG formats; JPEG on iOS), `Font`;
  - `Command` with soft keys and an options menu;
  - `Form`, `List`, `Alert`, `TextBox`, `TextField` (iOS keyboard), `ChoiceGroup`, `Gauge`, `StringItem`,
    `ImageItem`, `DateField`, `Spacer`, `CustomItem`.
- **lcdui.game:** `GameCanvas`, `Sprite` (pixel-level collisions), `TiledLayer`, `LayerManager`.
- **3D:**
  - **M3G (JSR-184):** full scene graph, `.m3g` loader (including zlib sections), keyframe animation (SLERP),
    `SkinnedMesh`, `MorphingMesh`, `Sprite3D`, lighting, fog, two texture units, blending, `pick`.
  - **Mascot Capsule v3** (`com.mascotcapsule.micro3d.v3`): MBAC models, MTRA animations, 8-bit BMP textures;
    bones, patterns, lighting, toon shading, semi-transparent modes, `renderPrimitives`, `drawCommandList`.
  - Both use the same software rasterizer written in C (`core/m3g.c`).
- **Sound:**
  - `Manager`/`Player` for WAV, MP3 and MIDI; MIDI goes through the built-in General MIDI synthesizer
    (`core/midi.c`), no soundfont needed.
  - Tone sequences (`ToneControl`, `.jts` files) and `Manager.playTone`.
  - Nokia ringing tones (`Sound` with `FORMAT_TONE` / OTT).
- **Storage:**
  - **RMS:** complete `RecordStore`.
  - **Files (JSR-75):** `FileConnection`, `FileSystemRegistry`; games only see their own `file:///root1/`.
- **Network:**
  - `HttpConnection` (http) and `SocketConnection`, optionally through a SOCKS5 / HTTP CONNECT proxy;
  - `resource:` URLs for jar resources.
- **Vendor APIs:**
  - **Nokia:** `FullCanvas`, `DirectUtils`, `DirectGraphics`, `DeviceControl`, `Sound`.
  - **Samsung:** `AudioClip`, `Vibration`, `LCDLight`, `SMS`.
  - **Siemens:**
    - game: `ExtendedImage`, `Sprite`, `TiledBackground`, `GraphicObjectManager`, `Melody`, `Sound`, `Light`, `Vibrator`;
    - `color_game.*`;
    - io: `File`;
    - lcdui: `Image`.
  - **Motorola:** `FunLight`, `Lighting`, `Vibrator`, `MidiPlayer`.
  - **LG:** `mmpp.media.MediaPlayer`.
- **Present but reporting "not supported":** SMS/MMS (`javax.wireless.messaging`; sending fails) and Bluetooth
  (`javax.bluetooth`). Games using them still start; only that feature does not work.
- **App:** JAD files, per-game settings, several games at once, save slots, per-slot proxies, game controllers,
  hardware keyboards, log viewer.

## Not supported yet

- **Network:** HTTPS (needs TLS on iOS).
- **Sound:** AMR and SMAF/MMF (Samsung). Games run, these sounds are silent.
- **Mascot Capsule:** sphere maps (reflections); point and line primitives are not drawn.
- **Not yet tried with real games:** Mascot Capsule and the vendor APIs have only been run with generated
  test data. There is no official MBAC/MTRA specification, so real models may still show problems.
- **Missing:** Sony Ericsson APIs other than Mascot Capsule, Motorola `com.motorola.game.*`, LG `mmpp.phone`, `MathFP`.

## Reporting a problem

- The game's name and `.jar` (if it can be shared).
- What happens: black screen, error, slow, wrong graphics, no sound…
- The log:
  - **iOS:** `☰` → **Xem log** → copy or share;
  - **Windows:** the full output of `build\j4i.exe --trace 1 game.jar`.

## Acknowledgements

The MBAC/MTRA (Mascot Capsule) file formats were worked out with the help of the open-source
[J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) project (Apache-2.0); J2ME4iOS's implementation is its own.
