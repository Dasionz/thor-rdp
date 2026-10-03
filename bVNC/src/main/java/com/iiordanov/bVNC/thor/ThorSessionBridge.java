/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.iiordanov.bVNC.thor;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.iiordanov.bVNC.protocol.RemoteConnection;
import com.iiordanov.bVNC.input.RemoteRdpKeyboard;
import com.undatech.opaque.RdpCommunicator;
import com.undatech.opaque.input.RemoteKeyboard;
import com.undatech.opaque.input.RemotePointer;

import java.util.function.Supplier;

/** Connects both local screens to one authenticated RDP connection. */
public final class ThorSessionBridge implements AutoCloseable {
    private final Supplier<RemoteConnection> connection;
    private final ThorInputController controller;
    private final ThorInputDeck deck;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean resumed;
    private boolean closed;
    private boolean lastConnected;
    private final Runnable statusTick = new Runnable() {
        @Override public void run() {
            if (closed || !resumed) return;
            boolean connected = connected();
            if (lastConnected && !connected) controller.releaseAll();
            lastConnected = connected;
            deck.refreshStatus(connected ? "RDP connected" : "Waiting for RDP connection");
            ui.postDelayed(this, 350);
        }
    };

    public ThorSessionBridge(Activity activity, Supplier<RemoteConnection> connection,
                             Runnable disconnect) {
        this.connection = connection;
        controller = new ThorInputController(activity, new ThorInputController.Sink() {
            @Override public boolean isConnected() { return connected(); }
            @Override public int getCursorX() {
                RemotePointer p = pointer(); return p == null ? 0 : p.getX();
            }
            @Override public int getCursorY() {
                RemotePointer p = pointer(); return p == null ? 0 : p.getY();
            }
            @Override public int getRemoteWidth() {
                RemoteConnection c = connection.get();
                return connected() ? Math.max(1, c.getRfbConn().framebufferWidth()) : 1;
            }
            @Override public int getRemoteHeight() {
                RemoteConnection c = connection.get();
                return connected() ? Math.max(1, c.getRfbConn().framebufferHeight()) : 1;
            }
            @Override public void moveMouse(int x, int y) {
                if (connected() && pointer() != null) pointer().moveMouse(x, y, deckMeta());
            }
            @Override public void mouseButton(int button, boolean down) {
                if (!connected()) return;
                // RDP button-up must identify the released button. Sending the
                // native flags also preserves an independently held other button.
                int flag = button == ThorInputController.BUTTON_RIGHT ? 0x2000 : 0x1000;
                connection.get().getRfbConn().writePointerEvent(
                        getCursorX(), getCursorY(), deckMeta(), flag | (down ? 0x8000 : 0), false);
            }
            @Override public void scroll(int steps) {
                if (!connected()) return;
                int flag = steps > 0 ? 0x0200 | 0x0078 : 0x0200 | 0x0100 | 0x0088;
                for (int i = 0; i < Math.min(8, Math.abs(steps)); i++) {
                    connection.get().getRfbConn().writePointerEvent(
                            getCursorX(), getCursorY(), deckMeta(), flag, false);
                }
            }
            @Override public void key(int code, boolean down, int metaState) {
                if (!connected() || connection.get().getKeyboard() == null) return;
                long now = SystemClock.uptimeMillis();
                KeyEvent event = new KeyEvent(now, now,
                        down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                        code, 0, KeyEvent.normalizeMetaState(metaState),
                        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
                if (connection.get().getKeyboard() instanceof RemoteRdpKeyboard keyboard) {
                    keyboard.processDeckKeyEvent(code, event, remoteMeta(metaState));
                }
            }
            @Override public void text(String text) {
                if (!connected() || text == null) return;
                if (connection.get().getRfbConn() instanceof RdpCommunicator rdp) {
                    // RDP Unicode input carries UTF-16 code units, including a
                    // surrogate pair for characters outside the basic plane.
                    for (int i = 0; i < text.length(); i++) {
                        rdp.processUnicodeKey(text.charAt(i), true, true);
                        rdp.processUnicodeKey(text.charAt(i), false, true);
                    }
                }
            }
        });
        deck = new ThorInputDeck(activity, controller, disconnect::run);
    }

    private static int remoteMeta(int state) {
        int meta = 0;
        if ((state & KeyEvent.META_SHIFT_MASK) != 0) meta |= RemoteKeyboard.SHIFT_MASK;
        if ((state & KeyEvent.META_CTRL_MASK) != 0) meta |= RemoteKeyboard.CTRL_MASK;
        if ((state & KeyEvent.META_ALT_MASK) != 0) meta |= RemoteKeyboard.ALT_MASK;
        if ((state & KeyEvent.META_META_MASK) != 0) meta |= RemoteKeyboard.SUPER_MASK;
        return meta;
    }

    private int deckMeta() { return remoteMeta(controller.getMetaState()); }

    private RemotePointer pointer() {
        RemoteConnection c = connection.get();
        return c == null ? null : c.getPointer();
    }

    private boolean connected() {
        RemoteConnection c = connection.get();
        return !closed && c != null && c.maintainConnection && c.getRfbConn() != null
                && c.getRfbConn().isInNormalProtocol();
    }

    public void resume() {
        if (closed) return;
        resumed = true;
        controller.setActive(true);
        deck.show();
        ui.removeCallbacks(statusTick);
        ui.post(statusTick);
    }

    public void pause() {
        resumed = false;
        controller.setActive(false);
        deck.dismiss();
        ui.removeCallbacks(statusTick);
    }

    public boolean key(KeyEvent event) {
        return resumed && controller.handleKeyEvent(event);
    }

    public boolean motion(MotionEvent event) {
        return resumed && controller.handleGenericMotionEvent(event);
    }

    public boolean showDeck() { return !closed && deck.show(); }

    public void releaseAll() { controller.releaseAll(); }

    @Override public void close() {
        if (closed) return;
        // Release the remote keys while the connection is still available.
        pause();
        controller.close();
        deck.close();
        closed = true;
    }
}
