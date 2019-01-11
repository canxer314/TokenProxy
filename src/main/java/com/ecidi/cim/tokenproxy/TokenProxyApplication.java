package com.ecidi.cim.tokenproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class TokenProxyApplication {

	public static void main(String[] args) {
		SpringApplication.run(TokenProxyApplication.class, args);
	}

}
