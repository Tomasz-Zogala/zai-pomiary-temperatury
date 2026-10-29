package pl.pw.zai.pomiary.repo;

import pl.pw.zai.pomiary.domain.Measurement;

import java.time.Instant;
import java.util.List;

public interface MeasurementSearch {
    List<Measurement> search(List<Long> seriesIds, Instant from, Instant to, boolean descending, int limit);
}
