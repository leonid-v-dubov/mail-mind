package de.leonidu.mailmind.mail;

import de.leonidu.mailmind.mail.config.GmailProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Service
public class GmailAuthService {

	private static final Logger log = LoggerFactory.getLogger(GmailAuthService.class);
	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

	private final GmailProperties properties;
	private final RestTemplate restTemplate;
	
	private String cachedAccessToken;
	private Instant tokenExpiry;

	public GmailAuthService(GmailProperties properties, RestTemplate restTemplate) {
		this.properties = properties;
		this.restTemplate = restTemplate;
	}

	public String getAccessToken() {
		if (cachedAccessToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
			return cachedAccessToken;
		}

		try {
			TokenRequest request = new TokenRequest(
				properties.clientId(),
				properties.clientSecret(),
				properties.refreshToken(),
				"refresh_token"
			);

			TokenResponse response = restTemplate.postForObject(TOKEN_URL, request, TokenResponse.class);
			
			if (response == null || response.accessToken() == null) {
				throw new IllegalStateException("Failed to obtain access token: empty response");
			}

			cachedAccessToken = response.accessToken();
			// Set expiry to 5 minutes before actual expiry to be safe
			tokenExpiry = Instant.now().plusSeconds(response.expiresIn() - 300);
			
			log.debug("Successfully obtained new access token, expires at {}", tokenExpiry);
			return cachedAccessToken;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to obtain access token from Gmail OAuth", ex);
		}
	}

	private record TokenRequest(
		@JsonProperty("client_id") String clientId,
		@JsonProperty("client_secret") String clientSecret,
		@JsonProperty("refresh_token") String refreshToken,
		@JsonProperty("grant_type") String grantType
	) {}

	private record TokenResponse(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("expires_in") long expiresIn,
		@JsonProperty("token_type") String tokenType
	) {}
}