package pl.pw.zai.pomiary.security;

public final class AuthPrincipals {

    private AuthPrincipals() {}

    public record Admin(Long userId, String username, String tokenHash) {}

    public record SensorPrincipal(Long sensorId, Long seriesId) {}
}
