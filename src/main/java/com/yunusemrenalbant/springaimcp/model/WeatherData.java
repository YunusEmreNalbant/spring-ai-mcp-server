package com.yunusemrenalbant.springaimcp.model;

import java.time.LocalDateTime;

public record WeatherData(
        String location,
        String country,
        String region,
        double latitude,
        double longitude,
        LocalDateTime localTime,
        int temperature,
        String weatherDescriptions,
        String weatherIcon,
        int windSpeed,
        String windDirection,
        int pressure,
        int precipitation,
        int humidity,
        int cloudCover,
        int feelsLike,
        int uvIndex,
        int visibility,
        boolean isDay
) {}