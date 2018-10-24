package com.samourai.whirlpool.server.beans;

public class CachedResult<T, E extends Exception> {
  private E exception;
  private T result;

  public CachedResult(E exception) {
    this.exception = exception;
    this.result = null;
  }

  public CachedResult(T result) {
    this.exception = null;
    this.result = result;
  }

  public T getOrException() throws E {
    if (exception != null) {
      throw exception;
    }
    return result;
  }
}
