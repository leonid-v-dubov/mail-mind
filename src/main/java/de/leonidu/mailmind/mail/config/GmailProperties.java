package de.leonidu.mailmind.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gmail")
public record GmailProperties(
		String clientId,
		String clientSecret,
		String refreshToken
) {
}