package com.app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracao TEMPORARIA de seguranca para o Epico 1 (Setup e Infraestrutura).
 *
 * <p>Neste ponto ainda nao existe autenticacao: todas as rotas sao publicas
 * (permitAll) para que a aplicacao suba e o endpoint de health possa ser
 * validado antes de qualquer feature de dominio.
 *
 * <p>SUBSTITUIR no Epico 2 (Autenticacao e Gestao de Casais): introduzir o
 * filtro JWT, CORS para o frontend e proteger as rotas privadas
 * (ver {@code docs/ARCHITECTURE.md} - com.app.security).
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable());
		return http.build();
	}
}
