package com.synapse.backend.email;

import com.synapse.backend.email.dto.EmailMessage;

/** Provider interface for transactional email, mirroring the LLM client boundary. */
public interface EmailClient {

    void send(EmailMessage message);

}
