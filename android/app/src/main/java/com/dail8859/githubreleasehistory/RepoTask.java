package com.dail8859.githubreleasehistory;

import android.os.AsyncTask;

public abstract class RepoTask<T> extends AsyncTask<Void, Void, T> {
    private final AsyncTaskCallback<T> cb;

    RepoTask(AsyncTaskCallback<T> _cb) {
        cb = _cb;
    }

    @Override
    protected final void onPostExecute (T result) {
        if (cb != null) {
            cb.onComplete(result);
        }
    }
}
