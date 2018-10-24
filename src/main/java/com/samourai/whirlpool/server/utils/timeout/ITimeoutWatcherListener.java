package com.samourai.whirlpool.server.utils.timeout;

public interface ITimeoutWatcherListener {

  Long computeTimeToWait(TimeoutWatcher timeoutWatcher);

  void onTimeout(TimeoutWatcher timeoutWatcher);
}
