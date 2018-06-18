package com.samourai.whirlpool.server.utils.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class TimeoutWatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ITimeoutWatcherListener listener;

    private long waitSince;
    private boolean running;
    private Thread thread;

    public TimeoutWatcher(ITimeoutWatcherListener listener) {
        this.listener = listener;

        this.waitSince = System.currentTimeMillis();
        this.running = true;

        // run
        this.thread = new Thread(this);
        this.thread.start();
    }

    @Override
    public void run() {
        while(running) {

            // did we wait enough?
            long timeToWait = listener.computeTimeToWait(this);
            if (timeToWait <= 0) {
                // timer expired => notify
                listener.onTimeout(this);
                // reset timer
                waitSince = System.currentTimeMillis();
            }
            else {
                if (log.isDebugEnabled()) {
                    log.debug(timeToWait + "ms to wait before next check.");
                }
                try {
                    Thread.sleep(timeToWait);
                }
                catch(InterruptedException e) {
                    // normal
                }
            }
        }
    }

    public void stop() {
        running = false;
        resumeThread();
    }

    public void resetTimeout() {
        this.waitSince = System.currentTimeMillis();
        resumeThread();
    }

    public void resumeThread() {
        try {
            this.thread.interrupt(); // resume thread from sleep
        }
        catch(Exception e) {
            log.error("", e);
        }
    }

    public void __simulateElapsedTime(long elapsedTimeSeconds) {
        this.waitSince = (System.currentTimeMillis() - (elapsedTimeSeconds * 1000));
        long timeToWait = listener.computeTimeToWait(this);
        log.info("__simulateElapsedTime: "+waitSince+" (" + timeToWait + "ms to wait)");
        resumeThread();
    }

    public long computeElapsedTime() {
        long elapsedTime = System.currentTimeMillis() - waitSince;
        return elapsedTime;
    }
}
