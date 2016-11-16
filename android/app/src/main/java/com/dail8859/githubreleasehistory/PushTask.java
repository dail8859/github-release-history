package com.dail8859.githubreleasehistory;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;

public class PushTask extends RepoTask<Iterable<PushResult>> {
    private final Git repo;
    private final CredentialsProvider cp;
    private final ProgressMonitor pm;

    public PushTask(Git _repo, CredentialsProvider _cp, ProgressMonitor _pm, AsyncTaskCallback<Iterable<PushResult>> _cb) {
        super(_cb);
        repo = _repo;
        cp = _cp;
        pm = _pm;
    }

    @Override
    protected Iterable<PushResult> doInBackground(Void... params) {
        PushCommand pc = repo.push().setPushAll().setCredentialsProvider(cp);

        if (pm != null) pc.setProgressMonitor(pm);

        try {
            return pc.call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            return null;
        }
    }
}
