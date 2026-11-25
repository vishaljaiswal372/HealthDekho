package com.healthDekho.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PolicyProperties.class, PaymentProperties.class, SearchProperties.class,
        AiProperties.class})
public class BusinessConfig {
}
