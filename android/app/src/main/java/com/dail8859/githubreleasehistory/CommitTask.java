package com.dail8859.githubreleasehistory;


import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

public class CommitTask extends RepoTask<Boolean> {
    private final Git repo;
    private final PersonIdent pi;
    private final String message;

    public CommitTask(Git _repo, PersonIdent _pi, String _message, AsyncTaskCallback<Boolean> _cb) {
        super(_cb);
        repo = _repo;
        pi = _pi;
        message = _message;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        CommitCommand cc = repo.commit().setCommitter(pi).setMessage(message);

        try {
            cc.call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
