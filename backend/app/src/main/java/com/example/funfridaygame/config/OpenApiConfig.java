package com.example.funfridaygame.config;

import com.example.funfridaygame.catalog.GameCatalogController.GameSummary;
import com.example.funfridaygame.engine.RoomQueries.RoomSummary;
import com.example.funfridaygame.history.HistoryService.GameHistoryEntry;
import com.example.funfridaygame.history.HistoryService.LeaderboardEntry;
import com.example.funfridaygame.messaging.protocol.ClientRequests;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot;
import com.example.funfridaygame.messaging.protocol.ServerMessages;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document ({@code /v3/api-docs}, UI at {@code /swagger-ui.html}). Besides the REST API it
 * includes the schemas of the STOMP protocol, so TypeScript types can be generated for both ({@code
 * npm run generate:api}).
 *
 * <p>Two adjustments make the generated types precise:
 *
 * <ul>
 *   <li>Fields of server responses are marked <em>required</em> unless the record component is
 *       annotated with JSpecify's {@link Nullable} (null fields are omitted from the JSON).
 *   <li>Properties declared as {@code Object} (game views, payloads, setting values) become "any"
 *       instead of an empty object.
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    /** STOMP protocol types that no REST endpoint references. */
    private static final List<Class<?>> PROTOCOL_TYPES =
            List.of(
                    RoomSnapshot.class,
                    ServerMessages.Reply.class,
                    ServerMessages.Joined.class,
                    ServerMessages.Pong.class,
                    ServerMessages.ServerEvent.class,
                    ServerMessages.StreamMessage.class,
                    ClientRequests.CreateRoom.class,
                    ClientRequests.JoinRoom.class,
                    ClientRequests.Resume.class,
                    ClientRequests.Simple.class,
                    ClientRequests.Kick.class,
                    ClientRequests.UpdateSettings.class,
                    ClientRequests.SetReady.class,
                    ClientRequests.GameAction.class,
                    ClientRequests.Ping.class);

    /** Everything the server sends; their non-nullable fields are always present. */
    private static final List<Class<?>> RESPONSE_TYPES =
            List.of(
                    RoomSnapshot.class,
                    ServerMessages.Reply.class,
                    ServerMessages.Joined.class,
                    ServerMessages.Pong.class,
                    ServerMessages.ServerEvent.class,
                    ServerMessages.StreamMessage.class,
                    GameSummary.class,
                    RoomSummary.class,
                    GameHistoryEntry.class,
                    LeaderboardEntry.class);

    @Bean
    public OpenApiCustomizer protocolSchemas() {
        return openApi -> {
            openApi.info(
                    new Info()
                            .title("Fun Friday API")
                            .version("2")
                            .description(
                                    "REST endpoints plus the schemas of the STOMP protocol"
                                            + " (see docs/asyncapi.yaml)."));
            for (Class<?> type : PROTOCOL_TYPES) {
                @SuppressWarnings("rawtypes")
                Map<String, Schema> schemas = ModelConverters.getInstance().readAll(type);
                schemas.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            }
            markRequiredFields(openApi);
            openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::loosenObjects);
        };
    }

    private static void markRequiredFields(OpenAPI openApi) {
        Map<String, Class<?>> records = new LinkedHashMap<>();
        RESPONSE_TYPES.forEach(type -> collectRecords(type, records));
        records.forEach(
                (name, type) -> {
                    Schema<?> schema = openApi.getComponents().getSchemas().get(name);
                    if (schema == null || schema.getProperties() == null) {
                        return;
                    }
                    List<String> required =
                            Arrays.stream(type.getRecordComponents())
                                    .filter(
                                            c ->
                                                    !c.getAnnotatedType()
                                                            .isAnnotationPresent(Nullable.class))
                                    .map(RecordComponent::getName)
                                    .filter(schema.getProperties()::containsKey)
                                    .toList();
                    schema.setRequired(required.isEmpty() ? null : required);
                });
    }

    /** Collects {@code type} and every record reachable through its components, by schema name. */
    private static void collectRecords(Type type, Map<String, Class<?>> into) {
        if (type instanceof ParameterizedType parameterized) {
            Arrays.stream(parameterized.getActualTypeArguments())
                    .forEach(argument -> collectRecords(argument, into));
        } else if (type instanceof Class<?> raw
                && raw.isRecord()
                && into.putIfAbsent(raw.getSimpleName(), raw) == null) {
            for (RecordComponent component : raw.getRecordComponents()) {
                collectRecords(component.getGenericType(), into);
            }
        }
    }

    /** Turns {@code Object}-typed properties (rendered as an empty object) into "any". */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void loosenObjects(Schema<?> schema) {
        Map<String, Schema> properties = (Map) schema.getProperties();
        if (properties == null) {
            return;
        }
        properties.replaceAll((name, property) -> isOpaqueObject(property) ? anyValue() : property);
        properties.values().stream()
                .filter(
                        p ->
                                p.getAdditionalProperties() instanceof Schema<?> values
                                        && isOpaqueObject(values))
                .forEach(p -> p.setAdditionalProperties(anyValue()));
    }

    private static boolean isOpaqueObject(Schema<?> schema) {
        boolean object =
                "object".equals(schema.getType())
                        || (schema.getTypes() != null
                                && schema.getTypes().equals(Set.of("object")));
        return object
                && schema.get$ref() == null
                && schema.getProperties() == null
                && schema.getAdditionalProperties() == null;
    }

    private static Schema<?> anyValue() {
        return new Schema<>();
    }
}
