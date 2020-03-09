package com.samourai.whirlpool.server.controllers.rest.beans;

public class HealthResponse {
  private static final String STATUS_OK = "OK";
  private static final String STATUS_ERROR = "ERROR";
  public String status;
  public String error;

  public static HealthResponse error(String error) {
    return new HealthResponse(STATUS_ERROR, error);
  }

  public static HealthResponse ok() {
    return new HealthResponse(STATUS_OK, null);
  }

  protected HealthResponse(String status, String error) {
    this.status = status;
    this.error = error;
  }
}
