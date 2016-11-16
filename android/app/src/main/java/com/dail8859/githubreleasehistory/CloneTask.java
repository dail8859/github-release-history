package com.dail8859.githubreleasehistory;


import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.io.File;
import java.io.IOException;

public class CloneTask extends RepoTask<Boolean> {
    private final String directory;
    private final String url;
    private final ProgressMonitor pm;

    public CloneTask(String _directory, String _url, ProgressMonitor _pm, AsyncTaskCallback<Boolean> _cb) {
        super(_cb);
        directory = _directory;
        url = _url;
        pm = _pm;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        File f;
        try {
            f = new File(directory);
            org.apache.commons.io.FileUtils.deleteDirectory(f);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setCloneAllBranches(true)
                .setDirectory(f);

        if (pm != null) cloneCommand.setProgressMonitor(pm);

        try {
            cloneCommand.call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
