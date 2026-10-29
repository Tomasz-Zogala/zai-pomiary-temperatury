package pl.pw.zai.pomiary.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "sensor")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64, columnDefinition = "bpchar(64)")
    private String apiKeyHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_measurement_at")
    private Instant lastMeasurementAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Sensor() {}

    public Sensor(String name, Long seriesId, String apiKeyHash, Instant createdAt) {
        this.name = name;
        this.seriesId = seriesId;
        this.apiKeyHash = apiKeyHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getSeriesId() { return seriesId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastMeasurementAt() { return lastMeasurementAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
