package com.limasegura.limasegurabackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "worker")
@Getter
@Setter
public class WorkerTokenConfig {
    private String apiToken;
}
