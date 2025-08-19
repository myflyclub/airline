package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.patson.data.GoogleResourceSource;
import com.patson.model.Airport;
import com.patson.model.google.GoogleResource;
import com.patson.model.google.ResourceType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.Json;
import scala.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GoogleImageUtil {
	private static final String API_KEY = loadApiKey();

	private static String loadApiKey() {
		Config configFactory = ConfigFactory.load();
		return configFactory.hasPath("google.apiKey") ? configFactory.getString("google.apiKey") : null;
	}

	private final static Logger logger = LoggerFactory.getLogger(GoogleImageUtil.class);
	private final static int MAX_PHOTO_WIDTH = 480;
	private final static int SEARCH_RADIUS = 50000; //50km

	private static LoadingCache<CityKey, Optional<ImageResult>> cityCache = CacheBuilder.newBuilder().maximumSize(100000).expireAfterWrite(1, TimeUnit.DAYS).build(new ResourceCacheLoader<>(key -> loadCityImageUrl(key.latitude, key.longitude), ResourceType.CITY_IMAGE().id()));
	private static LoadingCache<AirportKey, Optional<ImageResult>> airportCache = CacheBuilder.newBuilder().maximumSize(100000).expireAfterWrite(1, TimeUnit.DAYS).build(new ResourceCacheLoader<>(key -> 	loadAirportImageUrl(key.latitude, key.longitude), ResourceType.AIRPORT_IMAGE().id()));

	private interface LoadFunction<T, R> {
		R apply(T t) throws IOException;
	}

	private static class ResourceCacheLoader<KeyType extends Key> extends CacheLoader<KeyType, Optional<ImageResult>> {
		private static final int DEFAULT_MAX_AGE = 24 * 60 * 60; //in sec
		private final LoadFunction<KeyType, ImageResult> loadFunction;
		private final int resourceTypeValue;

		private ResourceCacheLoader(LoadFunction<KeyType, ImageResult> loadFunction, int resourceTypeValue) {
			this.loadFunction = loadFunction;
			this.resourceTypeValue = resourceTypeValue;
		}

		public Optional<ImageResult> load(KeyType key) {
			logger.info("Loading google resource on " + key);
			//try from db first
			Option<GoogleResource> googleResourceOption = GoogleResourceSource.loadResource(key.getId(), ResourceType.apply(resourceTypeValue));

			if (googleResourceOption.isDefined()) {
				logger.info("Found previous google resource on " + key + " resource " + googleResourceOption.get());
				GoogleResource googleResource = googleResourceOption.get();
				if (googleResource.url() == null) { //previous successful query returns no result, do not proceed
					return Optional.empty();
				}
				if (!googleResource.maxAgeDeadline().isEmpty() && System.currentTimeMillis() <= (Long) googleResource.maxAgeDeadline().get()) {
					try {
						return Optional.of(new ImageResult(new URL(googleResource.url()), googleResource.caption(), null));
					} catch (MalformedURLException e) {
						logger.warn("Stored URL is malformed: " + e.getMessage(), e);
					}
				} else { //max deadline expired, try and see if the url still works
					Optional<Long> newDeadline = isUrlValid(googleResource.url());
					if (newDeadline != null) {
						GoogleResourceSource.insertResource().apply(GoogleResource.apply(googleResource.resourceId(), googleResource.resourceType(), googleResource.url(), newDeadline.isPresent() ? Option.apply(newDeadline.get()) : Option.empty(), googleResource.caption()));
						try {
							return Optional.of(new ImageResult(new URL(googleResource.url()), googleResource.caption(), null));
						} catch (MalformedURLException e) {
							logger.warn("Stored URL is malformed: " + e.getMessage(), e);
						}
					}
				}
			} else {
				logger.info("No previous google resource on " + key);
			}

			//no previous successful query done, or the result is no longer valid
			try {
				ImageResult result = loadFunction.apply(key);
				logger.info("loaded " + ResourceType.apply(resourceTypeValue) + " image for  " + key + " " + result);
				if (result != null) {
					long deadline = System.currentTimeMillis() + (result.maxAge != null ? result.maxAge * 1000 : DEFAULT_MAX_AGE * 1000);
					GoogleResourceSource.insertResource().apply(GoogleResource.apply(key.getId(), ResourceType.apply(resourceTypeValue), result.url.toString(), Option.apply(deadline), result.placeName));

					return Optional.of(result);
				} else { //There is no result, save to DB, as we do not want to retry this at all
					GoogleResourceSource.insertResource().apply(GoogleResource.apply(key.getId(), ResourceType.apply(resourceTypeValue), null, Option.empty(), ""));
					return Optional.empty();
				}
			} catch (Throwable t) {
				logger.warn("Unexpected failure for google resource loading on " + key + " : " + t.getMessage(), t);
				return Optional.empty();
			}
		}
	}

	public static void invalidate(Key key) {
		if (key instanceof CityKey) {
			cityCache.invalidate(key);
			GoogleResourceSource.deleteResource(key.getId(), ResourceType.CITY_IMAGE());
		} else if (key instanceof AirportKey) {
			airportCache.invalidate(key);
			GoogleResourceSource.deleteResource(key.getId(), ResourceType.AIRPORT_IMAGE());
		}
	}

	private static Optional<Long> isUrlValid(String urlString) {
		URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.warn("URL " + urlString + " is not valid : " + e.getMessage(), e);
			return null;
		}

		HttpURLConnection conn = null;

		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			if (conn.getResponseCode() == 200) {
				Long maxAge = getMaxAge(conn);
				if (maxAge != null) {
					long newDeadline = System.currentTimeMillis() + maxAge * 1000;
					logger.debug(urlString + " is still valid, new max age deadline: " + newDeadline) ;
					return Optional.of(newDeadline);
				} else {
					logger.debug(urlString + " is still valid, no max age deadline");
					return Optional.empty();
				}
			} else {
				logger.info(urlString + " is no longer valid " + conn.getResponseCode());
				return null;
			}
		} catch (IOException e) {
			logger.warn(urlString + " failed with valid check : " + e.getMessage());
			return null;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}


	}

	private static Long getMaxAge(HttpURLConnection conn) {
		String cacheControl = conn.getHeaderField("Cache-Control");
		if (cacheControl != null) {
			for (String entry : cacheControl.split(",")) {
				entry = entry.toLowerCase().trim();
				if (entry.startsWith("max-age=")) {
					try {
						return Long.valueOf(entry.substring("max-age=".length()).trim());
					} catch (NumberFormatException e) {
						logger.warn("Invalid max-age : " + entry);
					}
				}
			}
		}
		return null;
	}

	public static abstract class Key {
		abstract int getId();
		abstract double getLatitude();
		abstract double getLongitude();
	}

	public static class CityKey extends Key {
		private final int id;
		private String cityName;
		private double latitude;
		private double longitude;

		public CityKey(int id, String cityName, double latitude, double longitude) {
			this.id = id;
			this.cityName = cityName;
			this.latitude = latitude;
			this.longitude = longitude;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			CityKey cityKey = (CityKey) o;

			return id == cityKey.id;
		}

		@Override
		public int hashCode() {
			return id;
		}

		@Override
		public String toString() {
			return "CityKey{" +
					"id=" + id +
					", cityName='" + cityName + '\'' +
					", latitude=" + latitude +
					", longitude=" + longitude +
					'}';
		}

		@Override
		public int getId() {
			return id;
		}
		@Override
		public double getLatitude() { return latitude; }
		@Override
		public double getLongitude() { return longitude; }
	}

	public static class AirportKey extends Key{
		private final int id;
		private String airportName;
		private double latitude;
		private double longitude;

		public AirportKey(int id, String airportName, double latitude, double longitude) {
			this.id = id;
			this.airportName = airportName;
			this.latitude = latitude;
			this.longitude = longitude;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			AirportKey that = (AirportKey) o;

			return id == that.id;
		}

		@Override
		public int hashCode() {
			return id;
		}

		@Override
		public String toString() {
			return "AirportKey{" +
					"id=" + id +
					", airportName='" + airportName + '\'' +
					", latitude=" + latitude +
					", longitude=" + longitude +
					'}';
		}

		@Override
		public int getId() {
			return id;
		}
		@Override
		public double getLatitude() { return latitude; }
		@Override
		public double getLongitude() { return longitude; }
	}



	public static ImageResult getCityImage(Airport airport) {
		try {
			Optional<ImageResult> result = cityCache.get(new CityKey(airport.id(), airport.city(), airport.latitude(), airport.longitude()));
			return result.orElse(null);
		} catch (Exception e) {
			logger.warn("Error getting city image for airport " + airport, e);
			return null;
		}
	}

	public static URL getCityImageUrl(Airport airport) {
		ImageResult imageResult = getCityImage(airport);
		return imageResult != null ? imageResult.url : null;
	}

	public static ImageResult getAirportImage(Airport airport) {
		try {
			Optional<ImageResult> result = airportCache.get(new AirportKey(airport.id(), airport.name(), airport.latitude(), airport.longitude()));
			return result.orElse(null);
		} catch (Exception e) {
			logger.warn("Error getting airport image for airport " + airport, e);
			return null;
		}
	}

	public static URL getAirportImageUrl(Airport airport) {
		ImageResult imageResult = getAirportImage(airport);
		return imageResult != null ? imageResult.url : null;
	}


	private static ImageResult loadCityImageUrl(double latitude, double longitude) throws IOException {
		return fetchImageUrlFromGoogle(Arrays.asList("tourist_attraction", "historical_landmark", "park", "locality"), latitude, longitude);
	}

	private static ImageResult loadAirportImageUrl(double latitude, double longitude) throws IOException {
		return fetchImageUrlFromGoogle(Collections.singletonList("airport"), latitude, longitude);
	}

	private static ImageResult fetchImageUrlFromGoogle(List<String> includedTypes, double latitude, double longitude) throws IOException {
		if (API_KEY == null) {
			logger.warn("Cannot fetch image, Google API key is not configured.");
			return null;
		}

		// Step 1: Nearby Search to get a photo name
		PhotoInfo photoInfo = findPhotoInfo(includedTypes, latitude, longitude);

		if (photoInfo == null || photoInfo.photoName == null) {
			logger.info("No photo found for types {} at {},{}", includedTypes, latitude, longitude);
			return null;
		}

		// Step 2: Place Photo to get the image URL
		ImageResult urlOnlyResult = getPhotoUrl(photoInfo.photoName);
		if (urlOnlyResult == null) {
			return null;
		}
		return new ImageResult(urlOnlyResult.url, photoInfo.placeName, urlOnlyResult.maxAge);
	}

	private static PhotoInfo findPhotoInfo(List<String> includedTypes, double latitude, double longitude) throws IOException {
		URL url = new URL("https://places.googleapis.com/v1/places:searchNearby");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		conn.setRequestProperty("X-Goog-Api-Key", API_KEY);
		conn.setRequestProperty("X-Goog-FieldMask", "places.photos,places.displayName");
		conn.setDoOutput(true);

		ObjectNode requestBodyNode = Json.newObject();
		ArrayNode typesNode = requestBodyNode.putArray("includedTypes");
		includedTypes.forEach(typesNode::add);
		requestBodyNode.put("maxResultCount", 1);
		ObjectNode locationRestrictionNode = requestBodyNode.putObject("locationRestriction");
		ObjectNode circleNode = locationRestrictionNode.putObject("circle");
		circleNode.putObject("center").put("latitude", latitude).put("longitude", longitude);
		circleNode.put("radius", SEARCH_RADIUS);

		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = requestBodyNode.toString().getBytes(StandardCharsets.UTF_8);
			os.write(input, 0, input.length);
		}

		int responseCode = conn.getResponseCode();
		if (responseCode != 200) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
				String errorResponse = br.lines().collect(Collectors.joining(""));
				logger.warn("Nearby Search failed with code {}: {}", responseCode, errorResponse);
			}
			return null;
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
			String response = br.lines().collect(Collectors.joining(""));
			JsonNode result = Json.parse(response);

			JsonNode places = result.get("places");
			if (places != null && places.isArray() && places.size() > 0) {
				JsonNode firstPlace = places.get(0);
				JsonNode photos = firstPlace.get("photos");
				if (photos != null && photos.isArray() && photos.size() > 0) {
					String photoName = photos.get(0).get("name").asText();
					String placeName = null;
					if (firstPlace.has("displayName")) {
						JsonNode displayNameNode = firstPlace.get("displayName");
						if (displayNameNode != null && displayNameNode.has("text")) {
							placeName = displayNameNode.get("text").asText();
						}
					}
					return new PhotoInfo(photoName, placeName);
				}
			}
		} finally {
			conn.disconnect();
		}

		return null;
	}

	private static ImageResult getPhotoUrl(String photoName) throws IOException {
		URL url = new URL("https://places.googleapis.com/v1/" + photoName + "/media?maxWidthPx=" + MAX_PHOTO_WIDTH + "&key=" + API_KEY);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setInstanceFollowRedirects(false); // We want to get the redirect location
		conn.connect();

		ImageResult result = null;
		int responseCode = conn.getResponseCode();
		if (responseCode >= 300 && responseCode < 400) { // Handle redirects
			String location = conn.getHeaderField("Location");
			if (location != null) {
				result = new ImageResult(new URL(location), null, getMaxAge(conn));
			}
		} else {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
				String errorResponse = br.lines().collect(Collectors.joining(""));
				logger.warn("Place Photo request failed for {} with code {}: {}", photoName, responseCode, errorResponse);
			}
		}

		conn.disconnect();
		return result;
	}

	private static class PhotoInfo {
		private final String photoName;
		private final String placeName;

		private PhotoInfo(String photoName, String placeName) {
			this.photoName = photoName;
			this.placeName = placeName;
		}
	}

	public static class ImageResult {
		public final URL url;
		public final String placeName;
		final Long maxAge;

		ImageResult(URL url, String placeName, Long maxAge) {
			this.url = url;
			this.placeName = placeName;
			this.maxAge = maxAge;
		}

		@Override
		public String toString() {
			return "ImageResult{" +
					"url=" + url +
					", placeName='" + placeName + '\'' +
					", maxAge=" + maxAge +
					'}';
		}
	}
}
