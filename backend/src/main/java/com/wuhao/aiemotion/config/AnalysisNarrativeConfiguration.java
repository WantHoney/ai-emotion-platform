package com.wuhao.aiemotion.config;

import com.wuhao.aiemotion.service.AnalysisNarrativeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnalysisNarrativeProperties.class)
public class AnalysisNarrativeConfiguration {
}
