package com.limasegura.limasegurabackend;

import org.springframework.boot.SpringApplication;

public class TestLimaSeguraBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(LimaSeguraBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
