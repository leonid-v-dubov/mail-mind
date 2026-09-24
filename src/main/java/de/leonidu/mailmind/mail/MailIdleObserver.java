package de.leonidu.mailmind.mail;

import de.leonidu.mailmind.mail.config.MailImapProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "mail.imap", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "mail.imap", name = "mode", havingValue = "idle", matchIfMissing = true)
public class MailIdleObserver {

	private static final Logger log = LoggerFactory.getLogger(MailIdleObserver.class);
	private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

	private final MailImapProperties properties;
	private final MailFetchService mailFetchService;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread observerThread;

	public MailIdleObserver(MailImapProperties properties, MailFetchService mailFetchService) {
		this.properties = properties;
		this.mailFetchService = mailFetchService;
	}

	@PostConstruct
	public void start() {
		if (running.compareAndSet(false, true)) {
			observerThread = new Thread(this::observe);
			observerThread.setName("IMAP-IDLE-Observer");
			observerThread.setDaemon(true);
			observerThread.start();
			log.info("IMAP IDLE observer started");
		}
	}

	@PreDestroy
	public void destroy() {
		stop();
	}

	public void stop() {
		if (running.compareAndSet(true, false)) {
			if (observerThread != null) {
				observerThread.interrupt();
			}
			log.info("IMAP IDLE observer stopped");
		}
	}

	private void observe() {
		while (running.get()) {
			try {
				Session session = createSession();
				String protocol = properties.ssl() ? "imaps" : "imap";

				try (Store store = session.getStore(protocol)) {
					log.debug("Connecting to {}:{}/{} as {}", properties.host(), properties.port(), protocol, properties.username());
					store.connect(properties.host(), properties.username(), properties.password());

					int openMode = properties.markAsSeen() ? Folder.READ_WRITE : Folder.READ_ONLY;
					Folder folder = store.getFolder(properties.folder());
					folder.open(openMode);

					try {
						folder.addMessageCountListener(new MessageCountAdapter() {
							@Override
							public void messagesAdded(MessageCountEvent e) {
								log.info("New messages detected via listener");
								handleNewMessages();
							}
						});

						log.info("IMAP listener mode active for folder: {}", properties.folder());
						handleNewMessages(); // Check for messages on startup

						// Keep connection alive with periodic checks
						while (running.get()) {
							try {
								// Wait with shorter interval for near real-time detection
								Thread.sleep(Math.min(properties.pollInterval().toMillis(), 5000));
								
								// Send NOOP to keep connection alive and trigger any pending events
								try {
									folder.getMessageCount();
								}
								catch (MessagingException ex) {
									if (running.get()) {
										log.warn("IMAP connection check failed, reconnecting...", ex);
										break;
									}
								}
							}
							catch (InterruptedException ie) {
								Thread.currentThread().interrupt();
								break;
							}
						}
					}
					finally {
						folder.close(false);
					}
				}
			}
			catch (Exception ex) {
				if (running.get()) {
					log.error("IMAP observer error, reconnecting in 10 seconds...", ex);
					try {
						Thread.sleep(10000);
					}
					catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
	}

	private void handleNewMessages() {
		try {
			var emails = mailFetchService.fetchAndDispatchNewMessages();
			if (!emails.isEmpty()) {
				log.info("Dispatched {} newly parsed email(s) via IDLE", emails.size());
			}
		}
		catch (RuntimeException ex) {
			log.error("Failed to handle new messages via IDLE", ex);
		}
	}

	private Session createSession() {
		Properties sessionProps = new Properties();
		String protocol = properties.ssl() ? "imaps" : "imap";
		sessionProps.put("mail.store.protocol", protocol);
		sessionProps.put("mail." + protocol + ".host", properties.host());
		sessionProps.put("mail." + protocol + ".port", String.valueOf(properties.port()));
		sessionProps.put("mail." + protocol + ".connectiontimeout", "30000");
		sessionProps.put("mail." + protocol + ".timeout", "30000");
		if (properties.ssl()) {
			sessionProps.put("mail.imaps.ssl.enable", "true");
		}
		return Session.getInstance(sessionProps);
	}
}