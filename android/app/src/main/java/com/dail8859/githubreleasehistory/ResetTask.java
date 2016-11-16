package com.dail8859.githubreleasehistory;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

public class ResetTask extends RepoTask<Boolean> {
    private final Git repo;

    public ResetTask(Git _repo, AsyncTaskCallback<Boolean> _cb) {
        super(_cb);
        repo = _repo;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            repo.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/master").call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
