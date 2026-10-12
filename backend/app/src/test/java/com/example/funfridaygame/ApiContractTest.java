package com.example.funfridaygame;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks the REST API contract and exports the OpenAPI document to {@code docs/openapi.json}; the
 * frontend generates its TypeScript types from it ({@code npm run generate:api}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiContractTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private JsonMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void exportsTheOpenApiDocumentWithProtocolSchemas() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode doc = json.readTree(response.body());
        assertThat(doc.at("/paths").has("/api/games")).isTrue();
        assertThat(doc.at("/components/schemas").has("RoomSnapshot")).isTrue();
        assertThat(doc.at("/components/schemas").has("Reply")).isTrue();

        String docsDir = System.getProperty("ff.docsDir");
        if (docsDir != null) {
            Path file = Path.of(docsDir, "openapi.json");
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file, json.writerWithDefaultPrettyPrinter().writeValueAsString(doc) + "\n");
        }
    }

    @Test
    void servesTheCatalogHealthAndLegacyRedirect() throws Exception {
        JsonNode games = json.readTree(get("/api/games").body());
        assertThat(games.isArray()).isTrue();
        assertThat(games.get(0).get("settings").isArray()).isTrue();

        assertThat(get("/actuator/health").body()).contains("UP");
        assertThat(get("/api/rooms/NOPE1").statusCode()).isEqualTo(404);
        assertThat(get("/ui/").statusCode()).isEqualTo(302);
        assertThat(get("/api/leaderboard").statusCode()).isEqualTo(200);
    }
}
