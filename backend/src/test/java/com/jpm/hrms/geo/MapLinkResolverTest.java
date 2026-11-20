package com.jpm.hrms.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapLinkResolverTest {
    private final MapLinkResolver resolver = new MapLinkResolver();

    @Test
    void readsThePlacePinBeforeTheMapCentre() {
        var p = MapLinkResolver.fromUrl("https://www.google.com/maps/place/Home/@11.0500000,77.3000000,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d11.0422548!4d77.3193728");
        assertThat(p).contains(new GeoPoint(11.0422548, 77.3193728));
    }

    @Test
    void readsDroppedPinAndSearchLinks() {
        assertThat(MapLinkResolver.fromUrl("https://maps.google.com/?q=11.1085,77.3411&entry=gps")).contains(new GeoPoint(11.1085, 77.3411));
        assertThat(MapLinkResolver.fromUrl("https://www.google.com/maps/search/?api=1&query=11.1085%2C77.3411")).contains(new GeoPoint(11.1085, 77.3411));
        assertThat(MapLinkResolver.fromUrl("https://www.google.com/maps/search/11.1085,+77.3411?entry=tts")).contains(new GeoPoint(11.1085, 77.3411));
        assertThat(MapLinkResolver.fromUrl("https://www.google.com/maps/@11.2,77.4,15z")).contains(new GeoPoint(11.2, 77.4));
        assertThat(MapLinkResolver.fromUrl("https://maps.app.goo.gl/ZXe9kpP8ytAnNGGa9")).isEmpty();
    }

    @Test
    void acceptsPlainCoordinatesWithoutTheNetwork() {
        assertThat(resolver.resolve(" 11.0423, 77.3194 ").point()).isEqualTo(new GeoPoint(11.0423, 77.3194));
        assertThat(resolver.resolve("(11.0423 77.3194)").point()).isEqualTo(new GeoPoint(11.0423, 77.3194));
        assertThat(resolver.resolve("Check my home: https://maps.google.com/?q=11.1,77.2.").point()).isEqualTo(new GeoPoint(11.1, 77.2));
    }

    @Test
    void refusesAnythingThatIsNotAGoogleMapsLink() {
        assertThat(resolver.resolve("Anupparpalayam, Tiruppur").found()).isFalse();
        assertThat(resolver.resolve("https://example.com/home").problem()).startsWith("Only Google Maps links");
        assertThat(resolver.resolve("http://169.254.169.254/latest").found()).isFalse();
        assertThat(resolver.resolve("95.0, 77.0").found()).isFalse();
        assertThat(MapLinkResolver.allowedHost("https://maps.app.goo.gl/abc")).isTrue();
        assertThat(MapLinkResolver.allowedHost("https://www.google.co.in/maps/place/x")).isTrue();
        assertThat(MapLinkResolver.allowedHost("https://google.com.evil.example/maps")).isFalse();
        assertThat(MapLinkResolver.allowedHost("http://maps.app.goo.gl/abc")).isFalse();
    }

    @Test
    void measuresStraightLineDistance() {
        GeoPoint company = new GeoPoint(11.0422548, 77.3193728);
        assertThat(company.kmTo(company)).isZero();
        // Tiruppur company to central Coimbatore is about 37 km in a straight line
        assertThat(company.kmTo(new GeoPoint(11.0168, 76.9558))).isBetween(38.0, 41.0);
    }
}
