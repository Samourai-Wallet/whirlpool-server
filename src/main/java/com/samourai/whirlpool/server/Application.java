package com.samourai.whirlpool.server;

import com.samourai.javaserver.config.ServerConfig;
import com.samourai.javaserver.run.ServerApplication;
import com.samourai.javaserver.utils.LogbackUtils;
import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.DbService;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.utils.Utils;
import com.samourai.xmanager.client.XManagerClient;
import com.samourai.xmanager.protocol.XManagerService;
import com.samourai.xmanager.protocol.rest.AddressIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan(value = "com.samourai.whirlpool.server.config.filters")
public class Application extends ServerApplication {
  private static final Logger log = LoggerFactory.getLogger(Application.class);

  @Autowired private ServerUtils serverUtils;

  @Autowired private RpcClientService rpcClientService;

  @Autowired private WhirlpoolServerConfig serverConfig;

  @Autowired private XManagerClient xManagerClient;
  @Autowired private DbService dbService;

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Override
  public void runServer() throws Exception {
    // check RPC connectivity
    if (!rpcClientService.testConnectivity()) {
      throw new Exception("RPC connexion failed");
    }

    // check XM connectivity
    AddressIndexResponse addressIndexResponse =
        xManagerClient.getAddressIndexOrDefault(XManagerService.WHIRLPOOL);
    log.info("XM index: " + addressIndexResponse.index);

    // server starting...
    dbService.fixMixHistory();

    exit();
  }

  @Override
  protected ServerConfig getServerConfig() {
    return serverConfig;
  }

  @Override
  protected void setLoggerDebug() {
    Utils.setLoggerDebug();

    // skip noisy logs
    LogbackUtils.setLogLevel(
        "org.springframework.web.socket.config.WebSocketMessageBrokerStats",
        Level.ERROR.toString());
  }
}
