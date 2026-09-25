package de.leonidu.mailmind.mail;

import de.leonidu.mailmind.ai.AiProvider;
import de.leonidu.mailmind.ai.AiResponse;
import de.leonidu.mailmind.ai.SenderChatSessionStore;
import de.leonidu.mailmind.mail.model.ParsedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class AiReplyMailMessageHandler implements MailMessageHandler {

	private static final Logger log = LoggerFactory.getLogger(AiReplyMailMessageHandler.class);

	private final AiProvider aiProvider;
	private final SenderChatSessionStore senderChatSessionStore;
	private final MailReplyService mailReplyService;

	public AiReplyMailMessageHandler(
			AiProvider aiProvider,
			SenderChatSessionStore senderChatSessionStore,
			MailReplyService mailReplyService
	) {
		this.aiProvider = aiProvider;
		this.senderChatSessionStore = senderChatSessionStore;
		this.mailReplyService = mailReplyService;
	}

	@Override
	public void handle(ParsedEmail email) {
		try {
			String body = resolveBody(email);
			if (body == null || body.isBlank()) {
				log.warn("Skipping uid={}: email has no text/html body", email.uid());
				return;
			}

			String sender = MailReplyService.extractEmailAddress(email.from());
			if (sender == null || sender.isBlank()) {
				log.warn("Skipping uid={}: cannot resolve sender address", email.uid());
				return;
			}

			String previousResponseId = senderChatSessionStore.findLastResponseId(sender).orElse(null);
			log.info(
					"Requesting AI reply via {} for uid={} sender={} subject={} continued={}",
					aiProvider.getProviderName(),
					email.uid(),
					sender,
					email.subject(),
					previousResponseId != null
			);

			String prompt = buildPrompt(email, body);
			AiResponse response = aiProvider.complete(prompt, previousResponseId);
			senderChatSessionStore.remember(sender, response.id());
			mailReplyService.reply(email, response.text());
		}
		catch (HttpClientErrorException.TooManyRequests ex) {
			log.error("AI rate limit reached for uid={}: {}", email.uid(), ex.getMessage());
			mailReplyService.reply(email, "The limit on AI requests has been exceeded");
		}
		catch (RuntimeException ex) {
			log.error("Failed to process AI reply for uid={}", email.uid(), ex);
		}
	}

	private static String resolveBody(ParsedEmail email) {
		if (email.textBody() != null && !email.textBody().isBlank()) {
			return email.textBody();
		}
		return email.htmlBody();
	}

	private static String buildPrompt(ParsedEmail email, String body) {
		return """
				From: %s
				Subject: %s

				%s
				""".formatted(
				nullToEmpty(email.from()),
				nullToEmpty(email.subject()),
				body
		);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
