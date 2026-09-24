package com.limasegura.limasegurabackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LimaSeguraBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LimaSeguraBackendApplication.class, args);
	}
}