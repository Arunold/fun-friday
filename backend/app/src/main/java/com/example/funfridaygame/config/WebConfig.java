package com.example.funfridaygame.config;

import java.time.InstantSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HTTP configuration: CORS for the REST API (only needed when the UI runs on another origin) and
 * routes of the single-page app.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    public WebConfig(AppProperties properties) {
        this.properties = properties;
    }

    /** The application clock (replaceable in tests). */
    @Bean
    public InstantSource clock() {
        return InstantSource.system();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.corsOrigins().toArray(String[]::new))
                .allowedMethods("GET")
                .maxAge(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Old links pointed at /ui/#/...; the app now lives at the root.
        registry.addRedirectViewController("/ui", "/");
        registry.addRedirectViewController("/ui/", "/");
        // Client-side routes are served by the SPA's index.html (deep links and refreshes).
        for (String route :
                new String[] {"/room/**", "/join/**", "/host/**", "/oops/**", "/history"}) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
