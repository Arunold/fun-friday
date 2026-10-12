package com.example.funfridaygame.testkit;

import com.example.funfridaygame.runtime.ActionConverter;
import com.example.funfridaygame.sdk.InvalidActionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import tools.jackson.databind.json.JsonMapper;

/** Converts payloads with Jackson and validates them with Bean Validation, like the platform. */
public final class JsonActionConverter implements ActionConverter {

    private final JsonMapper mapper;
    private final Validator validator;

    public JsonActionConverter(JsonMapper mapper) {
        this.mapper = mapper;
        this.validator =
                Validation.byDefaultProvider()
                        .configure()
                        .messageInterpolator(new ParameterMessageInterpolator())
                        .buildValidatorFactory()
                        .getValidator();
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
