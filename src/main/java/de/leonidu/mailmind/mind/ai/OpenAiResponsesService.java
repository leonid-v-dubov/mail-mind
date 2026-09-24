package de.leonidu.mailmind.mind.ai;

import de.leonidu.mailmind.mind.ai.config.OpenAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OpenAiResponsesService {

	static final String EMAIL_ASSISTANT_INSTRUCTIONS = """
			You are an email assistant. Write a clear, concise reply to the user's email.
			Reply with the email body only — no subject line, no markdown fences.
			Keep continuity with the ongoing conversation with this sender when prior context exists.
			""".strip();

	private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesService.class);

	private final OpenAiProperties properties;
	private final RestClient restClient;

	public OpenAiResponsesService(OpenAiProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.restClient = restClientBuilder
				.baseUrl(trimTrailingSlash(properties.baseUrl()))
				.build();
	}

	/**
	 * @param previousResponseId last response id for this sender chat, or {@code null} to start a new one
	 */
	public OpenAiResponse complete(String input, String previousResponseId) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new IllegalStateException("openai.api-key is not configured");
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.model());
		body.put("instructions", EMAIL_ASSISTANT_INSTRUCTIONS);
		body.put("input", input);
		body.put("store", properties.store());
		if (previousResponseId != null && !previousResponseId.isBlank()) {
			body.put("previous_response_id", previousResponseId);
		}

		log.debug(
				"Calling OpenAI Responses API model={} previousResponseId={}",
				properties.model(),
				previousResponseId
		);

		JsonNode response = restClient.post()
				.uri("/responses")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + properties.apiKey())
				.body(body)
				.retrieve()
				.body(JsonNode.class);

		String id = extractResponseId(response);
		String text = extractOutputText(response);
		if (text == null || text.isBlank()) {
			throw new IllegalStateException("OpenAI Responses API returned no output_text: " + response);
		}
		if (id == null || id.isBlank()) {
			throw new IllegalStateException("OpenAI Responses API returned no response id: " + response);
		}
		return new OpenAiResponse(id, text.trim());
	}

	static String extractResponseId(JsonNode response) {
		if (response == null || !response.hasNonNull("id")) {
			return null;
		}
		return response.get("id").asText();
	}

	static String extractOutputText(JsonNode response) {
		if (response == null) {
			return null;
		}
		if (response.hasNonNull("output_text")) {
			return response.get("output_text").asText();
		}
		JsonNode output = response.get("output");
		if (output == null || !output.isArray()) {
			return null;
		}
		StringBuilder texts = new StringBuilder();
		for (JsonNode item : output) {
			if (!"message".equals(textOrNull(item.get("type")))) {
				continue;
			}
			JsonNode content = item.get("content");
			if (content == null || !content.isArray()) {
				continue;
			}
			for (JsonNode part : content) {
				if ("output_text".equals(textOrNull(part.get("type"))) && part.hasNonNull("text")) {
					if (!texts.isEmpty()) {
						texts.append('\n');
					}
					texts.append(part.get("text").asText());
				}
			}
		}
		return texts.isEmpty() ? null : texts.toString();
	}

	private static String textOrNull(JsonNode node) {
		return node == null || node.isNull() ? null : node.asText();
	}

	private static String trimTrailingSlash(String url) {
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}
}
