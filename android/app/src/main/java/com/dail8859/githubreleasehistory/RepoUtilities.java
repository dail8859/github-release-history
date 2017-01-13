package com.dail8859.githubreleasehistory;

import android.content.Context;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.File;
import java.io.IOException;

public class RepoUtilities {
    public static String getRepositoryURL(Context ctx) {
        return "https://github.com/" + ctx.getString(R.string.GITHUB_USERNAME) + "/" + ctx.getString(R.string.GITHUB_REPONAME) + ".git";
    }

    public static String getLocalRepositoryDirectory(Context ctx) {
        return ctx.getFilesDir() + "/repo";
    }

    public static Git getLocalRepository(Context ctx) {
        return getLocalRepository(getLocalRepositoryDirectory(ctx));
    }

    public static Git getLocalRepository(String repoDirectory) {
        try {
            return Git.open(new File(repoDirectory));
        } catch (IOException e) {
            return null;
        }
    }

    public static void addAllFilesToStaging(Git repo) {
        try {
            repo.add().addFilepattern(".").call();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
    }

    public static void commitChanges(Git repo, String username, String email, String message, AsyncTaskCallback<Boolean> callback) {
        CommitTask ct = new CommitTask(repo, new PersonIdent(username, email), message, callback);
        ct.execute();
    }
}
