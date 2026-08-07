package com.app.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de rate limit por endpoint (FR-2). Cada limite expoe
 * capacidade (numero de requisicoes permitidas) e a janela de tempo em que
 * essa capacidade e restaurada.
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

	private boolean enabled = true;

	private Limit login = new Limit(5, Duration.ofMinutes(1));
	private Limit register = new Limit(3, Duration.ofMinutes(1));
	private Limit refresh = new Limit(30, Duration.ofMinutes(1));
	private Limit coupleJoin = new Limit(5, Duration.ofMinutes(1));
	private Limit loginByEmail = new Limit(10, Duration.ofHours(1));
	private Limit coupleJoinByUser = new Limit(20, Duration.ofHours(1));
	private Limit coupleDissolve = new Limit(5, Duration.ofHours(1));

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Limit getLogin() {
		return login;
	}

	public void setLogin(Limit login) {
		this.login = login;
	}

	public Limit getRegister() {
		return register;
	}

	public void setRegister(Limit register) {
		this.register = register;
	}

	public Limit getRefresh() {
		return refresh;
	}

	public void setRefresh(Limit refresh) {
		this.refresh = refresh;
	}

	public Limit getCoupleJoin() {
		return coupleJoin;
	}

	public void setCoupleJoin(Limit coupleJoin) {
		this.coupleJoin = coupleJoin;
	}

	public Limit getLoginByEmail() {
		return loginByEmail;
	}

	public void setLoginByEmail(Limit loginByEmail) {
		this.loginByEmail = loginByEmail;
	}

	public Limit getCoupleJoinByUser() {
		return coupleJoinByUser;
	}

	public void setCoupleJoinByUser(Limit coupleJoinByUser) {
		this.coupleJoinByUser = coupleJoinByUser;
	}

	public Limit getCoupleDissolve() {
		return coupleDissolve;
	}

	public void setCoupleDissolve(Limit coupleDissolve) {
		this.coupleDissolve = coupleDissolve;
	}

	public static class Limit {

		private int capacity;
		private Duration window;

		public Limit() {
		}

		public Limit(int capacity, Duration window) {
			this.capacity = capacity;
			this.window = window;
		}

		public int getCapacity() {
			return capacity;
		}

		public void setCapacity(int capacity) {
			this.capacity = capacity;
		}

		public Duration getWindow() {
			return window;
		}

		public void setWindow(Duration window) {
			this.window = window;
		}
	}
}
