package pl.pw.zai.pomiary.domain;

import jakarta.persistence.*;

import java.time.Instant;

// W bazie PK to (id, ts) ze względu na hypertable; id jest i tak unikalne (identity)
@Entity
@Table(name = "measurement")
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @Column(name = "sensor_id")
    private Long sensorId;

    @Column(nullable = false)
    private double value;

    @Column(name = "ts", nullable = false)
    private Instant timestamp;

    protected Measurement() {}

    public Measurement(Long seriesId, Long sensorId, double value, Instant timestamp) {
        this.seriesId = seriesId;
        this.sensorId = sensorId;
        this.value = value;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public Long getSeriesId() { return seriesId; }
    public Long getSensorId() { return sensorId; }
    public double getValue() { return value; }
    public Instant getTimestamp() { return timestamp; }
}
