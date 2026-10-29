package pl.pw.zai.pomiary.web;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.pw.zai.pomiary.config.AppProperties;
import pl.pw.zai.pomiary.domain.Measurement;
import pl.pw.zai.pomiary.domain.Series;
import pl.pw.zai.pomiary.repo.MeasurementRepository;
import pl.pw.zai.pomiary.repo.SensorRepository;
import pl.pw.zai.pomiary.repo.SeriesRepository;
import pl.pw.zai.pomiary.security.AuthPrincipals.SensorPrincipal;
import pl.pw.zai.pomiary.web.dto.Dtos.MeasurementDto;
import pl.pw.zai.pomiary.web.dto.Dtos.MeasurementInput;
import pl.pw.zai.pomiary.web.error.ApiException;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/api/measurements", produces = "application/json")
public class MeasurementController {

    private static final Logger log = LoggerFactory.getLogger(MeasurementController.class);
    private static final int MAX_LIMIT = 10_000;

    private final MeasurementRepository measurements;
    private final SeriesRepository series;
    private final SensorRepository sensors;
    private final AppProperties props;

    public MeasurementController(MeasurementRepository measurements, SeriesRepository series,
                                 SensorRepository sensors, AppProperties props) {
        this.measurements = measurements;
        this.series = series;
        this.sensors = sensors;
        this.props = props;
    }

    @GetMapping
    public List<MeasurementDto> search(@RequestParam(required = false) String series,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(required = false) String limit) {
        List<Long> ids = parseSeries(series);
        Instant f = parseTime("from", from);
        Instant t = parseTime("to", to);
        if (f != null && t != null && f.isAfter(t)) {
            throw ApiException.badRequest("from", "Parametr from nie może być późniejszy niż to");
        }
        boolean desc;
        if (sort == null || sort.isBlank() || sort.equals("timestamp")) desc = false;
        else if (sort.equals("-timestamp")) desc = true;
        else throw ApiException.badRequest("sort", "Dozwolone wartości sort: timestamp, -timestamp");
        int lim = parseLimit(limit);

        if (ids != null && ids.isEmpty()) return List.of();
        return measurements.search(ids, f, t, desc, lim).stream().map(MeasurementDto::of).toList();
    }

    @GetMapping("/{id}")
    public MeasurementDto get(@PathVariable Long id) {
        return measurements.findById(id).map(MeasurementDto::of)
                .orElseThrow(() -> ApiException.notFound("Wynik " + id));
    }

    @PostMapping(consumes = "application/json")
    @Transactional
    public ResponseEntity<MeasurementDto> upload(@AuthenticationPrincipal SensorPrincipal sensor,
                                                 @Valid @RequestBody MeasurementInput in) {
        Series s = series.findById(sensor.seriesId())
                .orElseThrow(() -> ApiException.notFound("Seria " + sensor.seriesId()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant ts = in.timestamp() != null ? in.timestamp().toInstant() : now;
        double v = in.value();

        if (!Double.isFinite(v) || v < s.getMinValue() || v > s.getMaxValue()) {
            log.warn("Odrzucono wynik czujnika {} (seria {}): wartość {} poza przedziałem [{}, {}]",
                    sensor.sensorId(), s.getId(), v, s.getMinValue(), s.getMaxValue());
            throw ApiException.unprocessable("value", String.format(java.util.Locale.ROOT,
                    "Wartość %s jest poza przedziałem serii [%s, %s]", v, s.getMinValue(), s.getMaxValue()));
        }
        if (ts.isAfter(now.plus(props.maxFutureSkew()))) {
            log.warn("Odrzucono wynik czujnika {} (seria {}): znacznik czasu {} z przyszłości",
                    sensor.sensorId(), s.getId(), ts);
            throw ApiException.unprocessable("timestamp", "Znacznik czasu nie może być późniejszy niż 5 minut od czasu serwera");
        }

        Measurement m = measurements.save(new Measurement(s.getId(), sensor.sensorId(), v, ts));
        sensors.touch(sensor.sensorId(), ts);
        return ResponseEntity.created(URI.create("/api/measurements/" + m.getId())).body(MeasurementDto.of(m));
    }

    private static List<Long> parseSeries(String raw) {
        if (raw == null) return null;
        List<Long> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(p));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("series", "Niepoprawny identyfikator serii: " + p);
            }
        }
        return ids;
    }

    private static Instant parseTime(String name, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().replace(' ', '+'); // „+” w strefie bywa zamieniany na spację
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(name, "Parametr " + name + " musi być datą ISO 8601 ze strefą czasową");
        }
    }

    private static int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) return 1000;
        try {
            int l = Integer.parseInt(raw.trim());
            if (l >= 1 && l <= MAX_LIMIT) return l;
        } catch (NumberFormatException ignored) {
        }
        throw ApiException.badRequest("limit", "Parametr limit musi być liczbą od 1 do " + MAX_LIMIT);
    }
}
