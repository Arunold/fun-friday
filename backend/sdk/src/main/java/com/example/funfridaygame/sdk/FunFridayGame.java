package com.example.funfridaygame.sdk;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

/**
 * Marks a {@link GameDefinition} so the platform registers it automatically.
 *
 * <p>The class must live in a sub-package of {@code com.example.funfridaygame.games}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface FunFridayGame {}
