package com.app;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint publico de health-check usado para validar que a aplicacao sobe e
 * responde antes de qualquer feature de dominio (Epico 1). Desde o Epico 12
 * (US-002) delega ao {@link HealthEndpoint} do Actuator para saber se o banco
 * esta acessivel, em vez de devolver uma constante - o {@code HealthEndpoint}
 * continua existindo como bean mesmo com {@code management.endpoints.web.exposure.include}
 * vazio (US-001): a exposicao so controla o mapeamento HTTP sob
 * {@code /actuator/**}, nao a existencia do bean.
 */
@RestController
public class HealthController {

	private static final Map<String, String> UP_BODY = Map.of("status", "UP", "db", "UP");
	private static final Map<String, String> DOWN_BODY = Map.of("status", "DOWN", "db", "DOWN");

	/**
	 * Orcamento de espera pela checagem do banco. O connection-timeout do
	 * Hikari (30s) fica intocado de proposito - baixa-lo globalmente faria
	 * requisicao real de usuario falhar sob contencao normal do pool. Esta
	 * espera curta mora so aqui, no probe.
	 */
	private static final long TIMEOUT_MILLIS = 2000;

	private final HealthEndpoint healthEndpoint;
	private final ExecutorService healthCheckExecutor;

	public HealthController(HealthEndpoint healthEndpoint,
			@Qualifier(HealthCheckExecutorConfig.EXECUTOR_BEAN_NAME) ExecutorService healthCheckExecutor) {
		this.healthEndpoint = healthEndpoint;
		this.healthCheckExecutor = healthCheckExecutor;
	}

	@GetMapping("/api/health")
	public ResponseEntity<Map<String, String>> health() {
		if (isDatabaseUp()) {
			return ResponseEntity.ok(UP_BODY);
		}
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(DOWN_BODY);
	}

	/**
	 * Roda a checagem num executor limitado e daemon (nao no thread da
	 * requisicao) para poder abandona-la apos {@value #TIMEOUT_MILLIS}ms sem
	 * acumular uma thread nova por probe se o banco estiver pendurado - o
	 * container sonda a cada 30s e o monitor externo a cada 5min. Qualquer
	 * excecao (timeout, falha na checagem em si) so decide UP/DOWN; nenhum
	 * detalhe dela (mensagem, causa) chega na resposta.
	 */
	private boolean isDatabaseUp() {
		Future<Status> future = healthCheckExecutor.submit(() -> healthEndpoint.health().getStatus());
		try {
			return Status.UP.equals(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
		}
		catch (Exception ex) {
			future.cancel(true);
			return false;
		}
	}
}
