package de.leonidu.mailmind.mail.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.annotation.PostConstruct;

@Configuration
@ConditionalOnBean(JavaMailSender.class)
public class MailSenderCredentialsConfig {

	private final JavaMailSender mailSender;
	private final MailImapProperties imapProperties;

	public MailSenderCredentialsConfig(JavaMailSender mailSender, MailImapProperties imapProperties) {
		this.mailSender = mailSender;
		this.imapProperties = imapProperties;
	}

	@PostConstruct
	void applyImapCredentialsToSmtp() {
		if (!(mailSender instanceof JavaMailSenderImpl sender)) {
			return;
		}
		if (imapProperties.username() != null && !imapProperties.username().isBlank()) {
			sender.setUsername(imapProperties.username());
		}
		if (imapProperties.password() != null && !imapProperties.password().isBlank()) {
			sender.setPassword(imapProperties.password());
		}
	}
}
