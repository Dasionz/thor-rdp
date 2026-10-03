/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.iiordanov.bVNC.thor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Input shared by the desktop activity and its touch-only control deck. */
public final class ThorInputController implements AutoCloseable {
    public static final int BUTTON_LEFT = 1;
    public static final int BUTTON_RIGHT = 2;

    public interface Sink {
        boolean isConnected();
        int getCursorX();
        int getCursorY();
        int getRemoteWidth();
        int getRemoteHeight();
        void moveMouse(int x, int y);
        void mouseButton(int button, boolean down);
        void scroll(int steps);
        void key(int androidKeyCode, boolean down, int metaState);
        void text(String text);
    }

    private final Sink sink;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Set<Integer> pressedButtons = new HashSet<>();
    private final Map<Integer, Set<String>> buttonOwners = new HashMap<>();
    private final Set<Integer> heldGamepadKeys = new HashSet<>();
    private boolean active;
    private boolean closed;
    private boolean buttonsSwapped;
    private float stickX, stickY, scrollAxis;
    private float fractionalX, fractionalY, fractionalScroll;
    private long previousFrame;
    private boolean frameScheduled;

    public ThorInputController(Context context, Sink sink) {
        this.sink = sink;
        preferences = context.getSharedPreferences("thor_input", Context.MODE_PRIVATE);
        buttonsSwapped = preferences.getBoolean("swap_ab", false);
    }

    public boolean isConnected() { return !closed && sink.isConnected(); }
    public boolean isActive() { return active && !closed; }
    public boolean isButtonsSwapped() { return buttonsSwapped; }

    public void setButtonsSwapped(boolean swapped) {
        releaseMouseButtons();
        heldGamepadKeys.clear();
        buttonsSwapped = swapped;
        preferences.edit().putBoolean("swap_ab", swapped).apply();
    }

    /** Pause input when the owner activity is no longer visible. */
    public void setActive(boolean value) {
        if (!value) releaseAll();
        active = value && !closed;
        if (active) scheduleFrame();
    }

    private boolean canSend() { return active && isConnected(); }

