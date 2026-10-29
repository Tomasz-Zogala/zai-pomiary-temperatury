package pl.pw.zai.pomiary.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import pl.pw.zai.pomiary.domain.Measurement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Zapytanie budowane z gotowych fragmentów; dane trafiają wyłącznie przez parametry
public class MeasurementSearchImpl implements MeasurementSearch {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Measurement> search(List<Long> seriesIds, Instant from, Instant to, boolean descending, int limit) {
        List<String> where = new ArrayList<>();
        if (seriesIds != null) where.add("m.seriesId in :series");
        if (from != null) where.add("m.timestamp >= :from");
        if (to != null) where.add("m.timestamp <= :to");

        String jpql = "select m from Measurement m"
                + (where.isEmpty() ? "" : " where " + String.join(" and ", where))
                + " order by m.timestamp " + (descending ? "desc" : "asc") + ", m.id " + (descending ? "desc" : "asc");

        TypedQuery<Measurement> q = em.createQuery(jpql, Measurement.class);
        if (seriesIds != null) q.setParameter("series", seriesIds);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
        return q.setMaxResults(limit).getResultList();
    }
}
