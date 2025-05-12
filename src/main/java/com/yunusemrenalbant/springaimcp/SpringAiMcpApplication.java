package com.yunusemrenalbant.springaimcp;

import com.yunusemrenalbant.springaimcp.service.WeatherService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class SpringAiMcpApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringAiMcpApplication.class, args);
	}

	@Bean
	public List<ToolCallback> appTools(WeatherService weatherService) {
		return Arrays.asList(
				ToolCallbacks.from(weatherService)[0],
				ToolCallbacks.from(weatherService)[1],
				ToolCallbacks.from(weatherService)[2]
		);
	}
}
