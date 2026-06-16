package com.catchingnow.clip;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.util.Log;

/**
 * Created by heruoxin on 15/1/19.
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SyncJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        CrashHandler.log("SyncJobService", "onStartJob");
        try {
        Log.v(MyUtil.PACKAGE_NAME, "Start Clean up...");
        return Storage.getInstance(this).cleanUpAndRequestBackup();
        } catch (Throwable e) {
            CrashHandler.logException("SyncJobService.onStartJob", e);
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
