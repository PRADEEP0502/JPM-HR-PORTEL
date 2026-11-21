package com.jpm.hrms.geo;

import com.jpm.hrms.candidate.Candidate;
import com.jpm.hrms.communication.CommunicationSettings;
import com.jpm.hrms.communication.CommunicationSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * How far each candidate lives from the company: reads the home pin from the candidate's Google Maps link and the
 * company pin from Settings → Communication, and keeps the straight-line distance and the road distance up to date.
 */
@Service
public class HomeDistanceService {
    private static final Logger log = LoggerFactory.getLogger(HomeDistanceService.class);
    /** A road lookup that failed is tried again after this long. */
    private static final Duration RETRY_AFTER = Duration.ofHours(6);
    private static final int ROADS_PER_RUN = 40;

    private final MapLinkResolver resolver;
    private final RoadRouter router;
    private final CommunicationSettingsRepository settingsRepo;
    private final MongoOperations mongo;
    private final AtomicBoolean filling = new AtomicBoolean(false);
    private final AtomicBoolean again = new AtomicBoolean(false);

    public HomeDistanceService(MapLinkResolver resolver, RoadRouter router, CommunicationSettingsRepository settingsRepo,
                               MongoOperations mongo) {
        this.resolver = resolver;
        this.router = router;
        this.settingsRepo = settingsRepo;
        this.mongo = mongo;
    }

    public MapLinkResolver.Result resolve(String link) {
        return resolver.resolve(link);
    }

    /** Stores the home link and its point (null when unreadable) and works out both distances. */
    public void setHome(Candidate c, String link, GeoPoint home) {
        c.setHomeMapLink(StringUtils.hasText(link) ? link.trim() : null);
        c.setHomeLat(home == null ? null : home.lat());
        c.setHomeLng(home == null ? null : home.lng());
        GeoPoint company = home == null ? null : companyPoint().orElse(null);
        c.setHomeDistanceKm(company == null || home == null ? null : home.kmTo(company));
        c.setHomeRoad(null, null, null, null);
        if (company != null && home != null) {
            Optional<RoadRouter.Route> road = router.route(home, company);
            c.setHomeRoad(road.map(RoadRouter.Route::km).orElse(null), road.map(RoadRouter.Route::minutes).orElse(null),
                    road.map(RoadRouter.Route::source).orElse(null), Instant.now());
        }
    }

    public static Optional<GeoPoint> homeOf(Candidate c) {
        return c.getHomeLat() == null || c.getHomeLng() == null ? Optional.empty() : Optional.of(new GeoPoint(c.getHomeLat(), c.getHomeLng()));
    }

    /** Google Maps directions by car from the candidate's home to the company. */
    public Optional<String> directionsUrl(Candidate c) {
        Optional<GeoPoint> home = homeOf(c);
        Optional<GeoPoint> company = companyPoint();
        if (home.isEmpty() || company.isEmpty()) return Optional.empty();
        return Optional.of("https://www.google.com/maps/dir/?api=1&origin=" + home.get().text() + "&destination=" + company.get().text()
                + "&travelmode=driving");
    }

    /** The company pin; read from the pin link (or location link) the first time it is needed after it changes. */
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

    /** Version 2: positions are only read from the link itself, never from the Google Maps page (wrong abroad). */
    static final int PIN_RULES = 2;

