# Changelog

## 0.1.1 — 2026-10-04 (preview)

- Restore RDP audio playback on Thor. The inherited setup screen incorrectly
  treated the app as a restricted upstream flavor and forced sound off.
- Repair saved disabled-audio profiles once during upgrade. Later user choices
  remain saved; other upstream app flavors retain their existing behavior.
- Route hardware volume up/down to local media volume instead of remote scroll.
  Right-stick scrolling is unchanged.
- Send lower-screen keyboard keys as physical RDP scancodes so Windows input
  methods can process Telex/VNI. Android IME Unicode input is preserved.
- Widen the lower keyboard's Space key.
- Add Android instrumentation regressions for keyboard encoding, volume versus
  scrolling, and the one-time audio settings migration.

Same application ID and signing certificate as 0.1.0; install over the old APK.
See [the 0.1.1 QA report](docs/KIEM-TRA-0.1.1.md) for test evidence and limitations.

## 0.1.0 — 2026-10-03 (preview)

- First Thor RDP build, based on aRDP / FreeRDP.
- Add a persistent lower-screen PC keyboard and touchpad sharing the upper-screen
  RDP session without taking keyboard focus.
- Add joystick pointer movement and scrolling, A-right/B-left mouse buttons, and
  a saved A/B mapping switch.
- Add sticky Ctrl/Alt/Shift/Win, held mouse buttons, and a release-all action.
- Release held input on session/display lifecycle changes and handle cancelled
  connections and certificate prompts.
- Publish ARM64 APK, source, pinned dependency preparation scripts, and QA notes.

Runtime QA used an Android API 34 x86_64 emulator with two displays and a real
xrdp server. Physical AYN Thor, ARM64 execution, and Windows/NLA remain untested.
