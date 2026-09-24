package de.leonidu.mailmind.mind.mail;

import de.leonidu.mailmind.mind.mail.config.MailImapProperties;
import de.leonidu.mailmind.mind.mail.model.ParsedEmail;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailReplyService {

	private static final Logger log = LoggerFactory.getLogger(MailReplyService.class);

	private final JavaMailSender mailSender;
	private final MailImapProperties imapProperties;

	public MailReplyService(JavaMailSender mailSender, MailImapProperties imapProperties) {
		this.mailSender = mailSender;
		this.imapProperties = imapProperties;
	}

	public void reply(ParsedEmail original, String body) {
		String to = extractEmailAddress(original.from());
		if (to == null || to.isBlank()) {
			throw new IllegalStateException("Cannot reply: original From address is missing (uid=" + original.uid() + ")");
		}

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
			helper.setTo(to);
			helper.setFrom(imapProperties.username());
			helper.setSubject(replySubject(original.subject()));
			helper.setText(body, false);

			if (original.messageId() != null && !original.messageId().isBlank()) {
				message.setHeader("In-Reply-To", original.messageId());
				message.setHeader("References", original.messageId());
			}

			mailSender.send(message);
			log.info("Sent reply to uid={} messageId={} to={}", original.uid(), original.messageId(), to);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to send reply for uid=" + original.uid(), ex);
		}
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
}
