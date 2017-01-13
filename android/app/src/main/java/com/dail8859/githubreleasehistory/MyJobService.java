package com.dail8859.githubreleasehistory;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.Log;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.util.Calendar;
import java.util.TimeZone;

public class MyJobService extends JobService {
    private static final int JOB_ID = 5;

    public static void scheduleJob(Context context) {
        Log.d("TAG", "scheduleJob: scheduling next job");

        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 2);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        long msTillMidnight = c.getTimeInMillis() - System.currentTimeMillis();
        msTillMidnight += 5000; // Add a slight delay
        long msWindow = 5 * 60 * 1000; // Within 5 minutes

        // For testing
        //msTillMidnight = 20 * 1000;
        //msWindow = 35 * 1000;

        ComponentName serviceName = new ComponentName(context, MyJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, serviceName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Needs network access
                .setMinimumLatency(msTillMidnight)
                .setOverrideDeadline(msTillMidnight + msWindow)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build();

        Log.d("TAG", "scheduleJob: scheduling job in " + msTillMidnight + "ms");

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        int result = scheduler.schedule(jobInfo);
        if (result == JobScheduler.RESULT_SUCCESS) Log.d("TAG", "scheduleJob: job scheduled successfully!");
    }

    public static void candelJob(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancelAll();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d("TAG", "onStartJob");

        /*
        if (params.isOverrideDeadlineExpired()) {
            showNotification("The deadline has expired. :(");
            Log.d("TAG", "The deadline has expired. :(");
            return false;
        }
        */
        new JobTask(this).execute(params);
        return true;
    }

    public void showNotification(String content) {
        Resources r = getResources();
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Github Release History")
                .setContentText(content)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private static class JobTask extends AsyncTask<JobParameters, Void, JobParameters> {
        private final MyJobService jobService;
        private final Git repo;

        public JobTask(MyJobService jobService) {
            this.repo = RepoUtilities.getLocalRepository(jobService);
            this.jobService = jobService;
        }

        @Override
        protected JobParameters doInBackground(JobParameters... params) {
            final String username = jobService.getString(R.string.GITHUB_USERNAME);
            final String email = jobService.getString(R.string.GITHUB_USEREMAIL);
            final String api_key = jobService.getString(R.string.SUPER_SECRET_GITHUB_API_KEY);
            final String repoDir = RepoUtilities.getLocalRepositoryDirectory(jobService);

            Log.d("TAG", "doInBackground");
            try {
                repo.reset().setMode(ResetCommand.ResetType.HARD).call();
                Log.d("TAG", "doInBackground: head has been reset");
                repo.pull().call();
                Log.d("TAG", "doInBackground: pull completed");
                String commitMessage = new StatGatherer(repoDir, username, api_key, null, null).doInBackground();
                Log.d("TAG", "doInBackground: StatGatherer done");
                if (repo.diff().call().size() > 0) {
                    Log.d("TAG", "doInBackground: changes have been made");
                    RepoUtilities.addAllFilesToStaging(repo);
                    RepoUtilities.commitChanges(repo, username, email, commitMessage, null);
                    Log.d("TAG", "doInBackground: it has been done");
                    jobService.showNotification("Success!");
                }

                MyJobService.scheduleJob(jobService);
            } catch (GitAPIException e) {
                jobService.showNotification("Oh noes");
                e.printStackTrace();
            }
            return params[0];
        }

        @Override
        protected void onPostExecute(JobParameters jobParameters) {
            jobService.jobFinished(jobParameters, false);
        }
    }
}
