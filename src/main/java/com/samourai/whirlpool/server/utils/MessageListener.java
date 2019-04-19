package com.samourai.whirlpool.server.utils;

public interface MessageListener<S> {
  void onMessage(S message);
}
