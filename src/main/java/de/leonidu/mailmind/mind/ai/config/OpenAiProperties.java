package de.leonidu.mailmind.mind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
		String apiKey,
		String baseUrl,
		String model,
		boolean store
) {
	public OpenAiProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://api.openai.com/v1";
		}
		if (model == null || model.isBlank()) {
			model = "gpt-6-luna";
		}
	}
}
