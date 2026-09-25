package de.leonidu.mailmind.mail.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MailImapProperties.class, GmailProperties.class})
@Import({RestTemplateConfig.class})
public class MailConfig {
}
