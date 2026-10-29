package pl.pw.zai.pomiary.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pl.pw.zai.pomiary.config.AppProperties;
import pl.pw.zai.pomiary.repo.SensorRepository;

import java.util.List;

@Configuration
public class SecurityConfig {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenService tokens, SensorRepository sensors,
                                           ProblemSecurityHandlers handlers) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // brak ciasteczek sesyjnych - token w nagłówku
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(c -> {})
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(f -> f.deny()))
            .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
            .addFilterBefore(new ApiKeyFilter(sensors), AnonymousAuthenticationFilter.class)
            .addFilterBefore(new BearerTokenFilter(tokens), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/series", "/api/series/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/measurements", "/api/measurements/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/measurements").hasRole("SENSOR")
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().permitAll()); // pliki frontendu (E2)
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!props.corsOrigins().isEmpty()) {
            CorsConfiguration c = new CorsConfiguration();
            c.setAllowedOrigins(props.corsOrigins());
            c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "Accept"));
            c.setExposedHeaders(List.of("Location"));
            c.setAllowCredentials(false);
            c.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", c);
        }
        return source;
    }
}
