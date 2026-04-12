package com.bin.bilibrain.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final AppProperties appProperties;
    private final ApiRequestLoggingInterceptor apiRequestLoggingInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = appProperties.getCors().getAllowedOrigins();
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins.toArray(String[]::new))
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRequestLoggingInterceptor)
            .addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String audioLocation = appProperties.getStorage()
            .getAudioDir()
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString();
        registry.addResourceHandler("/storage/audio/**")
            .addResourceLocations(audioLocation);
    }
}
