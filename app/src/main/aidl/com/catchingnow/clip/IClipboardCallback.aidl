// IClipboardCallback.aidl
package com.catchingnow.clip;

/**
 * Callback interface used by ShizukuClipboardService to notify the app
 * when the system clipboard content changes. The callback is invoked from
 * the Shizuku server process (running with shell / UID 2000 privileges),
 * which can observe clipboard changes even when the app is in the
 * background on Android 10+ and especially Android 16 where normal
 * background apps are blocked from reading the clipboard.
 */
interface IClipboardCallback {
    /**
     * Called when the system primary clip changes.
     *
     * @param text the new clipboard text, or null if the clip is non-text
     */
    void onClipboardChanged(String text);
}
