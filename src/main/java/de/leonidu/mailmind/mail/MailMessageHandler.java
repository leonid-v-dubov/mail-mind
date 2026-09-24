package de.leonidu.mailmind.mail;

import de.leonidu.mailmind.mail.model.ParsedEmail;

/**
 * Hook for downstream processing (Kafka → AI agent later).
 */
@FunctionalInterface
public interface MailMessageHandler {

	void handle(ParsedEmail email);
}
