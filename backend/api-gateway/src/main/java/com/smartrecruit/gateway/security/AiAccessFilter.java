package com.smartrecruit.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deny-by-default authorization for {@code /api/v1/ai/**}: the ai-service does no auth itself and
 * backend services reach it directly on the internal network, so only a few read endpoints are exposed.
 * RECRUITER and ADMIN may read any candidate's data; a CANDIDATE only their own.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AiAccessFilter extends OncePerRequestFilter {

    private static final String UUID_RE = "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";
    /** GET endpoints where group 1 is the candidate id. */
    private static final Pattern[] CANDIDATE_READS = {
            Pattern.compile("/api/v1/ai/skill-gap/candidates/" + UUID_RE + "/jobs/" + UUID_RE),
            Pattern.compile("/api/v1/ai/recommendation/candidates/" + UUID_RE + "/jobs"),
            Pattern.compile("/api/v1/ai/cv-parsing/candidates/" + UUID_RE)
    };
    private static final String MATCHING_SCORE = "/api/v1/ai/matching/score";
    private static final int MAX_BODY_BYTES = 4096;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/ai");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String role = request.getHeader(InternalIdentityHeaders.USER_ROLE);
        String userId = request.getHeader(InternalIdentityHeaders.USER_ID);
        boolean privileged = "RECRUITER".equals(role) || "ADMIN".equals(role);
        if (!privileged && !"CANDIDATE".equals(role)) {
            deny(request, response, "Accès refusé : endpoint réservé aux services internes");
            return;
        }

        if ("GET".equals(request.getMethod())) {
            for (Pattern pattern : CANDIDATE_READS) {
                Matcher matcher = pattern.matcher(uri);
                if (matcher.matches()) {
                    if (privileged || matcher.group(1).equalsIgnoreCase(userId)) {
                        chain.doFilter(request, response);
                    } else {
                        deny(request, response, "Accès refusé : ces données appartiennent à un autre candidat");
                    }
                    return;
                }
            }
        } else if ("POST".equals(request.getMethod()) && MATCHING_SCORE.equals(uri)) {
            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                GatewayErrorWriter.write(response, request, HttpStatus.BAD_REQUEST, "Corps de requête trop volumineux");
                return;
            }
            if (!privileged) {
                String cvId = cvId(body);
                if (cvId == null) {
                    GatewayErrorWriter.write(response, request, HttpStatus.BAD_REQUEST,
                            "Corps de requête invalide : cvId manquant");
                    return;
                }
                if (!cvId.equalsIgnoreCase(userId)) {
                    deny(request, response, "Accès refusé : ces données appartiennent à un autre candidat");
                    return;
                }
            }
            chain.doFilter(new BufferedBodyRequest(request, body), response);
            return;
        }
        deny(request, response, "Accès refusé : endpoint réservé aux services internes");
    }

    /** Returns the cvId as a canonical UUID string, or null if the body has no valid one. */
    private String cvId(byte[] body) {
        try {
            Object value = mapper.readValue(body, Map.class).get("cvId");
            return value instanceof String s ? UUID.fromString(s).toString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void deny(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        GatewayErrorWriter.write(response, request, HttpStatus.FORBIDDEN, message);
    }

    /** Re-serves a body already consumed by the filter so the proxy forwards it unchanged. */
    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
