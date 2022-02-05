package org.coderclan.whistle.example.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {
    @Value("${spring.application.name}")
    private String whistleSystemName;

    @Bean
    public String whistleSystemName() {
        return whistleSystemName;
    }
}

