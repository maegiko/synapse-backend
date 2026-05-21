package com.synapse.backend.ai.clients.groq;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.synapse.backend.ai.clients.LLMClient;
import com.synapse.backend.ai.clients.dto.LLMRequest;
import com.synapse.backend.ai.clients.groq.dto.GroqMessage;
import com.synapse.backend.ai.clients.groq.dto.GroqRequest;
import com.synapse.backend.ai.clients.groq.dto.GroqResponse;
import com.synapse.backend.ai.config.GroqProperties;
import com.synapse.backend.ai.exceptions.LLMProviderException;

@Primary
@Component
public class GroqClient implements LLMClient {
    private final RestClient restClient;
    private final GroqProperties properties;

    public GroqClient(RestClient restClient, GroqProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String generate(LLMRequest request) {
        GroqRequest req = new GroqRequest(
            request.model(),
            List.of(
                new GroqMessage("system", request.systemPrompt()),
                new GroqMessage("user", request.userPrompt())
            )
        );

        try {
            GroqResponse res = restClient.post()
                .uri("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(GroqResponse.class);

            return res
                .choices()
                .get(0)
                .message()
                .content();
        } catch (RestClientException | NullPointerException | IndexOutOfBoundsException e) {
            throw new LLMProviderException("Failed to get a valid response from the LLM provider");
        }
    }

}
