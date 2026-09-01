package com.synapse.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.backend.email.dto.EmailMessage;
import com.synapse.backend.email.exceptions.EmailProviderException;

class ResendClientTest {

    private static final String API_URL = "https://api.resend.com/emails";
    private static final String API_KEY = "test-resend-api-key";
    private static final String FROM = "Synapse <no-reply@studysynapse.app>";

    private static final ResendProperties PROPERTIES = new ResendProperties(
        API_KEY,
        FROM,
        API_URL,
        Duration.ofSeconds(5),
        Duration.ofSeconds(10)
    );

    private static final EmailMessage MESSAGE = new EmailMessage(
        "kenneth@example.com",
        "Verify your Synapse email address",
        "Confirm this address: https://studysynapse.app/verify-email?token=raw",
        "<p>Confirm this address</p>",
        "email-verification-42"
    );

    @Test
    void sendPostsTheMessageToResendWithTheApiKeyAndIdempotencyKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(API_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer " + API_KEY))
            .andExpect(header("Idempotency-Key", "email-verification-42"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.from").value(FROM))
            .andExpect(jsonPath("$.to[0]").value("kenneth@example.com"))
            .andExpect(jsonPath("$.subject").value("Verify your Synapse email address"))
            .andExpect(jsonPath("$.text").value(MESSAGE.text()))
            .andExpect(jsonPath("$.html").value(MESSAGE.html()))
            .andRespond(withSuccess("{\"id\": \"3f1c\"}", MediaType.APPLICATION_JSON));

        new ResendClient(builder.build(), PROPERTIES).send(MESSAGE);

        server.verify();
    }

    @Test
    void aFailedProviderCallBecomesABadGatewayErrorWithoutLeakingTheApiKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(API_URL))
            .andRespond(withServerError().body("{\"message\": \"internal provider detail\"}"));

        ResendClient client = new ResendClient(builder.build(), PROPERTIES);

        assertThatThrownBy(() -> client.send(MESSAGE))
            .isInstanceOf(EmailProviderException.class)
            .hasMessage("Failed to send the email through the email provider")
            .satisfies(ex -> {
                assertThat(ex.getMessage()).doesNotContain(API_KEY);
                assertThat(ex.getMessage()).doesNotContain("internal provider detail");
            });
    }

    @Test
    void aResponseWithoutAMessageIdBecomesABadGatewayError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(API_URL))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ResendClient client = new ResendClient(builder.build(), PROPERTIES);

        assertThatThrownBy(() -> client.send(MESSAGE))
            .isInstanceOf(EmailProviderException.class)
            .hasMessage("Failed to send the email through the email provider");
    }

}
