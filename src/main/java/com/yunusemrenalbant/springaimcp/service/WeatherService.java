package com.yunusemrenalbant.springaimcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunusemrenalbant.springaimcp.model.WeatherData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    @Value("${weatherstack.api.key}")
    private String apiKey;

    public WeatherService(@Value("${weatherstack.api.base-url:http://api.weatherstack.com}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Tool(name = "get_current_weather", description = "Belirli bir şehir veya konum için güncel hava durumu bilgisini alır. Sıcaklık, nem, rüzgar, yağış vb. tüm verileri içerir.")
    @Cacheable(value = "weatherCache", key = "#location", unless = "#result == null")
    public WeatherData getCurrentWeather(String location) {
        try {
            String responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/current")
                            .queryParam("access_key", apiKey)
                            .queryParam("query", location)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                logger.error("API error: {}", root.get("error").get("info").asText());
                return null;
            }

            JsonNode locationData = root.get("location");
            JsonNode currentData = root.get("current");

            return new WeatherData(
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

        String desc = data.weatherDescriptions().toLowerCase(Locale.ENGLISH);
        boolean badWeatherDescription = desc.contains("rain") || desc.contains("snow") ||
                desc.contains("storm") || desc.contains("fog");

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