package com.synapse.backend.email;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.email.dto.ResendEmailRequest;
import com.synapse.backend.email.dto.ResendEmailResponse;
import com.synapse.backend.email.exceptions.EmailProviderException;

/**
 * Sends transactional email through the Resend HTTP API.
 *
 * <p>Provider and transport failures are reported as one generic bad gateway
 * error, so neither the API key nor provider internals reach the client.</p>
 */
@Component
public class ResendClient implements EmailClient {

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendClient(@Qualifier("resendRestClient") RestClient restClient, ResendProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        ResendEmailRequest req = new ResendEmailRequest(
            properties.from(),
            List.of(message.to()),
            message.subject(),
            message.text(),
            message.html()
        );

        try {
            ResendEmailResponse res = restClient.post()
                .uri(properties.apiUrl())
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Idempotency-Key", message.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ResendEmailResponse.class);

            if (res == null || res.id() == null)
                throw new EmailProviderException("Failed to send the email through the email provider");
        } catch (RestClientException e) {
            throw new EmailProviderException("Failed to send the email through the email provider");
        }
    }

}
