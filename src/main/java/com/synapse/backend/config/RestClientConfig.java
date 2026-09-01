package com.synapse.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.synapse.backend.email.ResendProperties;

@Configuration
public class RestClientConfig {

    @Primary
    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

    /**
     * Builds the client the email provider is called with.
     *
     * <p>It has explicit connection and response timeouts, so a slow provider
     * cannot hold a registration request open indefinitely.</p>
     *
     * @param properties the Resend settings, including both timeouts.
     * @return a RestClient scoped to email sending.
     */
    @Bean
    public RestClient resendRestClient(ResendProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder().requestFactory(requestFactory).build();
    }

}
