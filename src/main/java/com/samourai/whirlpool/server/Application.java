package com.samourai.whirlpool.server;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.utils.DbUtils;
import com.samourai.whirlpool.server.utils.LogbackUtils;
import com.samourai.whirlpool.server.utils.Utils;
import java.util.Arrays;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class Application implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(Application.class);

  private static final String SETUP_SQL_FILENAME = "classpath:setup.sql";

  private static final String ARG_DEBUG = "debug";
  private static final String ARG_SETUP = "setup";

  private ApplicationArguments args;

  @Autowired private RpcClientService rpcClientService;

  @Autowired private ApplicationContext applicationContext;

  @Autowired private DbUtils dbUtils;

  @Autowired private WhirlpoolServerConfig whirlpoolServerConfig;

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Override
  public void run(ApplicationArguments args) {
    this.args = args;

    if (args.containsOption(ARG_SETUP)) {
      setup();
      exit();
    }

    if (args.containsOption(ARG_DEBUG)) {
      // enable debug logs
      Utils.setLoggerDebug("com.samourai.whirlpool.server");
      // Utils.setLoggerDebug("org.springframework.security");

      // skip noisy logs in debug mode
      LogbackUtils.setLogLevel(
          "org.springframework.boot.web.servlet.filter.OrderedRequestContextFilter",
          Level.INFO.toString());
      LogbackUtils.setLogLevel(
          "org.springframework.web.socket.config.WebSocketMessageBrokerStats",
          Level.ERROR.toString());
    }

    if (!rpcClientService.testConnectivity()) {
      exit();
    }

    log.info("------------ whirlpool-server ------------");
    log.info(
        "Running whirlpool-server {} on java {}",
        Arrays.toString(args.getSourceArgs()),
        System.getProperty("java.version"));
    for (Map.Entry<String, String> entry : whirlpoolServerConfig.getConfigInfo().entrySet()) {
      log.info("config: " + entry.getKey() + ": " + entry.getValue());
    }
  }

  private void exit() {
    final int exitCode = 1;
    SpringApplication.exit(applicationContext, () -> exitCode);
    System.exit(exitCode);
  }

  private void setup() {
    try {
      log.info("ENTERING SETUP...");

      // setup database
      dbUtils.runSqlFile(SETUP_SQL_FILENAME);

      log.info("SETUP SUCCESS.");
    } catch (Exception e) {
      log.error("SETUP ERROR", e);
    }
  }
}
