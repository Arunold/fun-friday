package com.example.funfridaygame.engine;

import com.example.funfridaygame.runtime.ActionConverter;
import com.example.funfridaygame.sdk.InvalidActionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Converts action payloads with the application's Jackson mapper and validates them. */
@Component
public class SpringActionConverter implements ActionConverter {

    private final JsonMapper mapper;
    private final Validator validator;

    public SpringActionConverter(JsonMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public <T> T convert(Object payload, Class<T> type) {
        T value;
        try {
            value = mapper.convertValue(payload == null ? Map.of() : payload, type);
        } catch (RuntimeException e) {
            throw new InvalidActionException("Invalid " + type.getSimpleName() + " payload");
        }
        if (value == null) {
            throw new InvalidActionException("Missing " + type.getSimpleName() + " payload");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.iterator().next();
            throw new InvalidActionException(first.getPropertyPath() + " " + first.getMessage());
        }
        return value;
    }
}
