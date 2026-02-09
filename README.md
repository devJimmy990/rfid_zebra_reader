# rfid_zebra_reader

[![pub package](https://img.shields.io/pub/v/rfid_zebra_reader.svg)](https://pub.dev/packages/rfid_zebra_reader)
[![GitHub](https://img.shields.io/github/stars/devJimmy990/rfid_zebra_reader?style=social)](https://github.com/devJimmy990/rfid_zebra_reader)

A Flutter plugin for seamless integration with Zebra RFID readers. Built specifically for Zebra TC27 and compatible devices, providing real-time tag scanning, antenna power control, and full Android 13+ support.

> **Important:** This plugin requires a **real Zebra RFID device** (e.g., TC27) with a reader antenna. It **cannot** be tested on a mobile phone, emulator, or simulator. You must use an actual computer mobile device paired with a Zebra reader antenna.

---

## Android Configuration

### 1. Add Maven Repository

The Zebra RFID SDK is hosted on GitHub. You need to add the repository to your app's Gradle configuration.

<details>
<summary><b>Kotlin DSL</b> (build.gradle.kts) - Click to expand</summary>

<br>

If your app uses **Kotlin DSL**, add this to `your_app/android/build.gradle.kts`:

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()

        // Add Zebra RFID SDK repository
        maven {
            url = uri("https://raw.githubusercontent.com/devJimmy990/rfid_zebra_reader/main/android/maven")
        }
    }
}
```

</details>

<details>
<summary><b>Groovy</b> (build.gradle) - Click to expand</summary>

<br>

If your app uses **Groovy**, add this to `your_app/android/build.gradle`:

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()

        // Add Zebra RFID SDK repository
        maven {
            url "https://raw.githubusercontent.com/devJimmy990/rfid_zebra_reader/main/android/maven"
        }
    }
}
```

</details>

### 2. Set Minimum SDK Version

In `your_app/android/app/build.gradle` (or `build.gradle.kts`), ensure `minSdk` is **26** or higher:

```groovy
android {
    defaultConfig {
        minSdk = 26
    }
}
```

### 3. Update AndroidManifest.xml

In `your_app/android/app/src/main/AndroidManifest.xml`, add the `tools` namespace and `tools:replace` attribute:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        tools:replace="android:label"
        android:label="your_app_name"
        ... >
        ...
    </application>
</manifest>
```

### 4. Add ProGuard Rules

Create the file `your_app/android/app/proguard-rules.pro`:
<details>
<summary><b>proguard-rules.pro</b> - Click to expand</summary>

```bash
# --- Flutter Play Store Split Install ---
-keep class com.google.android.play.core.** { *; }

# --- Zebra RFID SDK (reflection-heavy) ---
-keep class com.zebra.** { *; }

# --- JSch (SFTP) ---
-keep class com.jcraft.jsch.** { *; }

# --- Xerces XML Parser ---
-keep class org.apache.xerces.** { *; }
-keep class org.w3c.dom.** { *; }

# --- BouncyCastle Crypto ---
-keep class org.bouncycastle.** { *; }

# --- LLRP Toolkit (used by Zebra) ---
-keep class org.llrp.** { *; }

# Prevent warnings
-dontwarn com.google.android.play.core.**
-dontwarn com.jcraft.jsch.**
-dontwarn org.apache.xerces.**
-dontwarn org.bouncycastle.**
-dontwarn org.llrp.**

# ============================================
# FIX: javax.lang.model (Google Error Prone)
# ============================================
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**
-keep class javax.lang.model.** { *; }
-keep class com.google.errorprone.annotations.** { *; }

# ============================================
# Additional javax warnings
# ============================================
-dontwarn javax.annotation.**
-dontwarn javax.tools.**
-keep class javax.annotation.** { *; }

# ============================================
# Keep Flutter classes
# ============================================
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# ============================================
# Keep native methods
# ============================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================
# Keep attributes for debugging
# ============================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# Keep Parcelable implementations
# ============================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================
# Keep Serializable classes
# ============================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================
# Suppress warnings for common missing classes
# ============================================
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn sun.security.**
-dontwarn com.sun.**
```

</details>

Then reference it in `your_app/android/app/build.gradle`:

```groovy
android {
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

---

## Quick Start

```dart
import 'package:rfid_zebra_reader/rfid_zebra_reader.dart';

// Initialize (auto-handles: permissions -> SDK init -> reader connection)
await ZebraRfidReader.initialize();

// Monitor initialization status
ZebraRfidReader.statusStream.listen((status) {
  print(status.statusMessage); // e.g. "Initializing SDK...", "Connected to reader"
  if (status.isReady) {
    print('Reader is ready!');
  }
});

// Listen for tag events
ZebraRfidReader.eventStream.listen((event) {
  if (event.type == RfidEventType.tagRead) {
    print('Tags: ${event.tags}');
  }
});

// Start scanning
await ZebraRfidReader.startInventory();

// Stop scanning
await ZebraRfidReader.stopInventory();

// Clean up when done
ZebraRfidReader.dispose();
```

---

## Main Functions

### Core Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `initialize()` | `Future<void>` | Initialize the RFID reader. Auto-handles permissions, SDK init, and reader connection. Status updates come through `statusStream` |
| `getStatus()` | `Future<RfidStatus>` | Get current reader status (one-time fetch) |
| `isPermissionGranted()` | `Future<bool>` | Check if required permissions are granted |
| `disconnect()` | `Future<bool>` | Disconnect from current reader |
| `startInventory()` | `Future<bool>` | Start scanning for RFID tags |
| `stopInventory()` | `Future<bool>` | Stop tag scanning |
| `setAntennaPower(int level)` | `Future<bool>` | Set antenna power level (0 to maxPower, typically 270) |
| `getAntennaPower()` | `Future<Map<String, int>>` | Get current and max antenna power (`currentPower`, `maxPower`) |
| `getPlatformVersion()` | `Future<String>` | Get Android platform version |
| `dispose()` | `void` | Dispose resources (call when app is closing) |

### Streams

| Property | Type | Description |
|----------|------|-------------|
| `eventStream` | `Stream<RfidEvent>` | Real-time RFID events (tag reads, triggers, connection changes) |
| `statusStream` | `Stream<RfidStatus>` | Reader status updates during initialization and reconnection. Auto-polls until connected or error |

---

## Models & Classes

### RfidEvent

| Property | Type | Description |
|----------|------|-------------|
| `type` | `RfidEventType` | Event type |
| `data` | `dynamic` | Raw event data map |
| `message` | `String?` | Event message |
| `tags` | `List<RfidTag>?` | List of scanned tags (for `tagRead` events) |
| `triggerPressed` | `bool?` | Trigger state (for `trigger` events) |
| `errorMessage` | `String?` | Error description (for `error` events) |
| `readerName` | `String?` | Reader name (for connection events) |

### RfidStatus

| Property | Type | Description |
|----------|------|-------------|
| `permissionsGranted` | `bool` | Whether permissions are granted |
| `sdkInitialized` | `bool` | Whether SDK is initialized |
| `readerConnected` | `bool` | Whether reader is connected |
| `isReconnecting` | `bool` | Whether reader is reconnecting |
| `readerName` | `String?` | Connected reader name |
| `maxPower` | `int` | Maximum antenna power (default 270) |
| `error` | `String?` | Error message if any |
| `isReady` | `bool` | `true` when permissions + SDK + connected |
| `isInitializing` | `bool` | `true` when initialization is in progress |
| `hasError` | `bool` | `true` when error exists |
| `statusMessage` | `String` | Human-readable status message |
| `shortStatus` | `String` | Short status code (e.g. `READY`, `ERROR`, `CONNECTING`) |

### RfidTag

| Property | Type | Description |
|----------|------|-------------|
| `tagId` | `String` | EPC tag identifier |
| `rssi` | `int` | Signal strength (dBm) |
| `antennaId` | `int` | Antenna that detected the tag |
| `count` | `int` | Number of times tag was read |

---

### Services (`lib/src/services/`)

<details>
<summary><b>ZebraRfidReader</b> - Main service class</summary>

<br>

**Key Features:**

- Auto-initialization (permissions, SDK, reader connection)
- Status polling with automatic start/stop
- Reader connection and configuration
- Tag inventory operations
- Antenna power control
- Event streaming with full logging

</details>

<details>
<summary><b>AppLogger</b> - Logging utility</summary>

<br>

**Features:**

- Multi-level logging (debug, info, warning, error, critical)
- In-memory log storage (max 500 entries)
- Export logs to clipboard
- Real-time log updates via ChangeNotifier

</details>

---

### UI Screens (`lib/src/screens/`)

<details>
<summary><b>LogViewerScreen</b> - Debug log viewer</summary>

<br>

**Features:**

- Color-coded log levels
- Filter by log level
- Auto-scroll toggle
- Copy logs to clipboard
- Clear log history
- Expandable entries with error details

</details>

---

## Event Types

```dart
enum RfidEventType {
  tagRead,           // Tags were scanned
  trigger,           // Hardware trigger pressed/released
  connected,         // Reader connected
  disconnected,      // Reader disconnected
  readerAppeared,    // New reader detected
  readerDisappeared, // Reader removed
  initialized,       // SDK initialized
  reconnecting,      // Reader is reconnecting
  inventoryStarted,  // Inventory scan started
  inventoryStopped,  // Inventory scan stopped
  ready,             // Reader is fully ready
  error,             // Error occurred
  unknown,           // Unknown event
}
```

---

## Requirements

- **Device:** Zebra TC27 or compatible Zebra RFID device with reader antenna
- **Android:** API 26+ (Android 8.0+) with full Android 13+ support
- **Flutter:** 3.3.0+
- **Dart:** 3.0.0+

> **Note:** This plugin requires actual Zebra RFID hardware. It **will not work** on mobile phones, emulators, or simulators. You need a real computer mobile device with a Zebra reader antenna.

---

## Troubleshooting

<details>
<summary><b>Build Error: "Could not find com.zebra.rfid:rfid-api3:2.0.5.238"</b></summary>

<br>

**Solution:** You forgot to add the Maven repository! See the [Android Configuration](#android-configuration) section above.

</details>

<details>
<summary><b>No Readers Found</b></summary>

<br>

**Solutions:**

- Ensure Bluetooth is enabled
- Run on actual Zebra device (not emulator)
- Grant all required permissions
- Restart the device

</details>

<details>
<summary><b>Connection Failed</b></summary>

<br>

**Solutions:**

- Check reader is powered on
- Verify Bluetooth connection
- Ensure no other app is using the reader
- Try disconnecting and reconnecting

</details>

<details>
<summary><b>ProGuard / R8 build errors</b></summary>

<br>

**Solution:** Make sure you created `proguard-rules.pro` and referenced it in your `build.gradle`. See the [Add ProGuard Rules](#4-add-proguard-rules) section above.

</details>

---

## API Documentation

For complete API documentation, visit: [pub.dev/documentation/rfid_zebra_reader](https://pub.dev/documentation/rfid_zebra_reader/latest/)

---

## Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting a PR.

---

## Author

**Jimmy (@devJimmy990)**

- GitHub: [@devJimmy990](https://github.com/devJimmy990)
- Plugin: [rfid_zebra_reader](https://github.com/devJimmy990/rfid_zebra_reader)

---

## Acknowledgments

- Built with Zebra RFID SDK v2.0.5.238
- Designed for Zebra TC27 and compatible devices
- Special thanks to the Flutter community

---

## Support

- **Issues:** [GitHub Issues](https://github.com/devJimmy990/rfid_zebra_reader/issues)
- **Discussions:** [GitHub Discussions](https://github.com/devJimmy990/rfid_zebra_reader/discussions)
- **Documentation:** [pub.dev](https://pub.dev/packages/rfid_zebra_reader)

---

**If this plugin helped you, please star the repo!**

---

<p align="center">
  Made with love by <a href="https://github.com/devJimmy990">devJimmy990</a>
</p>
