package de.leonidu.mailmind.mail.model;

import java.time.Instant;
import java.util.List;

/**
 * Fully parsed inbox message, ready for downstream processing (e.g. Kafka).
 */
public record ParsedEmail(
		long uid,
		String messageId,
		String subject,
		String from,
		List<String> to,
		List<String> cc,
		Instant receivedAt,
		String textBody,
		String htmlBody
) {
}