    public boolean handleGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            releaseAll();
            return true;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        if (!canSend()) return true;
        stickX = axis(event, MotionEvent.AXIS_X);
        stickY = axis(event, MotionEvent.AXIS_Y);
        scrollAxis = axis(event, MotionEvent.AXIS_RZ);
        // Some Android controllers expose the right stick as RX/RY instead of Z/RZ.
        if (event.getDevice() != null
                && event.getDevice().getMotionRange(MotionEvent.AXIS_RZ, event.getSource()) == null) {
            scrollAxis = axis(event, MotionEvent.AXIS_RY);
        }
        scheduleFrame();
        return true;
    }

    private float axis(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        InputDevice.MotionRange range = device == null ? null : device.getMotionRange(axis, event.getSource());
        float flat = Math.max(0.16f, range == null ? 0f : range.getFlat());
        float value = event.getAxisValue(axis);
        float magnitude = Math.abs(value);
        if (magnitude <= flat || flat >= 1f) return 0f;
        float normalized = Math.min(1f, (magnitude - flat) / (1f - flat));
        return Math.copySign(normalized * normalized, value);
    }

    public boolean handleKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean gamepad = (event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        if (!gamepad && event.getDevice() != null) {
            gamepad = (event.getDevice().getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        }
        if (!gamepad) return false;
        // Unassigned gamepad buttons must not leak into the desktop keyboard mapper.
        if (code != KeyEvent.KEYCODE_BUTTON_A && code != KeyEvent.KEYCODE_BUTTON_B) {
            return KeyEvent.isGamepadButton(code)
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_CENTER;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (canSend() && heldGamepadKeys.add(code)) setMouseButtonForOwner(buttonFor(code), true, "gamepad:" + code);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (heldGamepadKeys.remove(code)) setMouseButtonForOwner(buttonFor(code), false, "gamepad:" + code);
        }
        return true;
    }

    private int buttonFor(int keyCode) {
        boolean a = keyCode == KeyEvent.KEYCODE_BUTTON_A;
        return a != buttonsSwapped ? BUTTON_RIGHT : BUTTON_LEFT;
    }

    private void scheduleFrame() {
        if (!frameScheduled && canSend() && (stickX != 0 || stickY != 0 || scrollAxis != 0)) {
            previousFrame = SystemClock.uptimeMillis();
            frameScheduled = true;
            handler.postDelayed(frame, 16);
        }
    }

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            frameScheduled = false;
            if (!canSend()) { releaseAll(); return; }
            long now = SystemClock.uptimeMillis();
            float seconds = Math.max(0.001f, Math.min(0.05f, (now - previousFrame) / 1000f));
            previousFrame = now;
            float speed = Math.max(650f, sink.getRemoteWidth() * 0.6f);
            moveRelative(stickX * speed * seconds, stickY * speed * seconds);
            fractionalScroll += -scrollAxis * 12f * seconds;
            int steps = (int) fractionalScroll;
            if (steps != 0) {
                scroll(steps);
                fractionalScroll -= steps;
            }
            if (stickX != 0 || stickY != 0 || scrollAxis != 0) {
                frameScheduled = true;
                handler.postDelayed(this, 16);
            }
        }
    };

    public void moveRelative(float dx, float dy) {
        if (!canSend()) return;
        fractionalX += dx;
        fractionalY += dy;
        int xDelta = (int) fractionalX;
        int yDelta = (int) fractionalY;
        fractionalX -= xDelta;
        fractionalY -= yDelta;
        if (xDelta == 0 && yDelta == 0) return;
        int x = Math.max(0, Math.min(Math.max(0, sink.getRemoteWidth() - 1), sink.getCursorX() + xDelta));
        int y = Math.max(0, Math.min(Math.max(0, sink.getRemoteHeight() - 1), sink.getCursorY() + yDelta));
        sink.moveMouse(x, y);
    }

    public void setMouseButton(int button, boolean down) {
        setMouseButtonForOwner(button, down, "touch-button");
    }

    private void setMouseButtonForOwner(int button, boolean down, String owner) {
        if (down) {
            if (!canSend()) return;
            Set<String> owners = buttonOwners.get(button);
            if (owners == null) { owners = new HashSet<>(); buttonOwners.put(button, owners); }
            if (!owners.add(owner)) return;
            if (canSend() && pressedButtons.add(button)) sink.mouseButton(button, true);
        } else {
            Set<String> owners = buttonOwners.get(button);
            if (owners == null || !owners.remove(owner) || !owners.isEmpty()) return;
            buttonOwners.remove(button);
            if (pressedButtons.remove(button) && isConnected()) sink.mouseButton(button, false);
        }
    }

    public void click(int button) {
        setMouseButtonForOwner(button, true, "tap");
        setMouseButtonForOwner(button, false, "tap");
    }

    public void scroll(int steps) {
        if (canSend() && steps != 0) sink.scroll(steps);
    }

    public int getMetaState() {
        int meta = 0;
        if (pressedKeys.contains(KeyEvent.KEYCODE_SHIFT_LEFT)) meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (pressedKeys.contains(KeyEvent.KEYCODE_CTRL_LEFT)) meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (pressedKeys.contains(KeyEvent.KEYCODE_ALT_LEFT)) meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (pressedKeys.contains(KeyEvent.KEYCODE_META_LEFT)) meta |= KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
        return meta;
    }

    public boolean isKeyPressed(int code) { return pressedKeys.contains(code); }

    public void keyDown(int code) {
        if (canSend() && pressedKeys.add(code)) sink.key(code, true, getMetaState());
    }

    public void keyUp(int code) {
        int meta = getMetaState();
        if (pressedKeys.remove(code) && isConnected()) sink.key(code, false, meta);
    }

    public void tapKey(int code) { keyDown(code); keyUp(code); }

    public void toggleModifier(int code) {
        if (isKeyPressed(code)) keyUp(code); else keyDown(code);
    }

    public void sendText(String text) {
        if (canSend() && text != null && !text.isEmpty()) sink.text(text);
    }

    private void releaseMouseButtons() {
        if (isConnected()) {
            for (int button : pressedButtons) sink.mouseButton(button, false);
        }
        pressedButtons.clear();
        buttonOwners.clear();
    }

    /** Must run on focus loss, disconnect, display removal, and activity destruction. */
    public void releaseAll() {
        handler.removeCallbacks(frame);
        frameScheduled = false;
        stickX = stickY = scrollAxis = fractionalX = fractionalY = fractionalScroll = 0;
        heldGamepadKeys.clear();
        releaseMouseButtons();
        for (int code : new HashSet<>(pressedKeys)) keyUp(code);
    }

    @Override public void close() {
        releaseAll();
        active = false;
        closed = true;
    }
}
