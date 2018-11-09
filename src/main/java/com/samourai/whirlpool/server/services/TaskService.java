package com.samourai.whirlpool.server.services;

import java.lang.invoke.MethodHandles;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private TaskScheduler taskScheduler;

  public TaskService(TaskScheduler taskScheduler) {
    this.taskScheduler = taskScheduler;
  }

  public ScheduledFuture runOnce(long delayMilliSeconds, Runnable runnable) {
    return taskScheduler.schedule(
        runnable, new Date(System.currentTimeMillis() + delayMilliSeconds));
  }
}
