package com.wuhao.aiemotion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Value("${upload.base-dir:${user.home}/ai-emotion/uploads}")
    private String uploadDir;

    @Value("${app.upload.public-path:/uploads}")
    private String publicPath;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String[] corsAllowedOrigins;

    @Value("${app.cors.allow-credentials:true}")
    private boolean corsAllowCredentials;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String[] corsAllowedMethods;

    @Value("${app.cors.allowed-headers:Authorization,Content-Type}")
    private String[] corsAllowedHeaders;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 例：/uploads/** -> 映射到本地目录
        String pattern = publicPath.endsWith("/") ? publicPath + "**" : publicPath + "/**";
        String absoluteUploadDir = java.nio.file.Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        String location = "file:" + (absoluteUploadDir.endsWith("/") ? absoluteUploadDir : absoluteUploadDir + "/");
        registry.addResourceHandler(pattern).addResourceLocations(location);
    }


    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsAllowedOrigins)
                .allowedMethods(corsAllowedMethods)
                .allowedHeaders(corsAllowedHeaders)
                .allowCredentials(corsAllowCredentials);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}
