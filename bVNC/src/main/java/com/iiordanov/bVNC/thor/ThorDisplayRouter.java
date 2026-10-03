/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.iiordanov.bVNC.thor;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

/** Route the session before creating its native connection, never reconnect to move a view. */
public final class ThorDisplayRouter {
    private static final String ROUTED = "dev.thor.freerdp.DISPLAY_ROUTED";
    private ThorDisplayRouter() {}

    public static boolean routeSession(Activity activity) {
        if (Build.VERSION.SDK_INT < 26 || activity.getIntent().getBooleanExtra(ROUTED, false)) return false;
        DisplayManager manager = (DisplayManager) activity.getSystemService(Activity.DISPLAY_SERVICE);
        if (manager == null || manager.getDisplays().length < 2) return false;
        Display current = activity.getWindowManager().getDefaultDisplay();
        Display target = manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (target == null || current == null) return false;
        // Display 0 is the Thor's main display. Consider another built-in panel
        // only when Android exposes its type; do not hijack an HDMI/cast session.
        long largest = area(target);
        for (Display display : manager.getDisplays()) {
            if (!display.isValid() || !isInternal(display)) continue;
            long area = area(display);
            if (area > largest) { target = display; largest = area; }
        }
        if (current.getDisplayId() == target.getDisplayId()) return false;
        Intent intent = new Intent(activity.getIntent());
        intent.putExtra(ROUTED, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(target.getDisplayId());
            activity.startActivity(intent, options.toBundle());
            activity.finish();
            return true;
        } catch (RuntimeException error) {
            Log.w("ThorDisplayRouter", "Display routing unavailable; keep this display", error);
            return false;
        }
    }

    private static boolean isInternal(Display display) {
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) return true;
        try {
            return ((Integer) Display.class.getMethod("getType").invoke(display)) == 1;
        } catch (Exception ignored) { return false; }
    }

    private static long area(Display display) {
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        return (long) metrics.widthPixels * metrics.heightPixels;
    }
}
