package de.leonidu.mailmind.ai;

import de.leonidu.mailmind.ai.config.GroqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GroqResponsesService implements AiProvider {
	@Value("classpath:prompts/medical-correction.txt")
	private Resource medicalCorrectionPrompt;

	private static final Logger log = LoggerFactory.getLogger(GroqResponsesService.class);

	private final GroqProperties properties;
	private final RestClient restClient;

	public GroqResponsesService(GroqProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.restClient = restClientBuilder
				.baseUrl(trimTrailingSlash(properties.baseUrl()))
				.build();
	}

	@Override
	public String getProviderName() {
		return "groq";
	}

	@Override
	public AiResponse complete(String input, String previousResponseId) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new IllegalStateException("groq.api-key is not configured");
		}

		String systemPrompt = null;
		try {
			systemPrompt = medicalCorrectionPrompt
					.getContentAsString(StandardCharsets.UTF_8);
		} catch (Exception ex) {
			log.warn("Failed to read medical-correction.txt prompt", ex);
		}

		List<Map<String, String>> messages = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			messages.add(Map.of("role", "system", "content", systemPrompt));
		}
		messages.add(Map.of("role", "user", "content", input));

		Map<String, Object> body = Map.of(
			"model", properties.model(),
			"messages", messages
		);

		log.debug(
				"Calling Groq API model={} previousResponseId={}",
				properties.model(),
				previousResponseId
		);

		JsonNode response = restClient.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + properties.apiKey())
				.body(body)
				.retrieve()
				.body(JsonNode.class);

		String id = extractResponseId(response);
		String text = extractOutputText(response);
		if (text == null || text.isBlank()) {
			throw new IllegalStateException("Groq API returned no content: " + response);
		}
		if (id == null || id.isBlank()) {
			id = generateFallbackId();
		}
		return new AiResponse(id, text.trim());
	}

	private String extractResponseId(JsonNode response) {
		if (response == null || !response.hasNonNull("id")) {
			return null;
		}
		return response.get("id").asText();
	}

	private String extractOutputText(JsonNode response) {
		if (response == null) {
			return null;
		}
		JsonNode choices = response.get("choices");
		if (choices == null || !choices.isArray() || choices.isEmpty()) {
			return null;
		}
		JsonNode firstChoice = choices.get(0);
		JsonNode message = firstChoice.get("message");
		if (message == null || !message.hasNonNull("content")) {
			return null;
		}
		return message.get("content").asText();
	}

	private String generateFallbackId() {
		return "groq-" + System.currentTimeMillis();
	}

	private static String trimTrailingSlash(String url) {
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}
}