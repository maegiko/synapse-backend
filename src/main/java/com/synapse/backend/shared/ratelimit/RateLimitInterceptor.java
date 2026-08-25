package com.synapse.backend.shared.ratelimit;

import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Set<String> AI_PATHS = Set.of(
        "/api/notes/summarise",
        "/api/flashcards/generate",
        "/api/quiz/generate"
    );

    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public RateLimitInterceptor(RateLimitService rateLimitService, RateLimitProperties rateLimitProperties) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * Limits authenticated API requests by user id.
     *
     * <p>AI generation routes, matched by method and path within the application,
     * are limited per minute and per day. All other authenticated routes share a
     * per-minute limit. Preflight and unauthenticated requests are not limited here.</p>
     *
     * @param request the incoming request.
     * @param response the outgoing response.
     * @param handler the handler the request is mapped to.
     * @return true so the request continues to the handler.
     * @throws RateLimitExceededException if the user has exceeded a limit.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) return true;

        String userId = authenticatedUserId();

        if (userId == null) return true;

        if (isAiRequest(request)) {
            rateLimitService.check("ai-minute:" + userId, rateLimitProperties.ai());
            rateLimitService.check("ai-day:" + userId, rateLimitProperties.aiDaily());
        } else {
            rateLimitService.check("api:" + userId, rateLimitProperties.api());
        }

        return true;
    }

    private boolean isAiRequest(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) return false;

        return AI_PATHS.contains(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
    }

    private String authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getSubject();
        }

        return null;
    }

}
