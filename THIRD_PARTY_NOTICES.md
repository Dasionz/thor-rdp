# Third-party notices and native dependency provenance

Thor RDP derives from [iiordanov/remote-desktop-clients](https://github.com/iiordanov/remote-desktop-clients),
commit `fd308d8596d95e221b388ff0172d7533cb255f31`. The app's GPL-3.0 text is
in [LICENSE](LICENSE). Preserve [COPYRIGHT-bVNC](COPYRIGHT-bVNC),
[COPYRIGHT-Opaque](COPYRIGHT-Opaque), and the license and copyright headers in
individual source files. Third-party components retain their own licenses;
this document does not relicense them.

The release's native engines come from the upstream publisher's
[dependency archive version 17](https://github.com/iiordanov/remote-desktop-clients/releases/download/dependencies/remote-desktop-clients-libs-17.tar.gz).
Its SHA-256 is `ec916db7e3d89a112ef4b9167e6a3ebf82d52f918de2e80dc9a6c8e56e51010e`.
The APK's native components are listed below. Complete license copies and their
original source locations are in [licenses/](licenses/SOURCES.md).

| Shipped component | Version/provenance | License and source |
| --- | --- | --- |
| `libfreerdp2.so`, `libfreerdp-client2.so`, `libwinpr2.so` | FreeRDP 2.11.7, reported by the supplied binary | [Apache-2.0 text](licenses/FreeRDP-Apache-2.0.txt); [source tag](https://github.com/FreeRDP/FreeRDP/tree/2.11.7), commit `7f6cc93c21d7f0faad6daacca06f494f29ce882c`. The Android CPU-feature notice is preserved [here](licenses/FreeRDP-Android-cpufeatures-NOTICE.txt). |
| `libfreerdp-android.so` and Android Java JNI wrapper | Android client from the same upstream dependency archive, with upstream Java-only patches 19 and 20 applied by `thor-prepare.ps1` | Android wrapper files carry MPL-2.0 notices, including copyright 2013 Thincast Technologies GmbH, author Martin Fleisz. [MPL-2.0 text](licenses/MPL-2.0.txt); [upstream Android source](https://github.com/FreeRDP/FreeRDP/tree/2.11.7/client/Android). Retain each file's stated license. |
| `libcrypto.so`, `libssl.so` | OpenSSL 1.1.1w, 11 September 2023, reported by the supplied binary | [Full OpenSSL and original SSLeay dual-license text](licenses/OpenSSL-1.1.1w.txt); [source tag](https://github.com/openssl/openssl/tree/OpenSSL_1_1_1w). |
| `libopenh264.so` | OpenH264 2.5.0; configured version and ARM64 version function agree | [BSD-2-Clause text and Cisco copyright](licenses/OpenH264-2.5.0.txt); [source tag](https://github.com/cisco/openh264/tree/v2.5.0). |
| `libc++_shared.so` | From dependency archive 17. Binary compiler metadata identifies Android clang 14.0.7, source revision `4c603efb0cca074e9238af8b4106c30add4418f6` | LLVM's Apache-2.0 with LLVM Exceptions, with retained legacy notices: [libc++](licenses/LLVM-libcxx.txt), [libc++abi](licenses/LLVM-libcxxabi.txt), [libunwind](licenses/LLVM-libunwind.txt). [Android LLVM source revision](https://android.googlesource.com/toolchain/llvm-project/+/4c603efb0cca074e9238af8b4106c30add4418f6). The exact runtime source tree was not independently rebuilt. |
| `libsqlcipher.so` and Android SQLCipher support | `net.zetetic:android-database-sqlcipher:4.5.4`, downloaded as the hash-pinned AAR by `thor-prepare.ps1` | SQLCipher's BSD-style [Android distribution notice](licenses/SQLCipher-Android-4.5.4.txt) and [core notice](licenses/SQLCipher-Core-4.5.4.txt); Android support layer [Apache-2.0](licenses/Apache-2.0.txt). [Android source](https://github.com/sqlcipher/android-database-sqlcipher/tree/v4.5.4), [SQLCipher core source](https://github.com/sqlcipher/sqlcipher/tree/v4.5.4). SQLite is public domain, as described by that upstream project. |
| `libzstd-jni-1.5.7-4.so` | `com.github.luben:zstd-jni:1.5.7-4`, including Zstandard 1.5.7 | [zstd-jni BSD-2-Clause](licenses/zstd-jni-1.5.7-4.txt), [its vendored Zstandard BSD notice](licenses/zstd-jni-vendored-zstd.txt), and [Zstandard 1.5.7 BSD notice](licenses/zstd-1.5.7.txt); [source tag](https://github.com/luben/zstd-jni/tree/v1.5.7-4). |

## Required OpenSSL acknowledgments

This product includes software developed by the OpenSSL Project for use in the
OpenSSL Toolkit (http://www.openssl.org/).

This product includes cryptographic software written by Eric Young
(eay@cryptsoft.com).

This product includes software written by Tim Hudson (tjh@cryptsoft.com).

Additional original attributions for the Android SQLCipher support sources are
preserved in [SQLCipher-Android-NOTICE-EXTRACTS.txt](licenses/SQLCipher-Android-NOTICE-EXTRACTS.txt).
The original SQLite [public-domain dedication](licenses/SQLite-public-domain.txt)
is included separately. Zstandard's xxHash, FSE, and huff0 attributions are
preserved in [zstd-NOTICE-EXTRACTS.txt](licenses/zstd-NOTICE-EXTRACTS.txt).

## Source availability and build scope

The Thor changes and the upstream aRDP application source are included in this
repository. The downloaded version-17 archive also includes the Android FreeRDP
Java wrapper source. `thor-prepare.ps1` verifies the archive, then applies the
published Java patches and manifest adjustment. The archive and upstream source
links above identify the supplied dependencies; original native source-build
recipes and patches remain in `remoteClientLib/jni/libs/`.

These original recipes are not verified to recreate the supplied native binaries
byte for byte. In particular, the supplied FreeRDP binary reports OpenH264 enabled
and FFmpeg disabled while the retained current upstream recipe edits select the
opposite values. See [THOR_BUILD.md](THOR_BUILD.md) for the tested Java/APK build
route and its limitations.

The release APK excludes SPICE and GStreamer native libraries. Their inherited
source files remain in this repository with their original notices; an
[LGPL-2.1 text](licenses/LGPL-2.1.txt) is provided for the source files that state
that license. Other Java/Android dependencies remain identified in the Gradle
build files, with existing notices and dependency metadata preserved. The table
above is the native-runtime inventory, not a claim that every repository file
uses the same license.

Keep this notice and the license copies available alongside release binaries.
