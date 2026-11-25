package com.healthDekho.backend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Spring AI's Bedrock chat model first asks the AWS SDK's own region chain (AWS_REGION env var,
 * {@code aws.region} system property, ~/.aws/config) before it applies {@code spring.ai.bedrock.aws.region}.
 * When none of those exist it logs a long stack trace at startup. This copies the configured region
 * (application.properties / local.properties) into {@code aws.region} so both agree and the warning goes away.
 * An explicit AWS_REGION environment variable or {@code -Daws.region} still wins.
 */
public class AwsRegionEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SPRING_REGION_PROPERTY = "spring.ai.bedrock.aws.region";
    static final String AWS_SDK_REGION_PROPERTY = "aws.region";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(System.getProperty(AWS_SDK_REGION_PROPERTY))
                || StringUtils.hasText(System.getenv("AWS_REGION"))) {
            return;
        }
        String region = environment.getProperty(SPRING_REGION_PROPERTY);
        if (StringUtils.hasText(region)) {
            System.setProperty(AWS_SDK_REGION_PROPERTY, region.trim());
        }
    }

    /** Run after config files (including the local.properties import) have been loaded. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
