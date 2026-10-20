package com.smartrecruit.dashboard.client;

import com.smartrecruit.dashboard.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Supplier;

/** How a failed call to another service surfaces in dashboard-service. */
@Slf4j
public final class Upstream {

    private Upstream() {
    }

    /**
     * Relays 400/404/409 as is (with the upstream message when there is one); anything else, including an
     * unreachable service, becomes 502.
     */
    public static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 400 || status == 404 || status == 409) {
                throw new ApiException(HttpStatus.valueOf(status), messageOf(e));
            }
            log.warn("Service interne en erreur : HTTP {}", status);
            throw unavailable();
        } catch (RestClientException e) {
            log.warn("Service interne injoignable : {}", e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    /** Best effort: any failure yields {@code null} so one missing source does not break a whole response. */
    public static <T> T orNull(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.warn("Statistique indisponible : {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Service interne indisponible, réessayez plus tard");
    }

    private static String messageOf(RestClientResponseException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("message") instanceof String message) {
                return message;
            }
        } catch (RuntimeException ignored) {
            // not a JSON error body
        }
        return e.getStatusCode().value() == 404 ? "Ressource introuvable" : "Requête refusée";
    }
}
