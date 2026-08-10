package com.app.user;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.auth.EmailAlreadyExistsException;
import com.app.common.ResourceNotFoundException;
import com.app.security.RateLimitExceededException;
import com.app.security.RateLimitProperties;
import com.app.security.RateLimitProperties.Limit;
import com.app.security.RateLimitService;
import com.app.security.RateLimitService.RateLimitResult;
import com.app.security.SecurityAuditLogger;

/**
 * Edicao do proprio perfil (epico 9, US-005). Primeira regra de negocio de
 * {@code com.app.user}, que ate aqui so tinha a entidade e o repositorio.
 *
 * <p>Nao ha verificacao do e-mail novo por link (E9.11): a troca e direta, e a colisao com
 * outra conta continua sendo {@code 409} (D4). A sessao NAO cai depois da troca - o token
 * carrega {@code userId}, nunca o e-mail.
 */
@Service
public class UserProfileService {

	private final UserRepository userRepository;
	private final RateLimitService rateLimitService;
	private final RateLimitProperties rateLimitProperties;
	private final SecurityAuditLogger securityAuditLogger;

	public UserProfileService(UserRepository userRepository, RateLimitService rateLimitService,
			RateLimitProperties rateLimitProperties, SecurityAuditLogger securityAuditLogger) {
		this.userRepository = userRepository;
		this.rateLimitService = rateLimitService;
		this.rateLimitProperties = rateLimitProperties;
		this.securityAuditLogger = securityAuditLogger;
	}

	/**
	 * Aplica so os campos presentes no corpo (semantica de PATCH). Um campo ausente ou com o
	 * mesmo valor atual nao conta como alteracao - nem para o {@code save}, nem para a linha de
	 * auditoria.
	 */
	@Transactional
	public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		enforceProfileUpdateRateLimit(userId);

		User user = loadUser(userId);
		List<String> changedFields = new ArrayList<>();

		if (request.name() != null && !request.name().equals(user.getName())) {
			user.setName(request.name());
			changedFields.add("name");
		}

		if (request.email() != null && !request.email().equals(user.getEmail())) {
			if (userRepository.existsByEmail(request.email())) {
				throw new EmailAlreadyExistsException();
			}
			user.setEmail(request.email());
			changedFields.add("email");
		}

		if (changedFields.isEmpty()) {
			return UserProfileResponse.from(user);
		}

		User saved = userRepository.save(user);
		// So QUAIS campos mudaram: o valor novo do e-mail nunca entra no log de auditoria.
		securityAuditLogger.profileUpdated(userId, changedFields);
		return UserProfileResponse.from(saved);
	}

	private User loadUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("usuario nao encontrado"));
	}

	/**
	 * Limite POR USUARIO ({@code app.rate-limit.profile-update}, 10/hora, E9.3). Nao passa pelo
	 * {@code RateLimitFilter}, que chaveia por IP e casa metodo+path.
	 */
	private void enforceProfileUpdateRateLimit(UUID userId) {
		Limit limit = rateLimitProperties.getProfileUpdate();
		RateLimitResult result = rateLimitService.tryConsume("profile-update:user:" + userId, limit.getCapacity(),
				limit.getWindow());
		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}
	}
}
