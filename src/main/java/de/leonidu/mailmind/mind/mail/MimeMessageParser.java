package de.leonidu.mailmind.mind.mail;

import de.leonidu.mailmind.mind.mail.model.ParsedEmail;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

final class MimeMessageParser {

	private MimeMessageParser() {
	}

	static ParsedEmail parse(UIDFolder folder, Message message) throws MessagingException, IOException {
		long uid = folder.getUID(message);
		String messageId = firstHeader(message, "Message-ID");
		String subject = message.getSubject();
		String from = formatAddress(first(message.getFrom()));
		List<String> to = formatAddresses(message.getRecipients(Message.RecipientType.TO));
		List<String> cc = formatAddresses(message.getRecipients(Message.RecipientType.CC));
		Instant receivedAt = toInstant(message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate());

		StringBuilder text = new StringBuilder();
		StringBuilder html = new StringBuilder();
		extractContent(message, text, html);

		return new ParsedEmail(
				uid,
				messageId,
				subject,
				from,
				List.copyOf(to),
				List.copyOf(cc),
				receivedAt,
				blankToNull(text.toString()),
				blankToNull(html.toString())
		);
	}

	private static void extractContent(Part part, StringBuilder text, StringBuilder html)
			throws MessagingException, IOException {
		if (part.isMimeType("text/plain") && !isAttachment(part)) {
			appendContent(text, part.getContent());
			return;
		}
		if (part.isMimeType("text/html") && !isAttachment(part)) {
			appendContent(html, part.getContent());
			return;
		}
		if (part.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) part.getContent();
			for (int i = 0; i < multipart.getCount(); i++) {
				BodyPart bodyPart = multipart.getBodyPart(i);
				extractContent(bodyPart, text, html);
			}
		}
	}

	private static boolean isAttachment(Part part) throws MessagingException {
		String disposition = part.getDisposition();
		return disposition != null && disposition.equalsIgnoreCase(Part.ATTACHMENT);
	}

	private static void appendContent(StringBuilder target, Object content) {
		if (content == null) {
			return;
		}
		if (!target.isEmpty()) {
			target.append('\n');
		}
		target.append(content);
	}

	private static String firstHeader(Message message, String name) throws MessagingException {
		String[] values = message.getHeader(name);
		if (values == null || values.length == 0) {
			return null;
		}
		return values[0];
	}

	private static Address first(Address[] addresses) {
		return addresses == null || addresses.length == 0 ? null : addresses[0];
	}

	private static String formatAddress(Address address) {
		if (address == null) {
			return null;
		}
		if (address instanceof InternetAddress internetAddress) {
			String personal = internetAddress.getPersonal();
			String email = internetAddress.getAddress();
			if (personal == null || personal.isBlank()) {
				return email;
			}
			return personal + " <" + email + ">";
		}
		return address.toString();
	}

	private static List<String> formatAddresses(Address[] addresses) {
		if (addresses == null || addresses.length == 0) {
			return List.of();
		}
		List<String> result = new ArrayList<>(addresses.length);
		Arrays.stream(addresses).map(MimeMessageParser::formatAddress).forEach(result::add);
		return result;
	}

	private static Instant toInstant(Date date) {
		return date == null ? null : date.toInstant();
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
