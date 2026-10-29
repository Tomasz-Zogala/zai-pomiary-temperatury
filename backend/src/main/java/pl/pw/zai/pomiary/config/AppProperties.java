package pl.pw.zai.pomiary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Admin admin, Duration tokenTtl, List<String> corsOrigins, Duration maxFutureSkew) {

    public record Admin(String username, String password) {}

    public AppProperties {
        if (tokenTtl == null) tokenTtl = Duration.ofHours(2);
        if (maxFutureSkew == null) maxFutureSkew = Duration.ofMinutes(5);
        corsOrigins = corsOrigins == null ? List.of()
                : corsOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
