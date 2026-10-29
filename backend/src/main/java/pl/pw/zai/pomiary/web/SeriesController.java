package pl.pw.zai.pomiary.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.pw.zai.pomiary.domain.Series;
import pl.pw.zai.pomiary.repo.MeasurementRepository;
import pl.pw.zai.pomiary.repo.SeriesRepository;
import pl.pw.zai.pomiary.web.dto.Dtos.SeriesDto;
import pl.pw.zai.pomiary.web.dto.Dtos.SeriesInput;
import pl.pw.zai.pomiary.web.error.ApiException;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/api/series", produces = "application/json")
public class SeriesController {

    private final SeriesRepository series;
    private final MeasurementRepository measurements;

    public SeriesController(SeriesRepository series, MeasurementRepository measurements) {
        this.series = series;
        this.measurements = measurements;
    }

    @GetMapping
    public List<SeriesDto> list() {
        return series.findAll(Sort.by("id")).stream().map(SeriesDto::of).toList();
    }

    @GetMapping("/{id}")
    public SeriesDto get(@PathVariable Long id) {
        return SeriesDto.of(find(id));
    }

    @PostMapping(consumes = "application/json")
    @Transactional
    public ResponseEntity<SeriesDto> create(@Valid @RequestBody SeriesInput in) {
        validateRange(in);
        Series s = new Series();
        apply(s, in);
        s = series.save(s);
        return ResponseEntity.created(URI.create("/api/series/" + s.getId())).body(SeriesDto.of(s));
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @Transactional
    public SeriesDto update(@PathVariable Long id, @Valid @RequestBody SeriesInput in) {
        Series s = find(id);
        validateRange(in);
        if (measurements.existsOutside(id, in.minValue(), in.maxValue())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Nowy przedział wyklucza wyniki już zapisane w serii");
        }
        apply(s, in);
        return SeriesDto.of(series.save(s));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        series.delete(find(id)); // wyniki i czujniki usuwa ON DELETE CASCADE
        return ResponseEntity.noContent().build();
    }

    private Series find(Long id) {
        return series.findById(id).orElseThrow(() -> ApiException.notFound("Seria " + id));
    }

    private static void validateRange(SeriesInput in) {
        if (!Double.isFinite(in.minValue()) || !Double.isFinite(in.maxValue())) {
            throw ApiException.unprocessable("minValue", "Granice przedziału muszą być liczbami skończonymi");
        }
        if (in.minValue() >= in.maxValue()) {
            throw ApiException.unprocessable("minValue", "minValue musi być mniejsze niż maxValue");
        }
    }

    private static void apply(Series s, SeriesInput in) {
        s.setName(in.name().trim());
        s.setMinValue(in.minValue());
        s.setMaxValue(in.maxValue());
        s.setColor(in.color().toLowerCase());
        s.setIcon(blankToNull(in.icon()));
        s.setUnit(blankToNull(in.unit()));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
