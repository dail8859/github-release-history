package com.dail8859.githubreleasehistory;

public interface AsyncTaskCallback<T> {
    void onComplete(T val);
}
