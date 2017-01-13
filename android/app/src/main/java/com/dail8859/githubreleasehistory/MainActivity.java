package com.dail8859.githubreleasehistory;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private String repoDir;
    private Git repo = null;
    private CommitAdapter commitAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repoDir = RepoUtilities.getLocalRepositoryDirectory(this);

        createCommitAdapter();
        updateLocalRepoReference();
    }

    @Override
    public void onResume(){
        super.onRestart();

        // The repository may have changed due to a background process
        resetUI();
    }

    private void createCommitAdapter() {
        commitAdapter = new CommitAdapter();
        ListView listView = (ListView) findViewById(R.id.listView);
        listView.setAdapter(commitAdapter);
        listView.setOnItemClickListener(commitAdapter);
    }

    private void updateLocalRepoReference() {
        repo = RepoUtilities.getLocalRepository(repoDir);
    }

    private void resetUI() {
        ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar);
        if (pb != null) pb.setVisibility(View.INVISIBLE);

        TextView tv = (TextView) findViewById(R.id.txt_prg_status);
        if (tv != null) tv.setText("");

        if (repo != null) {
            findViewById(R.id.btn_clone).setEnabled(false);
            findViewById(R.id.btn_pull).setEnabled(true);
            findViewById(R.id.btn_commit).setEnabled(true);
            findViewById(R.id.btn_reset).setEnabled(true);
            findViewById(R.id.btn_add_repo).setEnabled(true);

            try {
                BranchTrackingStatus bts = BranchTrackingStatus.of(repo.getRepository(), "master");
                if (bts != null) {
                    int aheadCount = bts.getAheadCount();

                    Button btn = (Button) findViewById(R.id.btn_push);
                    btn.setText("Push (" + String.valueOf(aheadCount) + ")");
                    btn.setEnabled(aheadCount > 0);
                    findViewById(R.id.btn_reset_head).setEnabled(aheadCount > 0);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            findViewById(R.id.btn_clone).setEnabled(true);
            findViewById(R.id.btn_pull).setEnabled(false);
            findViewById(R.id.btn_commit).setEnabled(false);
            findViewById(R.id.btn_reset_head).setEnabled(false);
            findViewById(R.id.btn_push).setEnabled(false);
            findViewById(R.id.btn_reset).setEnabled(false);
            findViewById(R.id.btn_add_repo).setEnabled(false);
        }

        commitAdapter.notifyDataSetChanged();
    }

    public void onButtonPress(View view) throws ExecutionException, InterruptedException {
        switch (view.getId()) {
            case R.id.btn_clone: {
                // Disable these during a clone
                findViewById(R.id.btn_clone).setEnabled(false);
                findViewById(R.id.btn_reset).setEnabled(false);

                String repoUrl = RepoUtilities.getRepositoryURL(this);
                CloneTask task = new CloneTask(repoDir, repoUrl, new RepoTaskMonitor(this), new AsyncTaskCallback<Boolean>() {
                    @Override public void onComplete(Boolean val) {
                        if (val) {
                            Toast.makeText(getApplicationContext(), "Clone Successful!", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            Toast.makeText(getApplicationContext(), "Clone Failed!", Toast.LENGTH_SHORT).show();
                        }
                        updateLocalRepoReference();
                        resetUI();
                    }
                });
                task.execute();
                break;
            }
            case R.id.btn_pull: {
                PullTask pt = new PullTask(repo, new RepoTaskMonitor(this), new AsyncTaskCallback<PullResult>() {
                    @Override public void onComplete(PullResult val) {
                        String message = (val != null && val.isSuccessful()) ? "Pull Successful!" : "Pull Failed!";
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                        resetUI();
                    }
                });
                pt.execute();
                break;
            }
            case R.id.btn_commit: {
                // Disable during a commit
                findViewById(R.id.btn_commit).setEnabled(false);

                final String username = getString(R.string.GITHUB_USERNAME);
                final String email = getString(R.string.GITHUB_USEREMAIL);
                final String api_key = getString(R.string.SUPER_SECRET_GITHUB_API_KEY);
                StatGatherer sg = new StatGatherer(repoDir, username, api_key, new RepoTaskMonitor(this), new AsyncTaskCallback<String>() {
                    @Override public void onComplete(String val) {
                        if (val != null) {
                            boolean has_changes = false;
                            try {
                                has_changes = repo.diff().call().size() > 0;
                            } catch (GitAPIException e) {
                                e.printStackTrace();
                            }

                            Log.d("TAG", "onComplete: Commit Message: " + val);

                            if (has_changes) {
                                doCommit(username, email, val);
                            }
                            else {
                                Log.d("TAG", "StatGatherer.onComplete: no changes made, skipping commit");
                                Toast.makeText(MainActivity.this, "Nothing to commit", Toast.LENGTH_SHORT).show();
                            }
                        }
                        resetUI();
                    }
                });
                sg.execute();

                MyJobService.candelJob(this);
                MyJobService.scheduleJob(this);
                break;
            }
            case R.id.btn_reset_head: {
                ResetTask rt = new ResetTask(repo, new AsyncTaskCallback<Boolean>() {
                    @Override public void onComplete(Boolean val) {
                        resetUI();
                    }
                });
                rt.execute();
                break;
            }
            case R.id.btn_push: {
                // Disable during a push
                findViewById(R.id.btn_push).setEnabled(false);

                String api_key = getString(R.string.SUPER_SECRET_GITHUB_API_KEY);
                CredentialsProvider cp = new UsernamePasswordCredentialsProvider(api_key, "x-oauth-basic");
                PushTask pt = new PushTask(repo, cp, new RepoTaskMonitor(MainActivity.this), new AsyncTaskCallback<Iterable<PushResult>>() {
                    @Override public void onComplete(Iterable<PushResult> val) {
                        for (PushResult pr : val) {
                            for (RemoteRefUpdate rru : pr.getRemoteUpdates()) {
                                if (rru.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE && rru.getStatus() != RemoteRefUpdate.Status.OK) {
                                    Log.d("TAG", "onComplete: " + rru.getRemoteName() + " failed with " + rru.getStatus().toString());
                                    Toast.makeText(MainActivity.this, "Push Failed! :(", Toast.LENGTH_SHORT).show();
                                    resetUI();
                                    return;
                                }
                            }
                        }
                        Toast.makeText(MainActivity.this, "Push Successful! :)", Toast.LENGTH_SHORT).show();
                        resetUI();
                    }
                });
                pt.execute();
                break;
            }
            case R.id.btn_reset: {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                try {
                                    org.apache.commons.io.FileUtils.deleteDirectory(new File(repoDir));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                updateLocalRepoReference();
                                resetUI();
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                //No button clicked
                                break;
                        }
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                builder.setMessage("Reset all the things?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();

                break;
            }
            case R.id.btn_add_repo: {
                //Make new Dialog
                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                dialog.setTitle("Repository");

                LinearLayout layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText userBox = new EditText(MainActivity.this);
                userBox.setHint("Username");
                userBox.setSingleLine();
                layout.addView(userBox);

                final EditText repoBox = new EditText(MainActivity.this);
                repoBox.setHint("Repository");
                repoBox.setSingleLine();
                layout.addView(repoBox);

                final EditText issueBox = new EditText(MainActivity.this);
                issueBox.setHint("Issue");
                issueBox.setInputType(InputType.TYPE_CLASS_NUMBER);
                layout.addView(issueBox);

                dialog.setView(layout);
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case DialogInterface.BUTTON_POSITIVE:
                                final String username = userBox.getText().toString();
                                final String reponame = repoBox.getText().toString();
                                final String issue = issueBox.getText().toString();

                                if (username.length() > 0 && reponame.length() > 0) {
                                    RepoToLookUp rtlu = new RepoToLookUp();
                                    rtlu.user = username;
                                    rtlu.repo = reponame;

                                    // add new repo to look up
                                    Log.d("TAG", "onClick: " + rtlu.user + "/" + rtlu.repo);

                                    List<RepoToLookUp> repos = Utilities.loadReposToLookUp(repoDir + "/repos.json");
                                    if (repos.contains(rtlu)) {
                                        Toast.makeText(MainActivity.this, "User/Repo already exists", Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    // Add this to the list. Insert it toward the beginning of the list. That way
                                    // it is after mine ;), yet shouldn't cause a merge conflict if any are manually
                                    // added at the end of the list.
                                    repos.add(Math.min(3, repos.size()), rtlu);

                                    Utilities.saveReposToLookUp(repoDir + "/repos.json", repos);

                                    String message = "Add new repo " + rtlu.user + "/" + rtlu.repo + (issue.length() > 0 ? "\n\nCloses #" + issue.toString() : "");
                                    Log.d("TAG", "onClick: " + message);
                                    doCommit(getString(R.string.GITHUB_USERNAME), getString(R.string.GITHUB_USEREMAIL), message);
                                }
                                Log.d("TAG", "onClick: added " + username + "/" + reponame);
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };
                dialog.setPositiveButton("OK", dialogClickListener).setNegativeButton("Cancel", dialogClickListener);
                dialog.show();
                break;
            }
        }
    }

    private void doCommit(String username, String email, String message) {
        RepoUtilities.addAllFilesToStaging(repo);

        RepoUtilities.commitChanges(repo, username, email, message, new AsyncTaskCallback<Boolean>() {
            @Override public void onComplete(Boolean val) {
                String message = val ? "Commit Successful! :)" : "Commit Failed!";
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                resetUI();
            }
        });
    }

    public class RepoTaskMonitor implements ProgressMonitor {
        MainActivity act;
        public RepoTaskMonitor(MainActivity _act) {
            act = _act;
        }

        @Override
        public void start(int totalTasks) {
            //Log.d("totalTasks", String.valueOf(totalTasks));
        }

        @Override
        public void beginTask(String title, int totalWork) {
            //Log.d("beginTask", title + " work:" + String.valueOf(totalWork));

            // Update stuff, this needs to be done on the UI thread
            final String new_title = title;
            final int new_totalWork = totalWork;
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar);
                    if (pb != null) {
                        pb.setMax(new_totalWork);
                        pb.setProgress(0);
                        pb.setSecondaryProgress(0); // Not needed but make sure it's 0
                        pb.setVisibility(View.VISIBLE);
                    }
                    TextView tv = (TextView) findViewById(R.id.txt_prg_status);
                    if (tv != null) tv.setText(new_title);
                }
            });
        }

        @Override
        public void endTask() {
            //Log.d("endTask", "a");
        }

        @Override
        public void update(int i) {
            final int new_i = i;
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar);
                    if (pb != null) pb.setProgress(pb.getProgress() + new_i);
                }
            });
        }

        @Override
        public boolean isCancelled() {
            return false;
            //return isTaskCanceled();
        }
    }

    public class CommitAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {
        int count = 0;

        @Override
        public void notifyDataSetChanged() {
            _getCount();
            super.notifyDataSetChanged();
        }

        private void _getCount() {
            if (repo == null) {
                count = 0;
                return;
            }

            BranchTrackingStatus bts = null;
            try {
                bts = BranchTrackingStatus.of(repo.getRepository(), "master");
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d("TAG", "getCount: " + String.valueOf(bts != null ? bts.getAheadCount() + 1 : 0));
            count = bts != null ? bts.getAheadCount() + 1 : 0;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public Object getItem(int position) {
            try {
                RevWalk walker = new RevWalk(repo.getRepository());

                Ref master_ref = repo.getRepository().getRef("master");
                Ref origin_ref = repo.getRepository().getRef("origin/master");

                // Apparently just because "repo" is valid doesn't mean everything is intact
                if (master_ref != null && origin_ref != null) {
                    RevCommit master_commit = walker.parseCommit(master_ref.getObjectId());
                    walker.markStart(master_commit);

                    int count = 0;
                    for (RevCommit rev : walker) {
                        if (count == position) return rev;
                        count++;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            RevCommit commit = (RevCommit) getItem(position);
            if (commit == null) return convertView;

            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            // Lookup view for data population
            TextView first = (TextView) convertView.findViewById(android.R.id.text1);
            TextView second = (TextView) convertView.findViewById(android.R.id.text2);
            first.setText(commit.getShortMessage());

            try {
                boolean is_master = repo.getRepository().getRef("master").getObjectId().equals(commit.toObjectId());
                boolean is_origin = repo.getRepository().getRef("origin/master").getObjectId().equals(commit.toObjectId());

                String str = commit.toObjectId().abbreviate(8).name();
                if (is_master) str += " (master)";
                if (is_origin) str += " (origin/master)";
                second.setText(str);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Return the completed view to render on screen
            return convertView;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            RevCommit commit = (RevCommit) parent.getItemAtPosition(position);

            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Commit " + commit.toObjectId().abbreviate(8).name())
                .setMessage(commit.getFullMessage())
                .show();
        }
    }
}
