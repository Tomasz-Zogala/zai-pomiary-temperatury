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

import java.io.IOException;

// Token administratora; pomijany przy POST /api/measurements, gdzie liczy się tylko klucz czujnika
public class BearerTokenFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    public BearerTokenFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return ApiKeyFilter.isMeasurementUpload(request);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                tokenService.resolve(token).ifPresent(admin -> {
                    var auth = new UsernamePasswordAuthenticationToken(admin, null,
                            AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }
        chain.doFilter(request, response);
    }
}
