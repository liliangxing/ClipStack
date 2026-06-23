package com.catchingnow.clip;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.preference.Preference;
import rikka.shizuku.Shizuku;

public class ActivitySetting extends MyPreferenceActivity {

    public final static String PREF_NOTIFICATION_SHOW = "pref_notification_show";
    public final static String PREF_NOTIFICATION_PIN = "pref_notification_pin";
    public final static String PREF_NOTIFICATION_PRIORITY = "pref_notification_priority";
    public final static String PREF_START_SERVICE = "pref_start_service";
    public final static String PREF_LONG_CLICK_BEHAVIOR = "pref_long_click_behavior";
    public final static String PREF_SAVE_DATES = "pref_save_dates";
    public static final String PREF_FLOATING_BUTTON = "pref_floating_button_switch";
    public static final String PREF_FLOATING_BUTTON_ALWAYS_SHOW = "pref_floating_button_always_show";
    public static final String PREF_SHIZUKU = "pref_shizuku";
    private static final int SHIZUKU_PERMISSION_REQUEST = 0xC51B;
    private Toolbar mActionBar;
    private SharedPreferences.OnSharedPreferenceChangeListener myPrefChangeListener;
    private SharedPreferences preferences;
    private Context context;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (requestCode != SHIZUKU_PERMISSION_REQUEST) return;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(context, R.string.shizuku_toast_activated, Toast.LENGTH_SHORT).show();
                                ShizukuManager.getInstance(context).start();
                            } else {
                                Toast.makeText(context, R.string.shizuku_toast_permission_denied, Toast.LENGTH_SHORT).show();
                            }
                            updateShizukuSummary();
                        }
                    });
                }
            };

    private final Shizuku.OnBinderReceivedListener shizukuBinderReceivedListener =
            new Shizuku.OnBinderReceivedListener() {
                @Override
                public void onBinderReceived() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateShizukuSummary();
                        }
                    });
                }
            };

    private final Shizuku.OnBinderDeadListener shizukuBinderDeadListener =
            new Shizuku.OnBinderDeadListener() {
                @Override
                public void onBinderDead() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateShizukuSummary();
                        }
                    });
                }
            };

    public ActivitySetting() {
        myPrefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                  String key) {
                switch (key) {
                    case PREF_NOTIFICATION_SHOW:
                    case PREF_NOTIFICATION_PRIORITY:
                    case PREF_NOTIFICATION_PIN:
                        CBWatcherService.startCBService(context, true);
                        break;
                    case PREF_START_SERVICE:
                        CBWatcherService.startCBService(context, true);
                        if (!sharedPreferences.getBoolean(PREF_START_SERVICE, true)) {
                            context.stopService(new Intent(context, FloatingWindowService.class));
                            break;
                        }
                    case PREF_FLOATING_BUTTON:
                    case PREF_FLOATING_BUTTON_ALWAYS_SHOW:
                        if (sharedPreferences.getBoolean(PREF_FLOATING_BUTTON, false)) {
                            if (sharedPreferences.getString(PREF_FLOATING_BUTTON_ALWAYS_SHOW, "always").equals("always")) {
                                if (checkOverlayPermission()) {
                                    context.startService(new Intent(context, FloatingWindowService.class));
                                }
                            } else {
                                checkAccessibilityPermission();
                                context.stopService(new Intent(context, FloatingWindowService.class));
                            }
                        } else {
                            context.stopService(new Intent(context, FloatingWindowService.class));
                        }
                        break;
                    case PREF_SAVE_DATES:
                        int i = Integer.parseInt(sharedPreferences.getString(key, "7"));
                        if (i > 9998) {
                            findPreference(key).setSummary(getString(R.string.pref_storage_summary_infinite));
                        } else {
                            findPreference(key).setSummary(String.format(getString(R.string.pref_storage_summary_days), i));
                        }
                        break;
                    case PREF_LONG_CLICK_BEHAVIOR:
                        break;
                }
            }
        };
    }

    public void initSharedPrefListener() {
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(myPrefChangeListener);
//        SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(this);
//        preference.edit().putLong(PREF_LAST_ACTIVE_THIS, new Date().getTime()).commit();
    }

    private boolean checkAccessibilityPermission() {
        if (MyUtil.isAccessibilityEnabled(context, MyUtil.PACKAGE_NAME)) {
            Log.i(MyUtil.PACKAGE_NAME, "checkAccessibilityPermission: true");
            return true;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.accessibility_dialog_title))
                .setMessage(getString(R.string.accessibility_dialog_summary))
                .setPositiveButton(getString(R.string.accessibility_dialog_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                startActivityForResult(intent, 0);
                            }
                        }
                )
                .setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PreferenceManager.getDefaultSharedPreferences(context)
                                        .edit()
                                        .putString(PREF_FLOATING_BUTTON_ALWAYS_SHOW, "always")
                                        .apply();
                            }
                        }
                )
                .setCancelable(false)
                .create()
                .show();
        return false;
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        if (Settings.canDrawOverlays(this)) return true;
        new AlertDialog.Builder(this)
                .setTitle("需要悬浮窗权限")
                .setMessage("悬浮球功能需要在其他应用上层显示内容，请在设置页面打开「允许显示在其他应用的上层」。")
                .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, 0);
                            }
                        }
                )
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .setCancelable(false)
                .create()
                .show();
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
        super.onCreate(savedInstanceState);
        CrashHandler.log("ActivitySetting", "onCreate");
        context = this.getBaseContext();
        addPreferencesFromResource(R.xml.preference);
        mActionBar.setTitle(getTitle());

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        CrashHandler.log("ActivitySetting", "onCreate OK");

        // Set up Shizuku preference click handler
        Preference shizukuPref = findPreference(PREF_SHIZUKU);
        if (shizukuPref != null) {
            shizukuPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showShizukuDialog();
                    return true;
                }
            });
        }
        } catch (Throwable e) {
            CrashHandler.logException("ActivitySetting.onCreate", e);
            throw e;
        }
    }

    @Override
    protected void onResume() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mActionBar.setElevation(MyUtil.dip2px(context, 4));
        } else {
            View mToolbarShadow = findViewById(R.id.my_toolbar_shadow);
            if (mToolbarShadow != null) {
                mToolbarShadow.setVisibility(View.VISIBLE);
            }
        }

        super.onResume();

        if (!preferences.getString(PREF_FLOATING_BUTTON_ALWAYS_SHOW, "always").equals("always")) {
            Log.i(MyUtil.PACKAGE_NAME, "" + preferences.getString(PREF_FLOATING_BUTTON_ALWAYS_SHOW, "always"));
            checkAccessibilityPermission();
        }

        initSharedPrefListener();

        // Register Shizuku listeners to keep the preference summary up to date
        try {
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener);
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener);
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Throwable t) {
            Log.w(MyUtil.PACKAGE_NAME, "Shizuku listeners registration failed: " + t.getMessage());
        }
        updateShizukuSummary();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener);
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener);
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        ViewGroup contentView = (ViewGroup) LayoutInflater.from(this).inflate(
                R.layout.activity_setting, new LinearLayout(this), false);

        mActionBar = (Toolbar) contentView.findViewById(R.id.my_toolbar);
        mActionBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ViewGroup contentWrapper = (ViewGroup) contentView.findViewById(R.id.content_wrapper);
        LayoutInflater.from(this).inflate(layoutResID, contentWrapper, true);

        getWindow().setContentView(contentView);
    }

    /**
     * Updates the Shizuku preference summary to reflect the current state:
     * not installed, not running, no permission, or active.
     */
    private void updateShizukuSummary() {
        Preference shizukuPref = findPreference(PREF_SHIZUKU);
        if (shizukuPref == null) return;

        int summaryRes;
        if (!ShizukuManager.isShizukuInstalled(context)) {
            summaryRes = R.string.pref_shizuku_summary_not_installed;
        } else if (!ShizukuManager.isShizukuRunning()) {
            summaryRes = R.string.pref_shizuku_summary_not_running;
        } else if (ShizukuManager.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            summaryRes = R.string.pref_shizuku_summary_no_permission;
        } else if (ShizukuManager.getInstance(context).isActive()) {
            summaryRes = R.string.pref_shizuku_summary_active;
        } else {
            summaryRes = R.string.pref_shizuku_summary_no_permission;
        }
        shizukuPref.setSummary(summaryRes);
    }

    /**
     * Shows a dialog explaining Shizuku integration and guides the user
     * through the setup: install Shizuku, start the server, authorize.
     */
    private void showShizukuDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.shizuku_dialog_title);
        builder.setMessage(R.string.shizuku_dialog_summary);

        boolean installed = ShizukuManager.isShizukuInstalled(context);
        boolean running = ShizukuManager.isShizukuRunning();
        boolean granted = ShizukuManager.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!installed) {
            builder.setPositiveButton(R.string.shizuku_dialog_install, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://shizuku.rikka.app/")));
                    } catch (Throwable t) {
                        CrashHandler.logException("ShizukuDialog.install", t);
                    }
                }
            });
        } else if (!running) {
            builder.setPositiveButton(R.string.shizuku_dialog_open, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        if (launchIntent != null) {
                            startActivity(launchIntent);
                        }
                    } catch (Throwable t) {
                        CrashHandler.logException("ShizukuDialog.open", t);
                    }
                }
            });
        } else if (!granted) {
            builder.setPositiveButton(R.string.shizuku_dialog_authorize, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                    } catch (Throwable t) {
                        Toast.makeText(context, R.string.shizuku_toast_not_running, Toast.LENGTH_SHORT).show();
                        CrashHandler.logException("ShizukuDialog.authorize", t);
                    }
                }
            });
        } else {
            // Already authorized and running; just open Shizuku for management
            builder.setPositiveButton(R.string.shizuku_dialog_open, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        if (launchIntent != null) {
                            startActivity(launchIntent);
                        }
                    } catch (Throwable t) {
                        CrashHandler.logException("ShizukuDialog.open", t);
                    }
                }
            });
        }

        builder.setNegativeButton(R.string.shizuku_dialog_dismiss, null);
        builder.show();
    }

}
