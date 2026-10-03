# Changelog

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
