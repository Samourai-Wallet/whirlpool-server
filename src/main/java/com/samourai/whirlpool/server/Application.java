package com.samourai.whirlpool.server;

import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.utils.LogbackUtils;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class Application implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(Application.class);

	private static final String ARG_DEBUG = "debug";

	private ApplicationArguments args;

	@Autowired
	private BlockchainDataService blockchainDataService;

	@Autowired
	private ApplicationContext applicationContext;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Override
	public void run(ApplicationArguments args) {
		this.args = args;

		if (args.containsOption(ARG_DEBUG)) {
			// enable debug logs
			LogbackUtils.setLogLevel("com.samourai.whirlpool.server", Level.DEBUG.toString());
		}

		if (!blockchainDataService.testConnectivity()) {
			int exitCode = 1;
			SpringApplication.exit(applicationContext, () -> exitCode);
			System.exit(exitCode);
		}

		log.info("------------ whirlpool-server ------------");
	}
}
