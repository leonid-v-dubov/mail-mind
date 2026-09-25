package de.leonidu.mailmind.ai;

public interface AiProvider {
	
	/**
	 * Generate AI response for the given input
	 * @param input the user input text
	 * @param previousResponseId the previous response ID for conversation continuity (may be null)
	 * @return the AI response containing ID and text
	 */
	AiResponse complete(String input, String previousResponseId);
	
	/**
	 * Get the provider name
	 */
	String getProviderName();
}