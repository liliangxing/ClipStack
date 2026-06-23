package com.catchingnow.clip;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import rikka.shizuku.Shizuku;

/**
 * Manages the lifecycle of the Shizuku integration for ClipStack.
 *
 * <p>This class is the single entry point through which the rest of the app
 * interacts with Shizuku. It is responsible for:</p>
 * <ul>
 *   <li>Detecting whether Shizuku is installed and its server is running.</li>
 *   <li>Requesting the runtime permission that Shizuku requires (API v23).</li>
 *   <li>Binding to {@link ShizukuClipboardService} which runs inside the
 *       Shizuku server process with shell (UID 2000) privileges.</li>
 *   <li>Forwarding clipboard-change events from the Shizuku process back to
 *       a listener registered by the app.</li>
 *   <li>Gracefully falling back to the legacy in-app clipboard listener when
 *       Shizuku is unavailable.</li>
 * </ul>
 *
 * <h2>Why Shizuku?</h2>
 * <p>Starting from Android 10 and made significantly stricter in Android 16,
 * background apps can no longer read the system clipboard. The shell user
 * (UID 2000) is exempt from this restriction. Shizuku runs a server process
 * as the shell user; by running our {@link ShizukuClipboardService} inside
 * that process we regain reliable background clipboard monitoring.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ShizukuManager mgr = ShizukuManager.getInstance(context);
 * mgr.setClipboardListener(text -> handleNewClip(text));
 * mgr.start(); // async; will request permission if needed
 * }</pre>
 */
public class ShizukuManager {

    private static final String TAG = "ClipStack/ShizukuMgr";
    private static final int PERMISSION_REQUEST_CODE = 0xC51A;

    private static ShizukuManager instance;

    private final Context context;
    private ClipboardChangeListener clipboardListener;
    private IClipboardService remoteService;
    private boolean bound = false;
    private boolean permissionGranted = false;
    private boolean listenersRegistered = false;

    /** Callback interface for receiving clipboard text from the Shizuku process. */
    public interface ClipboardChangeListener {
        void onClipboardChanged(String text);
    }

    private final IClipboardCallback remoteCallback = new IClipboardCallback.Stub() {
        @Override
        public void onClipboardChanged(String text) {
            Log.d(TAG, "Received clipboard change from Shizuku process");
            if (clipboardListener != null) {
                clipboardListener.onClipboardChanged(text);
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "Shizuku user service connected");
            remoteService = IClipboardService.Stub.asInterface(service);
            try {
                remoteService.registerCallback(remoteCallback);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to register callback", t);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Shizuku user service disconnected");
            remoteService = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.w(TAG, "Shizuku binding died");
            remoteService = null;
            bound = false;
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.w(TAG, "Shizuku null binding");
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (requestCode != PERMISSION_REQUEST_CODE) return;
                    permissionGranted = (grantResult == PackageManager.PERMISSION_GRANTED);
                    Log.i(TAG, "Permission result: granted=" + permissionGranted);
                    if (permissionGranted) {
                        bindUserService();
                    }
                }
            };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            new Shizuku.OnBinderReceivedListener() {
                @Override
                public void onBinderReceived() {
                    Log.i(TAG, "Shizuku binder received (server is running)");
                    if (checkPermission()) {
                        bindUserService();
                    }
                }
            };

    private final Shizuku.OnBinderDeadListener binderDeadListener =
            new Shizuku.OnBinderDeadListener() {
                @Override
                public void onBinderDead() {
                    Log.w(TAG, "Shizuku binder dead (server stopped)");
                    unbindUserService();
                }
            };

    private ShizukuManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized ShizukuManager getInstance(Context context) {
        if (instance == null) {
            instance = new ShizukuManager(context);
        }
        return instance;
    }

    public void setClipboardListener(ClipboardChangeListener listener) {
        this.clipboardListener = listener;
    }

