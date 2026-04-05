package com.wuhao.aiemotion.config;

import com.wuhao.aiemotion.service.AnalysisConsistencyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnalysisConsistencyProperties.class)
public class AnalysisConsistencyConfiguration {
}
