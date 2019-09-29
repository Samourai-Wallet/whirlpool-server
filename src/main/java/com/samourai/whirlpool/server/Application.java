package com.samourai.whirlpool.server;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.utils.DbUtils;
import com.samourai.whirlpool.server.utils.Utils;
import java.util.Arrays;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
@ServletComponentScan(value = "com.samourai.whirlpool.server.config.filters")
public class Application implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(Application.class);

  private static final String ARG_DEBUG = "debugserver";

  private ApplicationArguments args;

  @Autowired Environment env;

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

    if (args.containsOption(ARG_DEBUG)) {
      // enable debug logs
      Utils.setLoggerDebug();
    }

    try {
      whirlpoolServerConfig.validate();
    } catch (Exception e) {
      System.err.println("ERROR: invalid server configuration: " + e.getMessage());
      exit();
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
    log.info("profiles: " + Arrays.toString(env.getActiveProfiles()));
  }

  private void exit() {
    final int exitCode = 1;
    SpringApplication.exit(applicationContext, () -> exitCode);
    System.exit(exitCode);
  }
}
