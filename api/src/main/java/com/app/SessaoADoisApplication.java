package com.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UserDetailsServiceAutoConfiguration e excluida porque a aplicacao usa
 * autenticacao 100% via JWT (com.app.security) - sem ela, o Spring Boot
 * criaria um UserDetailsService in-memory com senha gerada a cada boot,
 * logada em texto claro, e abriria uma porta acidental para httpBasic.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class SessaoADoisApplication {

	public static void main(String[] args) {
		SpringApplication.run(SessaoADoisApplication.class, args);
	}

}
