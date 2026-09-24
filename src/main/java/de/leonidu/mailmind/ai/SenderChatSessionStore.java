package de.leonidu.mailmind.ai;

import de.leonidu.mailmind.ai.SenderChatSessionEntity;
import de.leonidu.mailmind.ai.SenderChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persists sender email → last OpenAI response id (one chat per sender) in PostgreSQL.
 */
@Service
public class SenderChatSessionStore {

	private final SenderChatSessionRepository repository;
	public SenderChatSessionStore(SenderChatSessionRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public Optional<String> findLastResponseId(String senderEmail) {
		if (senderEmail == null || senderEmail.isBlank()) {
			return Optional.empty();
		}
		return repository.findById(normalize(senderEmail)).map(SenderChatSessionEntity::getLastResponseId);
	}

	@Transactional
	public void remember(String senderEmail, String responseId) {
		if (senderEmail == null || senderEmail.isBlank() || responseId == null || responseId.isBlank()) {
			return;
		}

		String key = normalize(senderEmail);
		SenderChatSessionEntity session = repository.findById(key)
				.orElseGet(() -> new SenderChatSessionEntity(key, responseId));
		session.setLastResponseId(responseId);
		repository.save(session);
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase();
	}
}
