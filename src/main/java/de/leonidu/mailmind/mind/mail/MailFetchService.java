package de.leonidu.mailmind.mind.mail;

import de.leonidu.mailmind.mind.mail.config.MailImapProperties;
import de.leonidu.mailmind.mind.mail.model.ParsedEmail;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MailFetchService {

	private static final Logger log = LoggerFactory.getLogger(MailFetchService.class);

	private final MailImapProperties properties;
	private final MailMessageHandler messageHandler;
	/** Highest IMAP UID successfully processed in this JVM process. */
	private final AtomicLong lastProcessedUid = new AtomicLong(0);

	public MailFetchService(MailImapProperties properties, MailMessageHandler messageHandler) {
		this.properties = properties;
		this.messageHandler = messageHandler;
	}

	/**
	 * Fetches new INBOX messages, parses each immediately, then hands off to {@link MailMessageHandler}.
	 */
	public List<ParsedEmail> fetchAndDispatchNewMessages() {
		List<ParsedEmail> parsed = fetchNewMessages();
		for (ParsedEmail email : parsed) {
			messageHandler.handle(email);
		}
		return parsed;
	}

	/**
	 * Fetches and parses INBOX messages received within {@link MailImapProperties#maxAge()},
	 * skipping UIDs already processed in this process.
	 */
	public List<ParsedEmail> fetchNewMessages() {
		requireConfigured();

		Properties sessionProps = new Properties();
		String protocol = properties.ssl() ? "imaps" : "imap";
		sessionProps.put("mail.store.protocol", protocol);
		sessionProps.put("mail." + protocol + ".host", properties.host());
		sessionProps.put("mail." + protocol + ".port", String.valueOf(properties.port()));
		if (properties.ssl()) {
			sessionProps.put("mail.imaps.ssl.enable", "true");
		}

		Session session = Session.getInstance(sessionProps);
		List<ParsedEmail> result = new ArrayList<>();
		Instant cutoff = Instant.now().minus(properties.maxAge());

		try (Store store = session.getStore(protocol)) {
			log.debug("Connecting to {}:{}/{} as {}", properties.host(), properties.port(), protocol, properties.username());
			store.connect(properties.host(), properties.username(), properties.password());

			int openMode = properties.markAsSeen() ? Folder.READ_WRITE : Folder.READ_ONLY;
			Folder folder = store.getFolder(properties.folder());
			folder.open(openMode);
			try {
				if (!(folder instanceof UIDFolder uidFolder)) {
					throw new IllegalStateException("IMAP folder does not support UIDs: " + properties.folder());
				}

				Message[] candidates = selectRecentCandidates(folder, cutoff);
				log.info(
						"INBOX messages={}, candidates={}, maxAge={}, cutoff={}, lastProcessedUid={}",
						folder.getMessageCount(),
						candidates.length,
						properties.maxAge(),
						cutoff,
						lastProcessedUid.get()
				);

				for (Message message : candidates) {
					if (message == null) {
						continue;
					}
					long uid = uidFolder.getUID(message);
					if (uid <= lastProcessedUid.get()) {
						continue;
					}
					if (!receivedWithinWindow(message, cutoff)) {
						lastProcessedUid.updateAndGet(current -> Math.max(current, uid));
						continue;
					}

					ParsedEmail email = MimeMessageParser.parse(uidFolder, message);
					result.add(email);
					lastProcessedUid.updateAndGet(current -> Math.max(current, uid));

					if (properties.markAsSeen()) {
						message.setFlag(Flags.Flag.SEEN, true);
					}
				}
			}
			finally {
				folder.close(false);
			}
		}
		catch (jakarta.mail.AuthenticationFailedException ex) {
			throw new IllegalStateException(
					"IMAP authentication failed for user '%s' at %s:%d — check username/app password and that IMAP is enabled"
							.formatted(properties.username(), properties.host(), properties.port()),
					ex
			);
		}
		catch (MessagingException | IOException ex) {
			throw new IllegalStateException("Failed to fetch mail from IMAP inbox", ex);
		}

		return result;
	}

	/**
	 * IMAP date search is day-granular, so search with a 1-day cushion and filter by time locally.
	 */
	private Message[] selectRecentCandidates(Folder folder, Instant cutoff) throws MessagingException {
		Date searchSince = Date.from(cutoff.minus(Duration.ofDays(1)));
		Message[] found = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, searchSince));
		return found == null ? new Message[0] : found;
	}

	private static boolean receivedWithinWindow(Message message, Instant cutoff) throws MessagingException {
		Date received = message.getReceivedDate();
		if (received == null) {
			received = message.getSentDate();
		}
		if (received == null) {
			return false;
		}
		return !received.toInstant().isBefore(cutoff);
	}

	private void requireConfigured() {
		if (properties.host() == null || properties.host().isBlank()) {
			throw new IllegalStateException("mail.imap.host is not configured");
		}
		if (properties.username() == null || properties.username().isBlank()) {
			throw new IllegalStateException("mail.imap.username is not configured");
		}
		if (properties.password() == null || properties.password().isBlank()) {
			throw new IllegalStateException("mail.imap.password is not configured");
		}
	}
}
