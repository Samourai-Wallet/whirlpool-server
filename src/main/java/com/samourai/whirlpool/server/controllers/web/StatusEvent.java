package com.samourai.whirlpool.server.controllers.web;

import java.sql.Timestamp;

public class StatusEvent {
    private Timestamp date;
    private String title;
    private String details;

    public StatusEvent(Timestamp date, String title, String details) {
        this.date = date;
        this.title = title;
        this.details = details;
    }

    public Timestamp getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public String getDetails() {
        return details;
    }
}
