package com.synapse.backend.ai.clients.gemini;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.clients.gemini.dto.GeminiContent;
import com.synapse.backend.ai.clients.gemini.dto.GeminiPart;
import com.synapse.backend.ai.clients.gemini.dto.GeminiRequest;
import com.synapse.backend.ai.clients.gemini.dto.GeminiResponse;
import com.synapse.backend.ai.config.GeminiProperties;
import com.synapse.backend.ai.exceptions.LLMProviderException;

@Component
public class GeminiClient implements LLMClient {
    private final RestClient restClient;
    private final GeminiProperties geminiProperties;

    public GeminiClient(RestClient restClient, GeminiProperties geminiProperties) {
        this.restClient = restClient;
        this.geminiProperties = geminiProperties;
    }

    @Override
    public String generate(LLMRequest llmRequest) {
        GeminiRequest body = new GeminiRequest(
            List.of(
                new GeminiContent(
                    List.of(
                        new GeminiPart(llmRequest.userPrompt())
                    )
                )
            )
        );

        try {
            GeminiResponse res = restClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", llmRequest.model())
                .header("x-goog-api-key", geminiProperties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class);

            return res
                .candidates()
                .get(0)
                .content()
                .parts()
                .get(0)
                .text();
        } catch (RestClientException | NullPointerException | IndexOutOfBoundsException e) {
            throw new LLMProviderException("Failed to get a valid response from the LLM provider");
        }
    }

}
