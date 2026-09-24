package de.leonidu.mailmind.mail;

import de.leonidu.mailmind.mail.config.MailImapProperties;
import de.leonidu.mailmind.mail.model.ParsedEmail;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class MailFetchService {

	private static final Logger log = LoggerFactory.getLogger(MailFetchService.class);

	private final MailImapProperties properties;
	private final MailMessageHandler messageHandler;
	private final LastProcessedUidRepository lastProcessedUidRepository;

	public MailFetchService(MailImapProperties properties, MailMessageHandler messageHandler, 
	                       LastProcessedUidRepository lastProcessedUidRepository) {
		this.properties = properties;
		this.messageHandler = messageHandler;
		this.lastProcessedUidRepository = lastProcessedUidRepository;
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
	 * Fetches and parses INBOX messages, skipping UIDs already processed.
	 */
	@Transactional
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
		
		// Get last processed UID from database
		long lastProcessedUid = getLastProcessedUid();

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

				// Get all messages in the folder
				Message[] messages = folder.getMessages();
				log.info(
						"INBOX messages={}, lastProcessedUid={}",
						folder.getMessageCount(),
						lastProcessedUid
				);

				for (Message message : messages) {
					if (message == null) {
						continue;
					}
					long uid = uidFolder.getUID(message);
					if (uid <= lastProcessedUid) {
						continue;
					}

					ParsedEmail email = MimeMessageParser.parse(uidFolder, message);
					result.add(email);
					lastProcessedUid = uid;

					if (properties.markAsSeen()) {
						message.setFlag(Flags.Flag.SEEN, true);
					}
				}
				
				// Save the last processed UID to database
				if (lastProcessedUid > 0) {
					saveLastProcessedUid(lastProcessedUid);
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

	private long getLastProcessedUid() {
		return lastProcessedUidRepository.findById(properties.folder())
				.map(LastProcessedUidEntity::getLastUid)
				.orElse(0L);
	}

	private void saveLastProcessedUid(long uid) {
		LastProcessedUidEntity entity = lastProcessedUidRepository.findById(properties.folder())
				.orElse(new LastProcessedUidEntity(properties.folder(), uid));
		entity.setLastUid(uid);
		lastProcessedUidRepository.save(entity);
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
