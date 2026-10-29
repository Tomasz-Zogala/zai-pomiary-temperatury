package pl.pw.zai.pomiary.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pw.zai.pomiary.domain.Measurement;

public interface MeasurementRepository extends JpaRepository<Measurement, Long>, MeasurementSearch {

    @Query("select case when count(m) > 0 then true else false end from Measurement m where m.seriesId = :seriesId and (m.value < :min or m.value > :max)")
    boolean existsOutside(Long seriesId, double min, double max);
}
