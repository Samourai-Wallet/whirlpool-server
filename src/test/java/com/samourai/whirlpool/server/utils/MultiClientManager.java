package com.samourai.whirlpool.server.utils;

import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiClientManager {
  private static final Logger log = LoggerFactory.getLogger(MultiClientManager.class);

  protected List<WhirlpoolClient> clients;
  protected List<MultiClientListener> listeners;

  public MultiClientManager() {
    clients = new ArrayList<WhirlpoolClient>();
    listeners = new ArrayList<MultiClientListener>();
  }

  public synchronized MultiClientListener register(WhirlpoolClient whirlpoolClient) {
    int i = clients.size() + 1;
    if (log.isDebugEnabled()) {
      log.debug("Register client#" + i);
    }
    MultiClientListener listener = new MultiClientListener("client#" + i, this);
    listener.setLogPrefix("cli#" + i);
    this.clients.add(whirlpoolClient);
    this.listeners.add(listener);
    return listener;
  }

  public void stop() {
    for (WhirlpoolClient whirlpoolClient : clients) {
      if (whirlpoolClient != null) {
        whirlpoolClient.stop(false);
      }
    }
  }

  /** @return true when success, false when failed */
  public synchronized boolean waitDone(int nbSuccessExpected) {
    do {
      if (isDone(nbSuccessExpected)) {
        return (getNbSuccess() != null);
      }

      // will be notified by listeners to wakeup
      try {
        if (log.isDebugEnabled()) {
          Integer nbSuccess = getNbSuccess();
          log.debug("waitDone... (nbSuccess=" + nbSuccess + "/" + nbSuccessExpected + ")");
        }
        wait();
      } catch (Exception e) {
      }
    } while (true);
  }

  /** @return true when success, false when failed */
  public synchronized boolean waitDone() {
    return waitDone(clients.size());
  }

  public boolean isDone() {
    return isDone(clients.size());
  }

  public boolean isDone(int nbSuccessExpected) {
    Integer nbSuccess = getNbSuccess();
    return (nbSuccess == null || nbSuccess >= nbSuccessExpected);
  }

  protected void debugClients() {
    if (log.isDebugEnabled()) {
      log.debug("%%% debugging clients states... %%%");
      int i = 0;
      for (WhirlpoolClient whirlpoolClient : clients) {
        if (whirlpoolClient != null) {
          MultiClientListener listener = listeners.get(i);
          log.debug(
              "Client#"
                  + i
                  + ": mixStatus="
                  + listener.getMixStatus()
                  + ", mixStep="
                  + listener.getMixStep());
        } else {
          log.debug("Client#" + i + ": NULL");
        }
        i++;
      }
    }
  }

  public MultiClientListener getListener(int i) {
    return listeners.get(i);
  }

  /** @return number of success clients, or null=1 client failed */
  public Integer getNbSuccess() {
    if (clients.isEmpty()) {
      return 0;
    }

    int nbSuccess = 0;
    for (int i = 0; i < clients.size(); i++) {
      MultiClientListener listener = listeners.get(i);
      if (listener == null) {
        // client not initialized => not done
        log.debug("Client#" + i + ": null");
      } else {
        log.debug(
            "Client#"
                + i
                + ": mixStatus="
                + listener.getMixStatus()
                + ", mixStep="
                + listener.getMixStep());
        if (MixStatus.FAIL.equals(listener.getMixStatus())) {
          // client failed
          return null;
        }
        if (MixStatus.SUCCESS.equals(listener.getMixStatus())) {
          // client success
          nbSuccess++;
        }
      }
    }
    // all clients success
    return nbSuccess;
  }
}
