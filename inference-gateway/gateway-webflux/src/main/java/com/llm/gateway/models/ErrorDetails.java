package com.llm.gateway.models;

import org.springframework.http.HttpStatusCode;

public record ErrorDetails(String reason, HttpStatusCode status, long timestamp, String error, String path) {
}
