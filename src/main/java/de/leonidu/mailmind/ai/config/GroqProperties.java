package de.leonidu.mailmind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq")
public record GroqProperties(
		String apiKey,
		String baseUrl,
		String model
) {
	public GroqProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://api.groq.com/openai/v1";
		}
		if (model == null || model.isBlank()) {
			model = "openai/gpt-oss-120b";
		}
	}
}