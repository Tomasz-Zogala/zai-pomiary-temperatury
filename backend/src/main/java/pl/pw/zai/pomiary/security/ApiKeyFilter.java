package pl.pw.zai.pomiary.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.pw.zai.pomiary.repo.SensorRepository;

import java.io.IOException;

public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final SensorRepository sensors;

    public ApiKeyFilter(SensorRepository sensors) {
        this.sensors = sensors;
    }

    static boolean isMeasurementUpload(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equalsIgnoreCase(request.getMethod())
                && (path.equals("/api/measurements") || path.equals("/api/measurements/"));
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !isMeasurementUpload(request);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null && !key.isBlank()) {
            sensors.findByApiKeyHashAndRevokedAtIsNull(Secrets.sha256(key.trim())).ifPresent(s -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        new AuthPrincipals.SensorPrincipal(s.getId(), s.getSeriesId()), null,
                        AuthorityUtils.createAuthorityList("ROLE_SENSOR"));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}
