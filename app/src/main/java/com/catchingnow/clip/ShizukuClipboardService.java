package com.catchingnow.clip;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link IClipboardService} that runs inside the Shizuku
 * server process.
 *
 * <p>The Shizuku server runs with shell (UID 2000) privileges. On Android 10+
 * and especially Android 16, normal background apps cannot read the clipboard,
 * but the shell user can. By registering a
 * {@link ClipboardManager.OnPrimaryClipChangedListener} from within this
 * process, we receive reliable clipboard-change events regardless of whether
 * the app is in the foreground.</p>
 *
 * <p>This class is instantiated by the Shizuku framework via
 * {@link rikka.shizuku.Shizuku#bindUserService}. The constructor must accept
 * a {@link Context} (the Shizuku process context).</p>
 */
public class ShizukuClipboardService extends IClipboardService.Stub {

    private static final String TAG = "ClipStack/ShizukuSvc";

    private final ClipboardManager clipboardManager;
    private final List<IClipboardCallback> callbacks = new ArrayList<>();
    private final ClipboardManager.OnPrimaryClipChangedListener listener;
    private volatile boolean monitoring = false;
    private String lastNotifiedText = null;

    public ShizukuClipboardService(Context context) {
        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        listener = new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                Log.d(TAG, "onPrimaryClipChanged (Shizuku process)");
                notifyCallbacks();
            }
        };
        Log.i(TAG, "ShizukuClipboardService created in process uid=" + android.os.Process.myUid());
    }

    /**
     * Starts listening for clipboard changes. Called automatically when the
     * service is bound, but can also be invoked manually.
     */
    private void startMonitoring() {
        if (monitoring) return;
        try {
            clipboardManager.addPrimaryClipChangedListener(listener);
            monitoring = true;
            Log.i(TAG, "Clipboard monitoring started");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start monitoring", t);
        }
    }

    private void stopMonitoring() {
        if (!monitoring) return;
        try {
            clipboardManager.removePrimaryClipChangedListener(listener);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to remove listener", t);
        }
        monitoring = false;
    }

    @Override
    public String getCurrentClip() {
        try {
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
                return null;
            }
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).getText();
            return text != null ? text.toString() : null;
        } catch (SecurityException e) {
            // Even in Shizuku this could theoretically happen; log and bail.
            Log.w(TAG, "SecurityException reading clipboard: " + e.getMessage());
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "Error reading clipboard", t);
            return null;
        }
    }

    @Override
    public void registerCallback(IClipboardCallback callback) {
        if (callback == null) return;
        synchronized (callbacks) {
            if (!callbacks.contains(callback)) {
                callbacks.add(callback);
            }
        }
        // Start monitoring lazily when the first callback is registered.
        if (!monitoring) {
            startMonitoring();
        }
        // Immediately fire the current clip so the app is in sync.
        checkNow();
    }

    @Override
    public void unregisterCallback(IClipboardCallback callback) {
        if (callback == null) return;
        synchronized (callbacks) {
            callbacks.remove(callback);
        }
    }

    @Override
    public boolean isMonitoring() {
        return monitoring;
    }

    @Override
    public void checkNow() {
        notifyCallbacks();
    }

    /**
     * Reads the current clipboard and, if it differs from the last value we
     * notified, dispatches the new text to all registered callbacks.
     */
    private void notifyCallbacks() {
        String text = getCurrentClip();
        // Only notify when the text actually changes to avoid duplicate events.
        if (text != null && text.equals(lastNotifiedText)) {
            return;
        }
        lastNotifiedText = text;

        List<IClipboardCallback> snapshot;
        synchronized (callbacks) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (IClipboardCallback cb : snapshot) {
            try {
                cb.onClipboardChanged(text);
            } catch (RemoteException e) {
                // Callback died; remove it on next iteration.
                Log.w(TAG, "Callback RemoteException: " + e.getMessage());
                synchronized (callbacks) {
                    callbacks.remove(cb);
                }
            }
        }
    }

    /**
     * Called by the Shizuku framework when the user service is being destroyed.
     * We implement onDestroy via the no-arg method that Shizuku calls through
     * reflection if present; otherwise cleanup happens in finalize.
     */
    public void onDestroy() {
        Log.i(TAG, "ShizukuClipboardService onDestroy");
        stopMonitoring();
        synchronized (callbacks) {
            callbacks.clear();
        }
    }
}
