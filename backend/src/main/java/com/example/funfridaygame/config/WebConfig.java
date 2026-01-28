package com.example.funfridaygame.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.getAllowedOriginsArray())
                .allowedMethods(corsProperties.getAllowedMethodsArray())
                .allowedHeaders(corsProperties.getAllowedHeaders())
                .allowCredentials(corsProperties.isAllowCredentials());

        registry.addMapping("/ws/**")
                .allowedOrigins(corsProperties.getAllowedOriginsArray())
                .allowedMethods(corsProperties.getAllowedMethodsArray())
                .allowedHeaders(corsProperties.getAllowedHeaders())
                .allowCredentials(corsProperties.isAllowCredentials());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve Angular static files from /ui/**
        registry.addResourceHandler("/ui/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(
                        new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location)
                                    throws java.io.IOException {
                                Resource requestedResource = location.createRelative(resourcePath);
                                // Return index.html for Angular SPA routing
                                return requestedResource.exists() && requestedResource.isReadable()
                                        ? requestedResource
                                        : new ClassPathResource("/static/index.html");
                            }
                        });
    }
}
