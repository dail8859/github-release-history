package com.dail8859.githubreleasehistory;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class TestService extends IntentService {

    public TestService() {
        super("TestService");
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(getClass().getSimpleName(), "I'm running!");

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();

        try {
            doIt();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent serviceIntent = new Intent(this, TestService.class);
        AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(this,  0,  serviceIntent, 0);
        mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30000, pendingIntent);

        Log.d(getClass().getSimpleName(), "I ran!");

        wakeLock.release();
    }

    private void doIt() throws IOException, ExecutionException, InterruptedException, GitAPIException {
        String repoDir = getFilesDir() + "/repo";
        Git repo;

        try {
            repo = Git.open(new File(repoDir));
        } catch (IOException e) {
            // Repo not cloned?
            Log.d("TAG", "doIt: Cloning");
            String repoUrl = "https://github.com/" + getString(R.string.GITHUB_USERNAME) + "/" + getString(R.string.GITHUB_REPONAME) + ".git";
            CloneTask ct = new CloneTask(repoDir, repoUrl, null, null);
            ct.get();

            repo = Git.open(new File(repoDir));
        }

        Log.d("TAG", "doIt: Pulling");
        PullTask pt = new PullTask(repo, null, null);
        pt.get();

        Log.d("TAG", "doIt: StatGatherer");
        StatGatherer sg = new StatGatherer(repoDir, getString(R.string.GITHUB_USERNAME), getString(R.string.SUPER_SECRET_GITHUB_API_KEY), null, null);
        String msg = sg.get();
        if (msg != null) {
            boolean has_changes = repo.diff().call().size() > 0;

            Log.d("TAG", "onComplete: Commit Message: " + msg);

            if (has_changes) {
                repo.add().addFilepattern(".").call();
                Log.d("TAG", "doIt: Commiting");
                CommitTask ct = new CommitTask(repo, new PersonIdent(getString(R.string.GITHUB_USERNAME), getString(R.string.GITHUB_USEREMAIL)), msg, null);
                ct.get();

                CredentialsProvider cp = new UsernamePasswordCredentialsProvider(getString(R.string.SUPER_SECRET_GITHUB_API_KEY), "x-oauth-basic");
                PushTask pushTask = new PushTask(repo, cp, null, null);
                pushTask.get();
            }
            else {
                Log.d("TAG", "nothing to commit");
            }
        }
    }
}
