package de.leonidu.mailmind.mind.mail;

import de.leonidu.mailmind.mind.mail.model.ParsedEmail;

/**
 * Hook for downstream processing (Kafka → AI agent later).
 */
@FunctionalInterface
public interface MailMessageHandler {

	void handle(ParsedEmail email);
}
