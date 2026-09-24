package de.leonidu.mailmind.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mail.imap")
public record MailImapProperties(
		boolean enabled,
		String host,
		int port,
		String username,
		String password,
		boolean ssl,
		String folder,
		Duration pollInterval,
		boolean markAsSeen,
		/** Only process messages received within this window (default 1 minute). */
		Duration maxAge,
		/** Monitoring mode: 'idle' for push notifications (default), 'polling' for periodic checks */
		String mode
) {
	public MailImapProperties {
		if (port == 0) {
			port = 993;
		}
		if (folder == null || folder.isBlank()) {
			folder = "INBOX";
		}
		if (pollInterval == null) {
			pollInterval = Duration.ofSeconds(15);
		}
		if (maxAge == null) {
			maxAge = Duration.ofMinutes(1);
		}
		if (mode == null || mode.isBlank()) {
			mode = "idle";
		}
	}
}
