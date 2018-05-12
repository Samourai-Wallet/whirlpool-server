package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.services.RoundLimitsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class RoundLimitsWatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Round round;
    private RoundLimitsManager roundLimitsManager;

    private long waitSince;
    private boolean running;
    private Thread thread;

    public RoundLimitsWatcher(Round round, RoundLimitsManager roundLimitsManager) {
        this.round = round;
        this.roundLimitsManager = roundLimitsManager;

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
            long timeToWait = roundLimitsManager.computeRoundWatcherTimeToWait(waitSince, round);
            if (timeToWait <= 0) {
                // timer expired => notify
                roundLimitsManager.onRoundWatcherTimeout(round, this);
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
        long timeToWait = roundLimitsManager.computeRoundWatcherTimeToWait(waitSince, round);
        log.info("__simulateElapsedTime: "+waitSince+" (" + timeToWait + "ms to wait)");
        resumeThread();
    }
}
