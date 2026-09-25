package de.leonidu.mailmind.ai.config;

import de.leonidu.mailmind.ai.AiProvider;
import de.leonidu.mailmind.ai.GroqResponsesService;
import de.leonidu.mailmind.ai.OpenAiResponsesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, GroqProperties.class})
public class AiConfig {

	private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

	@Value("${ai.provider:openai}")
	private String aiProvider;

	@Bean
	@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
	public AiProvider openAiProvider(OpenAiProperties openAiProperties, RestClient.Builder restClientBuilder) {
		log.info("Creating OpenAI provider");
		return new OpenAiResponsesService(openAiProperties, restClientBuilder);
	}

	@Bean
	@ConditionalOnProperty(name = "ai.provider", havingValue = "groq")
	public AiProvider groqProvider(GroqProperties groqProperties, RestClient.Builder restClientBuilder) {
		log.info("Creating Groq provider");
		return new GroqResponsesService(groqProperties, restClientBuilder);
	}
}