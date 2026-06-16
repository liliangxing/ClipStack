package com.catchingnow.clip;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String LOG_DIR = "/sdcard/douyinguanjia/Log";
    private static final String LOG_FILE = "clickStack.log";

    private static CrashHandler instance;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context context;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void init(Context ctx) {
        this.context = ctx.getApplicationContext();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        log("CrashHandler", "init, SDK_INT=" + Build.VERSION.SDK_INT + ", MODEL=" + Build.MODEL);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        handleException(ex);
        if (defaultHandler != null && defaultHandler != this) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            Process.killProcess(Process.myPid());
        }
    }

    public static void log(String tag, String msg) {
        if (msg == null) msg = "null";
        Log.e("ClipStack", tag + ": " + msg);
        writeToFile(tag + "|" + msg);
    }

    public static void logException(String tag, Throwable e) {
        if (e == null) return;
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        String stackTrace = sw.toString();
        Log.e("ClipStack", tag + ": " + stackTrace);
        writeToFile(tag + "|" + stackTrace);
    }

    private void handleException(Throwable ex) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            Writer result = new StringWriter();
            PrintWriter printWriter = new PrintWriter(result);
            printWriter.println("========================================");
            printWriter.println("CRASH REPORT");
            printWriter.println("Time: " + formatTime(new Date()));
            printWriter.println("App Version: " + getVersionName());
            printWriter.println("SDK_INT: " + Build.VERSION.SDK_INT);
            printWriter.println("MODEL: " + Build.MODEL);
            printWriter.println("MANUFACTURER: " + Build.MANUFACTURER);
            printWriter.println("========================================");
            ex.printStackTrace(printWriter);
            printWriter.flush();
            String report = result.toString();
            Log.e("ClipStack", report);
            writeToFile(report);
        } catch (Exception ignored) {
        }
    }

    private static void writeToFile(String content) {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.exists() || !dir.isDirectory()) {
                Log.e("ClipStack", "Cannot create log dir: " + LOG_DIR);
                return;
            }
            File file = new File(dir, LOG_FILE);
            FileWriter fw = new FileWriter(file, true);
            try {
                fw.write(formatTime(new Date()));
                fw.write(" ");
                fw.write(content);
                fw.write("\n");
                fw.flush();
            } finally {
                fw.close();
            }
        } catch (Exception e) {
            Log.e("ClipStack", "Failed to write log file", e);
        }
    }

    private static String formatTime(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(date);
    }

    private String getVersionName() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
