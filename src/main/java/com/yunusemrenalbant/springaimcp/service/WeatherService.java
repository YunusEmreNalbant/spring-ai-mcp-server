package com.yunusemrenalbant.springaimcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunusemrenalbant.springaimcp.model.WeatherData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${weatherstack.api.key}")
    private String apiKey;

    @Value("${weatherstack.api.base-url:http://api.weatherstack.com}")
    private String baseUrl;

    private final Map<String, WeatherData> cache = new HashMap<>();
    private final long CACHE_TTL = 30 * 60 * 1000;
    private Map<String, Long> cacheTimestamps = new HashMap<>();

    @Tool(name = "get_current_weather", description = "Belirli bir şehir veya konum için güncel hava durumu bilgisini alır. Sıcaklık, nem, rüzgar, yağış vb. tüm verileri içerir.")
    public WeatherData getCurrentWeather(String location) {
        if (cache.containsKey(location)) {
            long timestamp = cacheTimestamps.getOrDefault(location, 0L);
            if (System.currentTimeMillis() - timestamp < CACHE_TTL) {
                logger.info("Cache hit for location: {}", location);
                return cache.get(location);
            }
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/current")
                    .queryParam("access_key", apiKey)
                    .queryParam("query", location)
                    .build().toUriString();

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());

                if (root.has("error")) {
                    logger.error("API error: {}", root.get("error").get("info").asText());
                    return null;
                }

                JsonNode locationData = root.get("location");
                JsonNode currentData = root.get("current");

                WeatherData weatherData = new WeatherData(
                        locationData.get("name").asText(),
                        locationData.get("country").asText(),
                        locationData.get("region").asText(),
                        locationData.get("lat").asDouble(),
                        locationData.get("lon").asDouble(),
                        parseLocalTime(locationData.get("localtime").asText()),

                        currentData.get("temperature").asInt(),
                        currentData.get("weather_descriptions").get(0).asText(),
                        currentData.get("weather_icons").get(0).asText(),
                        currentData.get("wind_speed").asInt(),
                        currentData.get("wind_dir").asText(),
                        currentData.get("pressure").asInt(),
                        currentData.get("precip").asInt(),
                        currentData.get("humidity").asInt(),
                        currentData.get("cloudcover").asInt(),
                        currentData.get("feelslike").asInt(),
                        currentData.get("uv_index").asInt(),
                        currentData.get("visibility").asInt(),
                        currentData.get("is_day").asText().equals("yes")
                );

                cache.put(location, weatherData);
                cacheTimestamps.put(location, System.currentTimeMillis());

                return weatherData;
            } else {
                logger.error("API error: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error fetching weather data: {}", e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseLocalTime(String localTimeStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm");
            return LocalDateTime.parse(localTimeStr, formatter);
        } catch (Exception e) {
            logger.error("Error parsing date: {}", e.getMessage());
            return LocalDateTime.now();
        }
    }

    @Tool(name = "is_outdoor_weather_good", description = "Belirli bir konumdaki hava durumunun dış mekan aktiviteleri için uygun olup olmadığını değerlendirir.")
    public Map<String, Object> evaluateOutdoorWeather(String location) {
        WeatherData data = getCurrentWeather(location);
        Map<String, Object> result = new HashMap<>();

        if (data == null) {
            result.put("error", "Konum için hava durumu verisi alınamadı");
            return result;
        }

        boolean heavyRain = data.precipitation() > 5;
        boolean strongWind = data.windSpeed() > 40;
        boolean extremeTemperature = data.temperature() < 5 || data.temperature() > 35;
        boolean lowVisibility = data.visibility() < 5;
        boolean badWeatherDescription = data.weatherDescriptions().toLowerCase().contains("rain") ||
                data.weatherDescriptions().toLowerCase().contains("snow") ||
                data.weatherDescriptions().toLowerCase().contains("storm") ||
                data.weatherDescriptions().toLowerCase().contains("fog");

        boolean isSuitable = !(heavyRain || strongWind || extremeTemperature || lowVisibility || badWeatherDescription);

        result.put("location", data.location());
        result.put("isSuitableForOutdoor", isSuitable);
        result.put("temperature", data.temperature());
        result.put("weather", data.weatherDescriptions());
        result.put("windSpeed", data.windSpeed());
        result.put("precipitation", data.precipitation());
        result.put("visibility", data.visibility());
        result.put("humidity", data.humidity());
        result.put("uvIndex", data.uvIndex());
        result.put("isDay", data.isDay());

        if (!isSuitable) {
            Map<String, Boolean> reasons = new HashMap<>();
            reasons.put("heavyRain", heavyRain);
            reasons.put("strongWind", strongWind);
            reasons.put("extremeTemperature", extremeTemperature);
            reasons.put("lowVisibility", lowVisibility);
            reasons.put("badWeatherCondition", badWeatherDescription);
            result.put("unsuitableReasons", reasons);
        }

        return result;
    }

    @Tool(name = "get_temperature", description = "Belirli bir konum için sadece sıcaklık bilgisini getirir")
    public Map<String, Object> getTemperature(String location) {
        WeatherData data = getCurrentWeather(location);
        Map<String, Object> result = new HashMap<>();

        if (data == null) {
            result.put("error", "Konum için hava durumu verisi alınamadı");
            return result;
        }

        result.put("location", data.location());
        result.put("temperature", data.temperature());
        result.put("feelsLike", data.feelsLike());
        result.put("unit", "celsius");

        return result;
    }
}