package com.example.funfridaygame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Fun Friday — real-time multiplayer party games.
 *
 * <p>Game modules in {@code com.example.funfridaygame.games.*} are picked up by component scanning;
 * everything else in this application is game-agnostic.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FunFridayGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(FunFridayGameApplication.class, args);
    }
}
