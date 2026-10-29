package pl.pw.zai.pomiary.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.pw.zai.pomiary.domain.Sensor;
import pl.pw.zai.pomiary.repo.SensorRepository;
import pl.pw.zai.pomiary.repo.SeriesRepository;
import pl.pw.zai.pomiary.security.Secrets;
import pl.pw.zai.pomiary.web.dto.Dtos.SensorCreatedDto;
import pl.pw.zai.pomiary.web.dto.Dtos.SensorDto;
import pl.pw.zai.pomiary.web.dto.Dtos.SensorInput;
import pl.pw.zai.pomiary.web.error.ApiException;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(value = "/api/sensors", produces = "application/json")
public class SensorController {

    private final SensorRepository sensors;
    private final SeriesRepository series;

    public SensorController(SensorRepository sensors, SeriesRepository series) {
        this.sensors = sensors;
        this.series = series;
    }

    @GetMapping
    public List<SensorDto> list() {
        return sensors.findByRevokedAtIsNullOrderByIdAsc().stream().map(SensorDto::of).toList();
    }

    @GetMapping("/{id}")
    public SensorDto get(@PathVariable Long id) {
        return SensorDto.of(find(id));
    }

    @PostMapping(consumes = "application/json")
    @Transactional
    public ResponseEntity<SensorCreatedDto> register(@Valid @RequestBody SensorInput in) {
        if (!series.existsById(in.seriesId())) {
            throw ApiException.unprocessable("seriesId", "Seria " + in.seriesId() + " nie istnieje");
        }
        String apiKey = Secrets.randomToken();
        Sensor s = sensors.save(new Sensor(in.name().trim(), in.seriesId(), Secrets.sha256(apiKey), Instant.now()));
        var body = new SensorCreatedDto(s.getId(), s.getName(), s.getSeriesId(), s.getCreatedAt(), null, apiKey);
        return ResponseEntity.created(URI.create("/api/sensors/" + s.getId())).body(body);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> unregister(@PathVariable Long id) {
        Sensor s = find(id);
        s.setRevokedAt(Instant.now());
        return ResponseEntity.noContent().build();
    }

    private Sensor find(Long id) {
        return sensors.findByIdAndRevokedAtIsNull(id).orElseThrow(() -> ApiException.notFound("Czujnik " + id));
    }
}
