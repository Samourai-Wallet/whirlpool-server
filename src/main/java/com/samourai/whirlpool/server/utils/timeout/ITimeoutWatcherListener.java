package com.samourai.whirlpool.server.utils.timeout;

public interface ITimeoutWatcherListener {

    long computeTimeToWait(TimeoutWatcher timeoutWatcher);
    void onTimeout(TimeoutWatcher timeoutWatcher);

}