    /**
     * Runs once after the reading rules change: reads every saved home link and the company pin again, so a pin
     * read the old way (for example a page centred on the server's region, thousands of km away) is corrected.
     */
    public void recheckIfRulesChanged() {
        CommunicationSettings s = settingsRepo.findById(CommunicationSettings.SINGLETON_ID).orElse(null);
        if (s == null || s.getPinRulesVersion() >= PIN_RULES) {
            fillMissingRoads();
            return;
        }
        List<Candidate> withLink = mongo.find(Query.query(Criteria.where("homeMapLink").ne(null)), Candidate.class);
        if (!withLink.isEmpty()) {
            BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Candidate.class);
            for (Candidate c : withLink) {
                GeoPoint home = resolver.resolve(c.getHomeMapLink()).point();
                bulk.updateOne(Query.query(Criteria.where("_id").is(c.getId())), clearRoad(new Update()
                        .set("homeLat", home == null ? null : home.lat())
                        .set("homeLng", home == null ? null : home.lng())
                        .set("homeDistanceKm", null)));
            }
            bulk.execute();
        }
        s.setPinRulesVersion(PIN_RULES);
        refreshCompanyPin(s);
    }

    /** New straight-line distances for everyone; road distances are looked up again in the background. */
    private void recalculateAll(GeoPoint company) {
        List<Candidate> withHome = mongo.find(Query.query(Criteria.where("homeLat").ne(null)), Candidate.class);
        if (withHome.isEmpty()) return;
        BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Candidate.class);
        for (Candidate c : withHome) {
            Double km = company == null ? null : new GeoPoint(c.getHomeLat(), c.getHomeLng()).kmTo(company);
            bulk.updateOne(Query.query(Criteria.where("_id").is(c.getId())), clearRoad(new Update().set("homeDistanceKm", km)));
        }
        bulk.execute();
        if (company != null) CompletableFuture.runAsync(this::fillMissingRoads);
    }

    /** Looks up every road distance again (for example after the Google road distance link is set). */
    public void redoRoads() {
        mongo.updateMulti(Query.query(Criteria.where("homeLat").ne(null)), clearRoad(new Update()), Candidate.class);
        CompletableFuture.runAsync(this::fillMissingRoads);
    }

    /** Null when the road distance link works; otherwise what is wrong with it. */
    public String checkRoadScript(String url) {
        GeoPoint company = companyPoint().orElse(new GeoPoint(11.0415413, 77.3206051));
        return router.checkScript(url, new GeoPoint(company.lat() + 0.02, company.lng() + 0.02), company);
    }

    private static Update clearRoad(Update u) {
        return u.set("homeRoadKm", null).set("homeRoadMinutes", null).set("homeRoadSource", null).set("homeRoadCheckedAt", null);
    }

    /**
     * Looks up road distances that are missing, failed earlier, or came from another map service than the one now
     * configured (for example after a Google Maps key is added). Runs every 10 minutes and after the company pin moves.
     */
    @Scheduled(initialDelay = 120_000, fixedDelay = 600_000)
    public void fillMissingRoads() {
        again.set(true);
        if (!filling.compareAndSet(false, true)) return; // the running fill sees "again" and goes round once more
        try {
            while (again.getAndSet(false)) fillOnce();
        } catch (RuntimeException e) {
            log.warn("Filling road distances failed: {}", e.getClass().getSimpleName());
        } finally {
            filling.set(false);
        }
    }

    /** Fills batches until nothing is due; stops early (and is run again) when the company pin moves meanwhile. */
    private void fillOnce() {
        Optional<GeoPoint> company = companyPoint();
        if (company.isEmpty()) return;
        for (int batch = 0; batch < 50; batch++) {
            Criteria due = new Criteria().orOperator(Criteria.where("homeRoadCheckedAt").is(null),
                    Criteria.where("homeRoadCheckedAt").lt(Instant.now().minus(RETRY_AFTER)));
            Query q = Query.query(new Criteria().andOperator(Criteria.where("homeLat").ne(null),
                    Criteria.where("homeRoadSource").ne(router.source()), due)).limit(ROADS_PER_RUN);
            List<Candidate> todo = mongo.find(q, Candidate.class);
            if (todo.isEmpty()) return;
            for (Candidate c : todo) {
                Optional<RoadRouter.Route> road = router.route(new GeoPoint(c.getHomeLat(), c.getHomeLng()), company.get());
                if (!company.equals(companyPoint())) {
                    again.set(true);
                    return;
                }
                Update u = new Update().set("homeRoadCheckedAt", Instant.now());
                if (road.isPresent()) {
                    u.set("homeRoadKm", road.get().km()).set("homeRoadMinutes", road.get().minutes()).set("homeRoadSource", road.get().source());
                }
                // only if the home pin is still the one the route was worked out for
                mongo.updateFirst(Query.query(Criteria.where("_id").is(c.getId()).and("homeLat").is(c.getHomeLat()).and("homeLng").is(c.getHomeLng())),
                        u, Candidate.class);
            }
        }
    }
}
