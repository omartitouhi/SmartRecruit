package com.smartrecruit.user.support;

import com.smartrecruit.user.service.CompanyInvitationNotifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test notifier that keeps the delivered invitations, so tests can read the raw token that the API
 * itself never returns. Import {@link Config} in the test class.
 */
public class CapturingInvitationNotifier implements CompanyInvitationNotifier {

    public record Sent(String email, String companyName, String link) {

        public String token() {
            return link.substring(link.indexOf("token=") + "token=".length());
        }
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendInvitation(String email, String companyName, String acceptLink) {
        sent.add(new Sent(email, companyName, acceptLink));
    }

    public List<Sent> sent() {
        return sent;
    }

    public Sent last() {
        return sent.get(sent.size() - 1);
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public CapturingInvitationNotifier capturingInvitationNotifier() {
            return new CapturingInvitationNotifier();
        }
    }
}
