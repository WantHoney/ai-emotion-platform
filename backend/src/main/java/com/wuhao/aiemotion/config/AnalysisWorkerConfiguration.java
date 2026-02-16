package com.wuhao.aiemotion.config;

import com.wuhao.aiemotion.service.AnalysisWorkerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AnalysisWorkerProperties.class)
public class AnalysisWorkerConfiguration {
}
