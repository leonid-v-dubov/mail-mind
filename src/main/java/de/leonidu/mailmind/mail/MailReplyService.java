package de.leonidu.mailmind.mail;

import de.leonidu.mailmind.mail.config.MailImapProperties;
import de.leonidu.mailmind.mail.model.ParsedEmail;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Properties;

@Service
public class MailReplyService {

	private static final Logger log = LoggerFactory.getLogger(MailReplyService.class);
	private static final String GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

	private final GmailAuthService gmailAuthService;
	private final MailImapProperties imapProperties;
	private final RestTemplate restTemplate;

	public MailReplyService(GmailAuthService gmailAuthService, MailImapProperties imapProperties, RestTemplate restTemplate) {
		this.gmailAuthService = gmailAuthService;
		this.imapProperties = imapProperties;
		this.restTemplate = restTemplate;
	}

	public void reply(ParsedEmail original, String body) {
		String to = extractEmailAddress(original.from());
		if (to == null || to.isBlank()) {
			throw new IllegalStateException("Cannot reply: original From address is missing (uid=" + original.uid() + ")");
		}

		try {
			String accessToken = gmailAuthService.getAccessToken();
			String rawMessage = createRawMessage(to, imapProperties.username(), replySubject(original.subject()), body, original.messageId());
			String base64UrlEncoded = Base64.getUrlEncoder().encodeToString(rawMessage.getBytes());

			GmailSendRequest request = new GmailSendRequest(base64UrlEncoded);
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(accessToken);
			
			HttpEntity<GmailSendRequest> entity = new HttpEntity<>(request, headers);
			
			restTemplate.postForObject(GMAIL_SEND_URL, entity, GmailSendResponse.class);
			
			log.info("Sent reply to uid={} messageId={} to={}", original.uid(), original.messageId(), to);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to send reply for uid=" + original.uid(), ex);
		}
	}

	private String createRawMessage(String to, String from, String subject, String body, String inReplyTo) throws Exception {
		Properties props = new Properties();
		Session session = Session.getInstance(props);
		MimeMessage message = new MimeMessage(session);
		
		message.setFrom(new InternetAddress(from));
		message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(to));
		message.setSubject(subject, "UTF-8");
		message.setText(body, "UTF-8");
		
		if (inReplyTo != null && !inReplyTo.isBlank()) {
			message.setHeader("In-Reply-To", inReplyTo);
			message.setHeader("References", inReplyTo);
		}
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		message.writeTo(outputStream);
		return outputStream.toString();
	}

	static String replySubject(String subject) {
		if (subject == null || subject.isBlank()) {
			return "Re:";
		}
		String trimmed = subject.trim();
		if (trimmed.regionMatches(true, 0, "Re:", 0, 3)) {
			return trimmed;
		}
		return "Re: " + trimmed;
	}

	static String extractEmailAddress(String from) {
		if (from == null || from.isBlank()) {
			return null;
		}
		try {
			InternetAddress[] addresses = InternetAddress.parse(from, false);
			if (addresses.length > 0 && addresses[0].getAddress() != null) {
				return addresses[0].getAddress();
			}
		}
		catch (Exception ignored) {
			// fall through
		}
		int start = from.indexOf('<');
		int end = from.indexOf('>');
		if (start >= 0 && end > start) {
			return from.substring(start + 1, end).trim();
		}
		return from.trim();
	}

	private record GmailSendRequest(
		@JsonProperty("raw") String raw
	) {}

	private record GmailSendResponse(
		@JsonProperty("id") String id,
		@JsonProperty("threadId") String threadId
	) {}
}
