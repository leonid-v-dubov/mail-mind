package de.leonidu.mailmind.mind.ai;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps sender email address → last OpenAI response id (one chat per sender).
 * In-memory only; cleared on application restart.
 */
@Component
public class SenderChatSessionStore {

	private final ConcurrentHashMap<String, String> lastResponseIdBySender = new ConcurrentHashMap<>();

	public Optional<String> findLastResponseId(String senderEmail) {
		if (senderEmail == null || senderEmail.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(lastResponseIdBySender.get(normalize(senderEmail)));
	}

	public void remember(String senderEmail, String responseId) {
		if (senderEmail == null || senderEmail.isBlank() || responseId == null || responseId.isBlank()) {
			return;
		}
		lastResponseIdBySender.put(normalize(senderEmail), responseId);
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase();
	}
}
