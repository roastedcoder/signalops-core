package com.signalops.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SignalopsCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(SignalopsCoreApplication.class, args);
	}

}
