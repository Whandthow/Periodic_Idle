package com.periodic.idle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PeriodicIdleApplication {

	public static void main(String[] args) {
		SpringApplication.run(PeriodicIdleApplication.class, args);
		SpringApplication.run(PeriodicIdleApplication.class, args);
	}
}
