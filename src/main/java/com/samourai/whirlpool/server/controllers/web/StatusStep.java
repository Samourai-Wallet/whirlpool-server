package com.samourai.whirlpool.server.controllers.web;

public class StatusStep {
    private boolean done;
    private boolean active;
    private String title;
    private String details;

    public StatusStep(boolean done, boolean active, String title, String details) {
        this.done = done;
        this.active = active;
        this.title = title;
        this.details = details;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isActive() {
        return active;
    }

    public String getTitle() {
        return title;
    }

    public String getDetails() {
        return details;
    }
}
