package de.leonidu.mailmind.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mail.imap", name = "enabled", havingValue = "true")
public class MailInboxPoller {

	private static final Logger log = LoggerFactory.getLogger(MailInboxPoller.class);

	private final MailFetchService mailFetchService;

	public MailInboxPoller(MailFetchService mailFetchService) {
		this.mailFetchService = mailFetchService;
	}

	@Scheduled(fixedDelayString = "${mail.imap.poll-interval}")
	public void poll() {
		try {
			var emails = mailFetchService.fetchAndDispatchNewMessages();
			if (!emails.isEmpty()) {
				log.info("Dispatched {} newly parsed email(s)", emails.size());
			}
		}
		catch (RuntimeException ex) {
			log.error("IMAP inbox poll failed", ex);
		}
	}
}
