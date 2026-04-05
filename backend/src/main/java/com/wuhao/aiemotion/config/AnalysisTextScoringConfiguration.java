package com.wuhao.aiemotion.config;

import com.wuhao.aiemotion.service.AnalysisTextScoringProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnalysisTextScoringProperties.class)
public class AnalysisTextScoringConfiguration {
}
