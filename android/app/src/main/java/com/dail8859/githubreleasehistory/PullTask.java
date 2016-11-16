package com.dail8859.githubreleasehistory;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;

public class PullTask extends RepoTask<PullResult> {
    private final Git repo;
    private final ProgressMonitor pm;

    public PullTask(Git _repo, ProgressMonitor _pm, AsyncTaskCallback<PullResult> _cb) {
        super(_cb);
        repo = _repo;
        pm = _pm;
    }

    @Override
    protected PullResult doInBackground(Void... params) {
        PullCommand pullCommand = repo.pull();

        if (pm != null) pullCommand.setProgressMonitor(pm);

        try {
            return pullCommand.call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            return null;
        }
    }
}
