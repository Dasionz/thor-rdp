/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.iiordanov.bVNC.thor;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A touch-only keyboard sharing the owner's RDP input stream; never launches an IME. */
public final class ThorInputDeck implements AutoCloseable {
    public interface Actions { void disconnect(); }

    private static final int BACKGROUND = Color.rgb(13, 21, 31);
    private static final int KEY = Color.rgb(34, 48, 65);
    private static final int ACCENT = Color.rgb(33, 143, 135);
    private final Activity activity;
    private final ThorInputController controller;
    private final Actions actions;
    private final DisplayManager displayManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Button> modifierButtons = new HashMap<>();
    private Presentation presentation;
    private TextView statusView;
    private Button swapButton;
    private String status = "Waiting for RDP connection";
    private boolean listening;
    private boolean trackpadShown;
    private boolean closed;
    private int attachedDisplayId = -1;

    public ThorInputDeck(Activity activity, ThorInputController controller, Actions actions) {
        this.activity = activity;
        this.controller = controller;
        this.actions = actions;
        displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
    }

    /** Returns false when Android does not expose a usable second display. */
    public boolean show() {
        if (closed) return false;
        if (presentation != null && presentation.isShowing()) return true;
        if (displayManager == null) return false;
        if (!listening) {
            displayManager.registerDisplayListener(displayListener, handler);
            listening = true;
        }
        int owner = activity.getWindowManager().getDefaultDisplay().getDisplayId();
        List<Display> candidates = new ArrayList<>();
        // CATEGORY_PRESENTATION excludes the second internal panel on some handhelds.
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() != owner && display.isValid() && display.getState() != Display.STATE_OFF) {
                candidates.add(display);
            }
        }
        // Favor a built-in panel and then the smaller screen, rather than a connected TV.
        candidates.sort(Comparator.comparingInt(ThorInputDeck::displayRank).thenComparingLong(ThorInputDeck::displayArea));
        for (Display display : candidates) {
            Presentation next = new Presentation(activity, display) {
                @Override public boolean dispatchKeyEvent(KeyEvent event) {
                    // Normally the owner gets keys because this window is not focusable.
                    // Also handle OEMs that route handheld buttons to the touched display.
                    return controller.handleKeyEvent(event) || super.dispatchKeyEvent(event);
                }

                @Override protected void onCreate(Bundle state) {
                    super.onCreate(state);
                    Window window = getWindow();
                    if (window != null) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                    }
                    setContentView(buildContent(getContext()));
                }
            };
            next.setOnDismissListener(dialog -> {
                if (presentation == dialog) {
                    presentation = null;
                    attachedDisplayId = -1;
                    controller.releaseAll();
                    // Presentation is cancelled by Android when display metrics change.
                    // Recreate it using the display's new configuration, not stale resources.
                    handler.removeCallbacks(restoreDeck);
                    handler.postDelayed(restoreDeck, 200);
                }
            });
            try {
                // Set before show as well, so this window never briefly takes key focus.
                if (next.getWindow() != null) next.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                next.show();
                if (next.getWindow() != null) next.getWindow().setLayout(-1, -1);
                presentation = next;
                attachedDisplayId = display.getDisplayId();
                return true;
            } catch (WindowManager.InvalidDisplayException | SecurityException exception) {
                next.dismiss();
            }
        }
        return false;
    }

    private static int displayRank(Display display) {
        String name = display.getName().toLowerCase(java.util.Locale.ROOT);
        return display.getDisplayId() == Display.DEFAULT_DISPLAY || name.contains("built-in")
                || name.contains("internal") || name.contains("thor") ? 0 : 1;
    }

    private static long displayArea(Display display) {
        Point size = new Point();
        display.getRealSize(size);
        return (long) size.x * size.y;
    }

    public int getDisplayId() { return attachedDisplayId; }

    /** The owner may add this as an explicit fallback drawer when only one display exists. */
    public View getViewForSingleScreen() { return buildContent(activity); }

    public void refreshStatus(String message) {
        status = message == null ? "RDP controls" : message;
        if (statusView != null) statusView.setText(status);
        refreshModifiers();
    }

    private final Runnable restoreDeck = new Runnable() {
        @Override public void run() {
            if (!closed && controller.isActive() && !activity.isFinishing() && !activity.isDestroyed()) show();
        }
    };

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) {
            if (presentation == null && !closed && controller.isActive()) show();
        }
        @Override public void onDisplayRemoved(int id) {
            if (id == attachedDisplayId) dismissPresentation();
        }
        @Override public void onDisplayChanged(int id) {
            if (id == attachedDisplayId && displayManager.getDisplay(id) == null) dismissPresentation();
        }
    };

    private View buildContent(Context context) {
        modifierButtons.clear();
        LinearLayout root = column(context);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(context, 7), dp(context, 4), dp(context, 7), dp(context, 6));
        root.setFocusable(false);
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        statusView = new TextView(context);
        statusView.setText(status);
        statusView.setTextColor(Color.rgb(180, 204, 222));
        statusView.setTextSize(12);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setSingleLine(true);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, dp(context, 24)));

        LinearLayout toolbar = row(context);
        LinearLayout content = column(context);
        Button mode = button(context, "Touchpad");
        mode.setOnClickListener(v -> {
            controller.releaseAll();
            trackpadShown = !trackpadShown;
            mode.setText(trackpadShown ? "Keyboard" : "Touchpad");
            fillInputContent(content);
        });
        addWeighted(toolbar, mode, 1);
        swapButton = button(context, "A/B normal");
        swapButton.setOnClickListener(v -> {
            controller.setButtonsSwapped(!controller.isButtonsSwapped());
            refreshModifiers();
        });
        addWeighted(toolbar, swapButton, 1.1f);
        Button release = button(context, "Release keys");
        release.setOnClickListener(v -> { controller.releaseAll(); refreshModifiers(); });
        addWeighted(toolbar, release, 1.15f);
        Button disconnect = button(context, "Disconnect");
        disconnect.setOnClickListener(v -> { controller.releaseAll(); actions.disconnect(); });
        addWeighted(toolbar, disconnect, 1.1f);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(context, 38)));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        fillInputContent(content);
        refreshModifiers();
        return root;
    }

    private void fillInputContent(LinearLayout content) {
        content.removeAllViews();
        modifierButtons.clear();
        if (trackpadShown) {
            addTrackpad(content);
            return;
        }
        addKeys(content,
                new String[]{"Esc", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"},
                new int[]{KeyEvent.KEYCODE_ESCAPE, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142});
        addKeys(content,
                new String[]{"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "−", "=", "Bksp"},
                new int[]{68, 8, 9, 10, 11, 12, 13, 14, 15, 16, 7, 69, 70, KeyEvent.KEYCODE_DEL});
        addKeys(content,
                new String[]{"Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\"},
                new int[]{61, 45, 51, 33, 46, 48, 53, 49, 37, 43, 44, 71, 72, 73});
        addKeys(content,
                new String[]{"Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter"},
                new int[]{115, 29, 47, 32, 34, 35, 36, 38, 39, 40, 74, 75, 66});
        addKeys(content,
                new String[]{"Shift", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "↑", "Del"},
                new int[]{59, 54, 52, 31, 50, 30, 42, 41, 55, 56, 76, 19, 112});
        addKeys(content,
                new String[]{"Ctrl", "Win", "Alt", "Space", "Home", "End", "PgUp", "PgDn", "←", "↓", "→"},
                new int[]{113, 117, 57, 62, 122, 123, 92, 93, 21, 20, 22});
        TextView hint = new TextView(content.getContext());
        hint.setText("Left stick: cursor   ·   Right stick: scroll   ·   Ctrl / Alt / Shift / Win stay held until tapped again");
        hint.setTextColor(Color.rgb(154, 177, 192));
        hint.setTextSize(10);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(content.getContext(), 23)));
        refreshModifiers();
    }

    private void addKeys(LinearLayout parent, String[] labels, int[] codes) {
        LinearLayout row = row(parent.getContext());
        for (int i = 0; i < labels.length; i++) {
            final int code = codes[i];
            Button key = button(parent.getContext(), labels[i]);
            boolean modifier = code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_ALT_LEFT
                    || code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_META_LEFT;
            if (modifier) {
                modifierButtons.put(code, key);
                key.setOnClickListener(v -> { controller.toggleModifier(code); refreshModifiers(); });
            } else {
                key.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        v.setPressed(true);
                        controller.keyDown(code);
                        return true;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        controller.keyUp(code);
                        v.setPressed(false);
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) v.performClick();
                        return true;
                    }
                    return true;
                });
                key.setOnClickListener(v -> { /* Input is paired explicitly on touch down/up. */ });
            }
            float weight = code == KeyEvent.KEYCODE_SPACE ? 4.8f : labels[i].length() > 2 ? 1.4f : 1f;
            addWeighted(row, key, weight);
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void addTrackpad(LinearLayout parent) {
        Context context = parent.getContext();
        TextView pad = new TextView(context);
        pad.setText("TOUCHPAD\n\nMove one finger · Tap to click\nTwo fingers to scroll");
        pad.setTextColor(Color.rgb(171, 199, 215));
        pad.setTextSize(16);
        pad.setGravity(Gravity.CENTER);
        pad.setBackground(background(context, Color.rgb(23, 37, 51)));
        pad.setOnTouchListener(new View.OnTouchListener() {
            float x, y, startX, startY, scrollRemainder;
            long started;
            boolean moved, multiple;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x = startX = event.getX(); y = startY = event.getY();
                        started = event.getEventTime(); moved = multiple = false; scrollRemainder = 0;
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        multiple = true; x = event.getX(); y = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - x, dy = event.getY() - y;
                        if (Math.hypot(event.getX() - startX, event.getY() - startY) > dp(context, 5)) moved = true;
                        if (event.getPointerCount() > 1) {
                            scrollRemainder -= dy / dp(context, 30);
                            int steps = (int) scrollRemainder;
                            if (steps != 0) { controller.scroll(steps); scrollRemainder -= steps; }
                        } else controller.moveRelative(dx * 1.5f, dy * 1.5f);
                        x = event.getX(); y = event.getY();
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        int remaining = event.getActionIndex() == 0 ? 1 : 0;
                        if (remaining < event.getPointerCount()) { x = event.getX(remaining); y = event.getY(remaining); }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!moved && event.getEventTime() - started < 350) {
                            controller.click(multiple ? ThorInputController.BUTTON_RIGHT : ThorInputController.BUTTON_LEFT);
                            view.performClick();
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        controller.releaseAll();
                        break;
                }
                return true;
            }
        });
        parent.addView(pad, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = row(context);
        addMouseButton(buttons, "Left click / hold", ThorInputController.BUTTON_LEFT);
        addMouseButton(buttons, "Right click / hold", ThorInputController.BUTTON_RIGHT);
        parent.addView(buttons, new LinearLayout.LayoutParams(-1, dp(context, 48)));
    }

    private void addMouseButton(LinearLayout row, String label, int button) {
        Button view = button(row.getContext(), label);
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.setPressed(true); controller.setMouseButton(button, true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false); controller.setMouseButton(button, false);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) v.performClick();
            }
            return true;
        });
        addWeighted(row, view, 1);
    }

    private void refreshModifiers() {
        for (Map.Entry<Integer, Button> item : modifierButtons.entrySet()) {
            item.getValue().setBackground(background(item.getValue().getContext(), controller.isKeyPressed(item.getKey()) ? ACCENT : KEY));
        }
        if (swapButton != null) swapButton.setText(controller.isButtonsSwapped() ? "A:left · B:right" : "A:right · B:left");
    }

    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private static Button button(Context context, String label) {
        Button view = new Button(context);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        view.setAllCaps(false);
        view.setPadding(0, 0, 0, 0);
        view.setMinWidth(0); view.setMinimumWidth(0);
        view.setMinHeight(0); view.setMinimumHeight(0);
        view.setFocusable(false); view.setFocusableInTouchMode(false);
        view.setBackground(background(context, KEY));
        return view;
    }

    private static GradientDrawable background(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, 5));
        return drawable;
    }

    private static void addWeighted(LinearLayout row, View view, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, weight);
        params.setMargins(dp(row.getContext(), 2), dp(row.getContext(), 2), dp(row.getContext(), 2), dp(row.getContext(), 2));
        row.addView(view, params);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void dismissPresentation() {
        Presentation old = presentation;
        presentation = null;
        attachedDisplayId = -1;
        if (old != null) old.dismiss();
        controller.releaseAll();
    }

    public void dismiss() {
        handler.removeCallbacks(restoreDeck);
        dismissPresentation();
        if (listening && displayManager != null) displayManager.unregisterDisplayListener(displayListener);
        listening = false;
    }

    @Override public void close() { closed = true; dismiss(); }
}
