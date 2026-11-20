package com.jpm.hrms.geo;

import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.communication.CommunicationSettings;
import com.jpm.hrms.communication.CommunicationSettingsRepository;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How far each candidate lives from the company: reads the home pin from the candidate's Google Maps link and the
 * company pin from the location link in Settings → Communication, and keeps the straight-line distance up to date.
 */
@Service
public class HomeDistanceService {
    private final MapLinkResolver resolver;
    private final CommunicationSettingsRepository settingsRepo;
    private final MongoOperations mongo;

    public HomeDistanceService(MapLinkResolver resolver, CommunicationSettingsRepository settingsRepo, MongoOperations mongo) {
        this.resolver = resolver;
        this.settingsRepo = settingsRepo;
        this.mongo = mongo;
    }

    public MapLinkResolver.Result resolve(String link) {
        return resolver.resolve(link);
    }

    /** Stores the home link and its point (null when unreadable) and works out the distance. */
    public void setHome(Candidate c, String link, GeoPoint home) {
        c.setHomeMapLink(StringUtils.hasText(link) ? link.trim() : null);
        c.setHomeLat(home == null ? null : home.lat());
        c.setHomeLng(home == null ? null : home.lng());
        GeoPoint company = home == null ? null : companyPoint().orElse(null);
        c.setHomeDistanceKm(company == null ? null : home.kmTo(company));
    }

    public static Optional<GeoPoint> homeOf(Candidate c) {
        return c.getHomeLat() == null || c.getHomeLng() == null ? Optional.empty() : Optional.of(new GeoPoint(c.getHomeLat(), c.getHomeLng()));
    }

    /** Google Maps directions from the candidate's home to the company (shows road distance and travel time). */
    public Optional<String> directionsUrl(Candidate c) {
        Optional<GeoPoint> home = homeOf(c);
        Optional<GeoPoint> company = companyPoint();
        if (home.isEmpty() || company.isEmpty()) return Optional.empty();
        return Optional.of("https://www.google.com/maps/dir/?api=1&origin=" + home.get().text() + "&destination=" + company.get().text());
    }

    /** The company pin; read from the location link the first time it is needed after the link changes. */
    public Optional<GeoPoint> companyPoint() {
        CommunicationSettings s = settingsRepo.findById(CommunicationSettings.SINGLETON_ID).orElse(null);
        if (s == null) return Optional.empty();
        if (!Objects.equals(s.getCompanyPinSource(), s.pinSource())) refreshCompanyPin(s);
        return s.getCompanyLat() == null ? Optional.empty() : Optional.of(new GeoPoint(s.getCompanyLat(), s.getCompanyLng()));
    }

    /**
     * Reads the company pin from the exact pin link, or else the location link (the caller's object is updated and saved), and
     * recalculates every candidate's distance. Returns the problem when the link cannot be read.
     */
    public String refreshCompanyPin(CommunicationSettings s) {
        String problem = null;
        GeoPoint pin = null;
        if (StringUtils.hasText(s.pinSource())) {
            MapLinkResolver.Result r = resolver.resolve(s.pinSource());
            pin = r.point();
            problem = r.problem();
        }
        s.setCompanyPin(pin == null ? null : pin.lat(), pin == null ? null : pin.lng(), s.pinSource());
        settingsRepo.save(s);
        recalculateAll(pin);
        return problem;
    }

    private void recalculateAll(GeoPoint company) {
        List<Candidate> withHome = mongo.find(Query.query(Criteria.where("homeLat").ne(null)), Candidate.class);
        if (withHome.isEmpty()) return;
        BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Candidate.class);
        for (Candidate c : withHome) {
            Double km = company == null ? null : new GeoPoint(c.getHomeLat(), c.getHomeLng()).kmTo(company);
            bulk.updateOne(Query.query(Criteria.where("_id").is(c.getId())), new Update().set("homeDistanceKm", km));
        }
        bulk.execute();
    }
}
