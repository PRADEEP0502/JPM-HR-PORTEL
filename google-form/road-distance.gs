/**
 * JPM HR Portal - road distance from Google Maps (free, no API key, no billing).
 *
 * The HR portal calls this web app with two map points and gets back Google's road distance and driving time by car,
 * the same numbers Google Maps shows. It uses Apps Script's built-in Maps service, which is free with a Google
 * account (about 1,000 route lookups a day). Answers are kept for 6 hours so repeated lookups use no quota.
 *
 * Set-up (once): see "Road distance from Google Maps" in google-form/README.md.
 *   1. script.google.com → New project → paste this whole file → Save.
 *   2. Deploy → New deployment → type "Web app" → Execute as "Me" → Who has access "Anyone" → Deploy → Authorize.
 *   3. Copy the Web app URL (https://script.google.com/macros/s/.../exec) into the portal:
 *      Settings → Communication → "Google road distance link" → Save.
 * Keep the link private: anyone who has it can use your daily Google Maps allowance.
 */

function doGet(e) {
  var p = (e && e.parameter) || {};
  var from = parsePoint_(p.from);
  var to = parsePoint_(p.to);
  if (!from || !to) return json_({ error: 'from and to must be "latitude,longitude"' });

  var cache = CacheService.getScriptCache();
  var cacheKey = 'r:' + from.join(',') + ':' + to.join(',');
  var cached = cache.get(cacheKey);
  if (cached) return ContentService.createTextOutput(cached).setMimeType(ContentService.MimeType.JSON);

  try {
    var directions = Maps.newDirectionFinder()
      .setOrigin(from[0], from[1])
      .setDestination(to[0], to[1])
      .setMode(Maps.DirectionFinder.Mode.DRIVING)
      .setRegion('in')
      .getDirections();
    var route = directions.routes && directions.routes[0];
    if (!route) return json_({ error: directions.status || 'NO_ROUTE' });
    var meters = 0;
    var seconds = 0;
    route.legs.forEach(function (leg) {
      meters += leg.distance.value;
      seconds += leg.duration.value;
    });
    var body = JSON.stringify({ distanceMeters: meters, durationSeconds: seconds, source: 'Google Maps' });
    cache.put(cacheKey, body, 6 * 60 * 60);
    return ContentService.createTextOutput(body).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return json_({ error: String((err && err.message) || err) });
  }
}

/** "11.0415413,77.3206051" → [11.04154, 77.32061] (5 decimals ≈ 1 m), or null. */
function parsePoint_(text) {
  var m = String(text || '').match(/^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$/);
  if (!m) return null;
  var lat = Math.round(Number(m[1]) * 1e5) / 1e5;
  var lng = Math.round(Number(m[2]) * 1e5) / 1e5;
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;
  return [lat, lng];
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/** Run this once from the editor to check that it works (it logs the road distance between two Tiruppur points). */
function testRoadDistance() {
  var res = doGet({ parameter: { from: '11.1085,77.3411', to: '11.0415413,77.3206051' } });
  Logger.log(res.getContent());
}
