package pl.pw.zai.pomiary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.admin.username=admin", "app.admin.password=" + ApiIntegrationTest.PASSWORD})
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {

    static final String PASSWORD = "Test-Haslo-123";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    // ---------- pomocnicze

    String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    JsonNode body(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString());
    }

    String login() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return body(r).get("accessToken").asText();
    }

    long createSeries(String token, double min, double max) throws Exception {
        MvcResult r = mvc.perform(post("/api/series").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Test-" + UUID.randomUUID(), "minValue", min, "maxValue", max, "color", "#1f77b4"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        return body(r).get("id").asLong();
    }

    JsonNode registerSensor(String token, long seriesId) throws Exception {
        MvcResult r = mvc.perform(post("/api/sensors").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Czujnik testowy", "seriesId", seriesId))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        return body(r);
    }

    MvcResult sendValue(String apiKey, double value, String timestamp) throws Exception {
        Map<String, Object> m = timestamp == null ? Map.of("value", value) : Map.of("value", value, "timestamp", timestamp);
        return mvc.perform(post("/api/measurements").header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON).content(json(m))).andReturn();
    }

    // ---------- testy

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void wrongPasswordReturns401ProblemDetails() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "zle-haslo"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminOperationsRequireToken() throws Exception {
        mvc.perform(post("/api/series").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "minValue", 0, "maxValue", 1, "color", "#000000"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sensors")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sensors").header("Authorization", "Bearer sfalszowany"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sensorLifecycle() throws Exception {
        String token = login();
        long seriesId = createSeries(token, 0, 10);
        JsonNode sensor = registerSensor(token, seriesId);
        String key = sensor.get("apiKey").asText();
        assertThat(key).hasSizeGreaterThanOrEqualTo(32);

        MvcResult ok = sendValue(key, 5.5, "2026-01-10T10:00:00Z");
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(ok).get("seriesId").asLong()).isEqualTo(seriesId);

        assertThat(sendValue(key, 11, null).getResponse().getStatus()).isEqualTo(422);
        assertThat(sendValue(key, 1, "2999-01-01T00:00:00Z").getResponse().getStatus()).isEqualTo(422);

        mvc.perform(post("/api/measurements").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/sensors/" + sensor.get("id").asLong()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(key))));

        mvc.perform(delete("/api/sensors/" + sensor.get("id").asLong()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(sendValue(key, 5, null).getResponse().getStatus()).isEqualTo(401);

        mvc.perform(get("/api/measurements").param("series", String.valueOf(seriesId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void measurementsAreFilteredAndSorted() throws Exception {
        String token = login();
        long seriesId = createSeries(token, -50, 50);
        String key = registerSensor(token, seriesId).get("apiKey").asText();
        sendValue(key, 3, "2026-01-10T12:00:00Z");
        sendValue(key, 1, "2026-01-10T10:00:00Z");
        sendValue(key, 2, "2026-01-10T11:00:00Z");

        mvc.perform(get("/api/measurements").param("series", String.valueOf(seriesId))
                        .param("from", "2026-01-10T10:00:00Z").param("to", "2026-01-10T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].value").value(1.0))
                .andExpect(jsonPath("$[1].value").value(2.0));

        mvc.perform(get("/api/measurements").param("series", String.valueOf(seriesId)).param("sort", "-timestamp"))
                .andExpect(jsonPath("$[0].value").value(3.0));

        mvc.perform(get("/api/measurements").param("from", "nie-data")).andExpect(status().isBadRequest());
    }

    @Test
    void rangeChangeExcludingExistingResultsIsRejected() throws Exception {
        String token = login();
        long seriesId = createSeries(token, 0, 100);
        String key = registerSensor(token, seriesId).get("apiKey").asText();
        sendValue(key, 20, "2026-01-10T10:00:00Z");

        mvc.perform(put("/api/series/" + seriesId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Zmieniona", "minValue", 50, "maxValue", 60, "color", "#ff0000"))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/series").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Zla", "minValue", 10, "maxValue", 10, "color", "#ff0000"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deletingSeriesRemovesMeasurementsAndSensors() throws Exception {
        String token = login();
        long seriesId = createSeries(token, 0, 10);
        JsonNode sensor = registerSensor(token, seriesId);
        long measurementId = body(sendValue(sensor.get("apiKey").asText(), 1, "2026-01-10T10:00:00Z")).get("id").asLong();

        mvc.perform(delete("/api/series/" + seriesId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/series/" + seriesId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/measurements/" + measurementId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/sensors/" + sensor.get("id").asLong()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void measurementsCannotBeModified() throws Exception {
        String token = login();
        long seriesId = createSeries(token, 0, 10);
        long id = body(sendValue(registerSensor(token, seriesId).get("apiKey").asText(), 7, "2026-01-10T10:00:00Z"))
                .get("id").asLong();
        int put = mvc.perform(put("/api/measurements/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":8}")).andReturn().getResponse().getStatus();
        int del = mvc.perform(delete("/api/measurements/" + id).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        assertThat(put).isIn(404, 405);
        assertThat(del).isIn(404, 405);
        mvc.perform(get("/api/measurements/" + id)).andExpect(jsonPath("$.value").value(7.0));
    }

    @Test
    void logoutRevokesToken() throws Exception {
        String token = login();
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/sensors").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contentNegotiation() throws Exception {
        String token = login();
        String key = registerSensor(token, createSeries(token, 0, 10)).get("apiKey").asText();
        mvc.perform(get("/api/series").accept(MediaType.APPLICATION_XML)).andExpect(status().isNotAcceptable());
        mvc.perform(post("/api/measurements").header("X-API-Key", key)
                        .contentType(MediaType.TEXT_PLAIN).content("value=1"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/series"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"));
    }
}