    /**
     * Registers Shizuku listeners and, if the server is already running and
     * permission is granted, binds to the user service. Safe to call multiple
     * times.
     */
    public synchronized void start() {
        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
            Shizuku.addRequestPermissionResultListener(permissionResultListener);
            listenersRegistered = true;
            Log.i(TAG, "Shizuku listeners registered");
        }
        // If the binder is already alive, bind immediately.
        if (isShizukuRunning() && checkPermission()) {
            bindUserService();
        }
    }

    /**
     * Unbinds the user service and removes all listeners. Called when the
     * app no longer needs Shizuku monitoring (e.g. service is destroyed).
     */
    public synchronized void stop() {
        if (listenersRegistered) {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
            Shizuku.removeRequestPermissionResultListener(permissionResultListener);
            listenersRegistered = false;
        }
        unbindUserService();
    }

    /**
     * Returns true if the Shizuku app is installed on the device.
     */
    public static boolean isShizukuInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns true if the Shizuku server process is currently running.
     */
    public static boolean isShizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns true if the app has been granted the Shizuku runtime permission.
     */
    public boolean hasPermission() {
        return checkPermission();
    }

    /**
     * Static helper that returns the Shizuku permission state.
     * @return {@link PackageManager#PERMISSION_GRANTED} or
     *         {@link PackageManager#PERMISSION_DENIED}
     */
    public static int checkSelfPermission() {
        try {
            if (Shizuku.isPreV11()) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return Shizuku.checkSelfPermission();
        } catch (Throwable t) {
            Log.w(TAG, "checkSelfPermission failed: " + t.getMessage());
            return PackageManager.PERMISSION_DENIED;
        }
    }

    private boolean checkPermission() {
        try {
            if (Shizuku.isPreV11()) {
                // Pre-v11 Shizuku versions don't require runtime permission.
                permissionGranted = true;
                return true;
            }
            permissionGranted = (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED);
            return permissionGranted;
        } catch (Throwable t) {
            Log.w(TAG, "checkPermission failed: " + t.getMessage());
            return false;
        }
    }

    /**
     * Requests the Shizuku runtime permission. The result is delivered
     * asynchronously via the permission result listener.
     */
    public void requestPermission() {
        try {
            if (Shizuku.isPreV11()) {
                permissionGranted = true;
                bindUserService();
                return;
            }
            if (checkPermission()) {
                bindUserService();
                return;
            }
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
        } catch (Throwable t) {
            Log.e(TAG, "requestPermission failed", t);
        }
    }

    /**
     * Binds to the {@link ShizukuClipboardService} running inside the Shizuku
     * server process. If already bound this is a no-op.
     */
    private synchronized void bindUserService() {
        if (bound) return;
        if (!isShizukuRunning()) {
            Log.w(TAG, "Cannot bind: Shizuku server not running");
            return;
        }
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(context, ShizukuClipboardService.class))
                    .processNameSuffix("clipboard_service")
                    .debuggable(false)
                    .version(1);

            Shizuku.bindUserService(args, serviceConnection);
            bound = true;
            Log.i(TAG, "bindUserService called, bound=" + bound);
        } catch (Throwable t) {
            Log.e(TAG, "bindUserService failed", t);
            bound = false;
        }
    }

    private synchronized void unbindUserService() {
        if (!bound) return;
        try {
            if (remoteService != null) {
                try {
                    remoteService.unregisterCallback(remoteCallback);
                } catch (Throwable ignored) {
                }
            }
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(context, ShizukuClipboardService.class))
                    .processNameSuffix("clipboard_service")
                    .debuggable(false)
                    .version(1);
            Shizuku.unbindUserService(args, serviceConnection, true);
        } catch (Throwable t) {
            Log.w(TAG, "unbindUserService failed: " + t.getMessage());
        }
        remoteService = null;
        bound = false;
    }

    /**
     * Returns true if the Shizuku clipboard service is currently connected
     * and actively monitoring the clipboard.
     */
    public boolean isActive() {
        return bound && remoteService != null;
    }

    /**
     * Triggers an immediate clipboard check on the remote service. Useful
     * after the app comes to the foreground to catch any clips that were
     * copied while the binding was being established.
     */
    public void checkNow() {
        if (remoteService != null) {
            try {
                remoteService.checkNow();
            } catch (Throwable t) {
                Log.w(TAG, "checkNow failed: " + t.getMessage());
            }
        }
    }

    /**
     * Returns the current clipboard text read from the Shizuku process, or
     * null if the service is not connected.
     */
    public String getCurrentClip() {
        if (remoteService == null) return null;
        try {
            return remoteService.getCurrentClip();
        } catch (Throwable t) {
            Log.w(TAG, "getCurrentClip failed: " + t.getMessage());
            return null;
        }
    }
}
