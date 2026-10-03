# Thor RDP build notes

Thor RDP is a GPL-3.0 derivative of iiordanov/remote-desktop-clients at commit
`fd308d8596d95e221b388ff0172d7533cb255f31`. It uses actual RDP through FreeRDP,
not a video-streaming host. Preserve the upstream LICENSE, COPYRIGHT-bVNC,
and per-file license notices when redistributing the app or source.

## Windows build

Requirements: Git, PowerShell, curl.exe, tar.exe, JDK 17, Android SDK Platform 36,
and SDK Build-Tools 35.0.0. The repository wrapper downloads Gradle 8.13.
Dependencies also include Android Gradle Plugin 8.13.2 and Kotlin 2.2.21.
No Android Studio or native compilation is required for the preview build.

```powershell
./thor-prepare.ps1
./thor-build.ps1 -JavaHome C:/path/to/jdk17 -AndroidSdk C:/path/to/android-sdk -Configuration Debug
./thor-build.ps1 -JavaHome C:/path/to/jdk17 -AndroidSdk C:/path/to/android-sdk -Configuration Release
```

Optionally supply `-AndroidNdk C:/path/to/android-ndk-r26d` to strip native debug
symbols and reduce APK size. This does not rebuild the native RDP engine.

The release outputs are unsigned. Use your own Android signing key with the
Android SDK apksigner tool. Never distribute a private keystore or its password.
The application ID is `dev.thor.freerdp`; retaining the same signing key is required
for later upgrades. Outputs include arm64-v8a for Thor, x86_64 for an emulator,
and a universal APK under `freeaRDP-app/build/outputs/apk/`.

## Native backend provenance

The preview uses publisher-provided dependency archive version 17 from:

https://github.com/iiordanov/remote-desktop-clients/releases/download/dependencies/remote-desktop-clients-libs-17.tar.gz

SHA-256: `ec916db7e3d89a112ef4b9167e6a3ebf82d52f918de2e80dc9a6c8e56e51010e`

FreeRDP reports version 2.11.7 and the bundled libcrypto reports OpenSSL 1.1.1w
(11 September 2023). These are upstream's existing prebuilt dependencies, not a
newly updated cryptographic/RDP stack. The repository retains the upstream build
configuration and patches in `remoteClientLib/jni/libs/`, including
`build-deps.sh` and `build-deps.conf`. These recipes are a source-build starting
point, not evidence of an exact rebuild of the older version-17 binaries.
The supplied FreeRDP binary reports OpenH264 enabled and FFmpeg disabled; the
current recipe edits configure OpenH264 disabled and FFmpeg enabled. Native
bit-for-bit reproducibility has not been established. FreeRDP source is at
https://github.com/FreeRDP/FreeRDP/tree/2.11.7 . The Android Java wrapper has
MPL-2.0 notices; FreeRDP core has its own Apache-2.0 license. This preview does
not claim to update or security-audit the supplied native backend.

Runtime component versions, source locations, acknowledgments, and full license
texts are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and
[licenses/SOURCES.md](licenses/SOURCES.md). Keep these with binary distributions.

`thor-prepare.ps1` applies upstream Java-only patches 19 and 20 required by the
newer aRDP sources and removes an obsolete package declaration from the library
manifest. It pins both the archive and SQLCipher AAR by SHA-256. The application
build excludes unrelated SPICE/GStreamer native libraries.

The supplied ARM64 FreeRDP native libraries have 4 KiB ELF segment alignment.
16 KiB Android page-size support has not been established. Device compatibility,
Windows authentication, and actual remote input require device/server testing.

To rebuild native libraries from source rather than use the publisher's binaries,
follow the [preserved upstream README](docs/UPSTREAM-README.md)'s Linux/WSL2 route
and the pinned build-deps.conf.
That path uses NDK r25c for FreeRDP/OpenH264, CMake 3.23.0, and additional Linux
build dependencies; the existing Windows NDK alone is not a drop-in replacement.
