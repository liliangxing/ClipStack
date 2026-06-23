// IClipboardService.aidl
package com.catchingnow.clip;

import com.catchingnow.clip.IClipboardCallback;

/**
 * Service interface that runs inside the Shizuku server process.
 *
 * <p>Because the Shizuku server runs with shell (UID 2000) privileges, the
 * {@link android.content.ClipboardManager} obtained from within that process
 * is not subject to the Android 10+ / Android 16 background clipboard-read
 * restrictions that affect normal apps. This service registers a
 * {@link android.content.ClipboardManager.OnPrimaryClipChangedListener} on
 * behalf of the app and forwards change events to any registered
 * {@link IClipboardCallback}s.</p>
 *
 * <p>The app binds to this service via
 * {@link rikka.shizuku.Shizuku#bindUserService} and registers a callback to
 * receive real-time clipboard updates.</p>
 */
interface IClipboardService {
    /**
     * Returns the current primary clip text, or null if the clipboard is
     * empty or contains non-text content.
     */
    String getCurrentClip();

    /**
     * Registers a callback that will be notified on every clipboard change.
     */
    void registerCallback(IClipboardCallback callback);

    /**
     * Unregisters a previously registered callback.
     */
    void unregisterCallback(IClipboardCallback callback);

    /**
     * Returns true if the service is actively monitoring the clipboard.
     */
    boolean isMonitoring();

    /**
     * Performs an immediate check of the current clipboard and fires the
     * callback if the content has changed since the last notification.
     * Useful for recovering missed events after the service is bound.
     */
    void checkNow();
}
