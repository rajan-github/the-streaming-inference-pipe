package com.llm.gateway.models;

public record LlmPromptRequest(String prompt, int maxTokens) {
}
