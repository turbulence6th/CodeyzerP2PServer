package com.codeyzer.p2p;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CodeyzerP2PApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodeyzerP2PApplication.class, args);
	}
}
