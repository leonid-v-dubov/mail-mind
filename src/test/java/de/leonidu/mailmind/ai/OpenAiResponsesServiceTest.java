package de.leonidu.mailmind.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenAiResponsesServiceTest {

	private final JsonMapper mapper = JsonMapper.shared();

	@Test
	void extractsOutputTextFromMessageContent() {
		ObjectNode root = mapper.createObjectNode();
		ArrayNode output = root.putArray("output");
		ObjectNode reasoning = output.addObject();
		reasoning.put("type", "reasoning");
		ObjectNode message = output.addObject();
		message.put("type", "message");
		ArrayNode content = message.putArray("content");
		ObjectNode part = content.addObject();
		part.put("type", "output_text");
		part.put("text", "Hello reply");

		assertEquals("Hello reply", OpenAiResponsesService.extractOutputText(root));
	}

	@Test
	void prefersTopLevelOutputText() {
		ObjectNode root = mapper.createObjectNode();
		root.put("output_text", "Top level");
		assertEquals("Top level", OpenAiResponsesService.extractOutputText(root));
	}

	@Test
	void returnsNullWhenEmpty() {
		assertNull(OpenAiResponsesService.extractOutputText(mapper.createObjectNode()));
	}

	@Test
	void extractsResponseId() {
		ObjectNode root = mapper.createObjectNode();
		root.put("id", "resp_123");
		assertEquals("resp_123", OpenAiResponsesService.extractResponseId(root));
	}
}
