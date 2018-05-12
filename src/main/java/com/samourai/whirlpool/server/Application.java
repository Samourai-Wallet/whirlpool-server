package com.samourai.whirlpool.server;

import com.samourai.whirlpool.server.utils.LogbackUtils;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(Application.class);

	private static final String ARG_DEBUG = "debug";

	private ApplicationArguments args;

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

		log.info("------------ whirlpool-server ------------");
	}
}
