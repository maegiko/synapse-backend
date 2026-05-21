package com.synapse.backend.ai.clients;

import com.synapse.backend.ai.clients.dto.LLMRequest;

public interface LLMClient {

    /**
     * Makes an API call to an LLM and returns the output.
     *
     * @param llmRequest the model, system prompt and user prompt for the request.
     * @return the string output of the LLM.
     */
    public String generate(LLMRequest llmRequest);

}
