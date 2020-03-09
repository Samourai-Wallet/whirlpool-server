package com.samourai.whirlpool.server.utils;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;

public class MultiClientListener extends LoggingWhirlpoolClientListener {
  // indice 0 is always null as currentMix starts from 1
  private MixStatus mixStatus;
  private MixStep mixStep;
  private MultiClientManager multiClientManager;

  public MultiClientListener(String poolId, MultiClientManager multiClientManager) {
    super(poolId);
    this.multiClientManager = multiClientManager;
  }

  @Override
  public void success(MixSuccess mixSuccess) {
    super.success(mixSuccess);
    mixStatus = MixStatus.SUCCESS;
    notifyMultiClientManager();
  }

  @Override
  public void progress(MixStep step) {
    super.progress(step);
    mixStep = step;
  }

  @Override
  public void fail(MixFailReason reason, String notifiableError) {
    super.fail(reason, notifiableError);
    mixStatus = MixStatus.FAIL;
    notifyMultiClientManager();
  }

  private void notifyMultiClientManager() {
    synchronized (multiClientManager) {
      multiClientManager.notify();
    }
  }

  public MixStatus getMixStatus() {
    return mixStatus;
  }

  public MixStep getMixStep() {
    return mixStep;
  }
}
