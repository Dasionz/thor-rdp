/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.iiordanov.bVNC;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.iiordanov.bVNC.input.RemoteRdpKeyboard;
import com.iiordanov.bVNC.thor.ThorInputController;
import com.undatech.opaque.Viewable;
import com.undatech.opaque.input.RdpKeyboardMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runs on an isolated Android test device; restores the media volume it changes. */
public class ThorInputRegressionTest extends InstrumentationTestCase {
    private static KeyEvent key(int action, int code, int meta, int repeat) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, action, code, repeat, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }

    private static final class KeyRecorder implements RdpKeyboardMapper.KeyProcessingListener {
        final List<String> events = new ArrayList<>();
        @Override public void processVirtualKey(int code, boolean down) { events.add("V:" + code + ":" + down); }
        @Override public void processUnicodeKey(int code, boolean down, boolean suppress) { events.add("U:" + code + ":" + down); }
        @Override public void switchKeyboard(int type) { }
        @Override public void modifiersChanged() { }
    }

    private RdpKeyboardMapper mapper(RemoteRdpKeyboard keyboard, String fieldName, KeyRecorder recorder) throws Exception {
        Field field = RemoteRdpKeyboard.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        RdpKeyboardMapper mapper = (RdpKeyboardMapper) field.get(keyboard);
        mapper.reset(recorder);
        return mapper;
    }

    public void testDeckPhysicalKeysAndImeUnicodeStayIndependent() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        // Constructor only needs the context. Recording the actual production mapper
        // avoids a native/server dependency; connected wire behavior is checked separately.
        Viewable view = (Viewable) Proxy.newProxyInstance(Viewable.class.getClassLoader(),
                new Class<?>[]{Viewable.class}, (proxy, method, args) -> context);
        RemoteRdpKeyboard keyboard = new RemoteRdpKeyboard(null, view, null,
                new Handler(Looper.getMainLooper()), false, true);
        KeyRecorder deckEvents = new KeyRecorder();
        KeyRecorder imeEvents = new KeyRecorder();
        RdpKeyboardMapper deck = mapper(keyboard, "deckKeyboardMapper", deckEvents);
        RdpKeyboardMapper ime = mapper(keyboard, "keyboardMapper", imeEvents);

        // Telex input must reach Windows as real letter down/up events, not VK_PACKET.
        for (char letter : "tieengs vieetj".toCharArray()) {
            int code = letter == ' ' ? KeyEvent.KEYCODE_SPACE : KeyEvent.KEYCODE_A + letter - 'a';
            int vk = letter == ' ' ? 0x20 : Character.toUpperCase(letter);
            deckEvents.events.clear();
            assertTrue(deck.processAndroidKeyEvent(key(KeyEvent.ACTION_DOWN, code, 0, 0), false));
            assertTrue(deck.processAndroidKeyEvent(key(KeyEvent.ACTION_UP, code, 0, 0), false));
            assertEquals(Arrays.asList("V:" + vk + ":true", "V:" + vk + ":false"), deckEvents.events);
        }

        // Every remaining printable/PC key on the deck must preserve its physical identity,
        // including with Shift held. The host keyboard layout produces the punctuation.
        int[][] keys = {
                {68, 0xC0}, {8, 0x31}, {9, 0x32}, {10, 0x33}, {11, 0x34}, {12, 0x35},
                {13, 0x36}, {14, 0x37}, {15, 0x38}, {16, 0x39}, {7, 0x30}, {69, 0xBD}, {70, 0xBB},
                {71, 0xDB}, {72, 0xDD}, {73, 0xDC}, {74, 0xBA}, {75, 0xDE}, {55, 0xBC},
                {56, 0xBE}, {76, 0xBF}, {KeyEvent.KEYCODE_DEL, 0x08}, {66, 0x0D}, {61, 0x09},
                {59, 0xA0}, {113, 0xA2}, {57, 0xA4}, {117, 0x15B}, {115, 0x14}, {111, 0x1B},
                {122, 0x124}, {123, 0x123}, {92, 0x121}, {93, 0x122}, {19, 0x126},
                {20, 0x128}, {21, 0x125}, {22, 0x127}, {112, 0x12E}
        };
        for (int[] pair : keys) {
            for (int meta : new int[]{0, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON}) {
                deckEvents.events.clear();
                assertTrue(deck.processAndroidKeyEvent(key(KeyEvent.ACTION_DOWN, pair[0], meta, 0), false));
                assertTrue(deck.processAndroidKeyEvent(key(KeyEvent.ACTION_UP, pair[0], meta, 0), false));
                assertEquals("Android key " + pair[0],
                        Arrays.asList("V:" + pair[1] + ":true", "V:" + pair[1] + ":false"), deckEvents.events);
            }
        }
        for (int code = KeyEvent.KEYCODE_A; code <= KeyEvent.KEYCODE_Z; code++) {
            deckEvents.events.clear();
            deck.processAndroidKeyEvent(key(KeyEvent.ACTION_DOWN, code, KeyEvent.META_CTRL_ON, 0), false);
            deck.processAndroidKeyEvent(key(KeyEvent.ACTION_UP, code, KeyEvent.META_CTRL_ON, 0), false);
            int vk = 0x41 + code - KeyEvent.KEYCODE_A;
            assertEquals(Arrays.asList("V:" + vk + ":true", "V:" + vk + ":false"), deckEvents.events);
        }
        for (int code = KeyEvent.KEYCODE_F1; code <= KeyEvent.KEYCODE_F12; code++) {
            deckEvents.events.clear();
            deck.processAndroidKeyEvent(key(KeyEvent.ACTION_DOWN, code, 0, 0), false);
            deck.processAndroidKeyEvent(key(KeyEvent.ACTION_UP, code, 0, 0), false);
            int vk = 0x70 + code - KeyEvent.KEYCODE_F1;
            assertEquals(Arrays.asList("V:" + vk + ":true", "V:" + vk + ":false"), deckEvents.events);
        }
        ime.processAndroidKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, 0), false);
        ime.processAndroidKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, 0), false);
        assertEquals(Arrays.asList("U:97:true", "U:97:false"), imeEvents.events);
        imeEvents.events.clear();
        ime.processAndroidKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), "\u1ebf", KeyCharacterMap.FULL, 0), false);
        assertEquals(Arrays.asList("U:7871:true", "U:7871:false"), imeEvents.events);
    }

    private static final class Sink implements ThorInputController.Sink {
        boolean connected;
        volatile int scrolls;
        int otherRemoteEvents;
        @Override public boolean isConnected() { return connected; }
        @Override public int getCursorX() { return 100; }
        @Override public int getCursorY() { return 100; }
        @Override public int getRemoteWidth() { return 1280; }
        @Override public int getRemoteHeight() { return 720; }
        @Override public void moveMouse(int x, int y) { otherRemoteEvents++; }
        @Override public void mouseButton(int button, boolean down) { otherRemoteEvents++; }
        @Override public void scroll(int steps) { scrolls += steps; }
        @Override public void key(int code, boolean down, int meta) { otherRemoteEvents++; }
        @Override public void text(String text) { otherRemoteEvents++; }
    }

    public void testVolumeIsLocalBeforeAndAfterConnectionAndRightStickStillScrolls() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int stream = AudioManager.STREAM_MUSIC;
        int original = audio.getStreamVolume(stream);
        int base = Math.max(2, audio.getStreamMaxVolume(stream) / 2);
        assertFalse("Run this regression on a device with adjustable media volume", audio.isVolumeFixed());
        Sink sink = new Sink();
        ThorInputController controller = new ThorInputController(context, sink);
        try {
            for (boolean connected : new boolean[]{false, true}) {
                audio.setStreamVolume(stream, base, 0);
                getInstrumentation().runOnMainSync(() -> { sink.connected = connected; controller.setActive(true); });
                getInstrumentation().runOnMainSync(() -> assertTrue(controller.handleKeyEvent(
                        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0, 0))));
                assertEquals(base + 1, audio.getStreamVolume(stream));
                getInstrumentation().runOnMainSync(() -> assertTrue(controller.handleKeyEvent(
                        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0, 1))));
                assertEquals(base + 2, audio.getStreamVolume(stream));
                getInstrumentation().runOnMainSync(() -> assertTrue(controller.handleKeyEvent(
                        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0, 0))));
                assertEquals("Key-up must not change volume a second time", base + 2, audio.getStreamVolume(stream));
                getInstrumentation().runOnMainSync(() -> assertTrue(controller.handleKeyEvent(
                        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0, 0))));
                getInstrumentation().runOnMainSync(() -> assertTrue(controller.handleKeyEvent(
                        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0, 0))));
                assertEquals(base + 1, audio.getStreamVolume(stream));
                assertEquals(0, sink.scrolls);
                assertEquals(0, sink.otherRemoteEvents);
            }
            MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
            pointer.id = 0;
            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.setAxisValue(MotionEvent.AXIS_RZ, -1f);
            // Device -1 is Android's virtual keyboard, which does not advertise RZ.
            // Populate the controller's documented RY fallback as well.
            coords.setAxisValue(MotionEvent.AXIS_RY, -1f);
            long now = SystemClock.uptimeMillis();
            MotionEvent stick = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1,
                    new MotionEvent.PointerProperties[]{pointer}, new MotionEvent.PointerCoords[]{coords},
                    0, 0, 1f, 1f, -1, 0, InputDevice.SOURCE_JOYSTICK, 0);
            try {
                getInstrumentation().runOnMainSync(() -> assertTrue(controller.handleGenericMotionEvent(stick)));
                SystemClock.sleep(350);
                getInstrumentation().runOnMainSync(controller::releaseAll);
                assertTrue("The right stick must still produce remote wheel events", sink.scrolls > 0);
                assertEquals(0, sink.otherRemoteEvents);
                assertEquals(base + 1, audio.getStreamVolume(stream));
            } finally { stick.recycle(); }
        } finally {
            getInstrumentation().runOnMainSync(controller::close);
            audio.setStreamVolume(stream, original, 0);
        }
    }
}
