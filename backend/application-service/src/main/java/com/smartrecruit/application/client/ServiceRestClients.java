package com.smartrecruit.application.client;

import com.smartrecruit.application.security.InternalIdentityVerifier;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Builds clients for backend-to-backend calls: HTTP/1.1 only (uvicorn does not speak h2c), short
 * timeouts and a signed {@code SERVICE} identity, the same mechanism the api-gateway uses (BE-SEC-04).
 */
final class ServiceRestClients {

    static final UUID SERVICE_ID = new UUID(0, 0);
    static final String SERVICE_ROLE = "SERVICE";

    private ServiceRestClients() {
    }

    static RestClient create(String baseUri, InternalIdentityVerifier signer, Clock clock, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUri)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    long now = clock.instant().getEpochSecond();
                    var headers = request.getHeaders();
                    headers.set(InternalIdentityVerifier.USER_ID, SERVICE_ID.toString());
                    headers.set(InternalIdentityVerifier.USER_ROLE, SERVICE_ROLE);
                    headers.set(InternalIdentityVerifier.TIMESTAMP, Long.toString(now));
                    headers.set(InternalIdentityVerifier.SIGNATURE, signer.sign(SERVICE_ID.toString(), SERVICE_ROLE, now));
                    return execution.execute(request, body);
                })
                .build();
    }
}
