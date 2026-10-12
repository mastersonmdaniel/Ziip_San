package br.com.sigsan.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTest {

    @Test
    @DisplayName("Map vira objeto JSON; toString() daria {a=b}, que não é JSON")
    void mapIsJson() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", "b");
        m.put("n", 2);
        assertThat(Json.write(m)).isEqualTo("{\"a\":\"b\",\"n\":2}");
        assertThat(m.toString()).as("o que o código antigo gravava").isEqualTo("{a=b, n=2}");
        new ObjectMapper().readTree(Json.write(m)); // não lança
    }

    @Test
    @DisplayName("List vira array JSON; toString() daria [x, y]")
    void listIsJson() {
        assertThat(Json.write(List.of("x", "y"))).isEqualTo("[\"x\",\"y\"]");
        assertThat(List.of("x", "y").toString()).isEqualTo("[x, y]");
    }

    @Test
    @DisplayName("aspas e acentos são escapados corretamente")
    void escaping() throws Exception {
        String json = Json.write(Map.of("texto", "ele disse \"olá\" ação"));
        assertThat(new ObjectMapper().readTree(json).get("texto").asText()).isEqualTo("ele disse \"olá\" ação");
    }

    @Test
    @DisplayName("datas com fuso são serializadas (módulo java.time registrado)")
    void javaTime() {
        OffsetDateTime t = OffsetDateTime.of(2026, 10, 12, 9, 0, 0, 0, ZoneOffset.UTC);
        // ISO-8601, não um número (timestamp)
        assertThat(Json.write(Map.of("em", t))).isEqualTo("{\"em\":\"2026-10-12T09:00:00Z\"}");
    }

    @Test
    @DisplayName("orDefault: nulo devolve o padrão; valor devolve o JSON")
    void orDefault() {
        assertThat(Json.orDefault(null, "{}")).isEqualTo("{}");
        assertThat(Json.orDefault(null, "[]")).isEqualTo("[]");
        assertThat(Json.orDefault(Map.of("k", 1), "{}")).isEqualTo("{\"k\":1}");
        assertThat(Json.orDefault(List.of(), "[]")).isEqualTo("[]");
    }

    @Test
    @DisplayName("o que não se serializa lança exceção em vez de virar \"{}\"")
    void failsLoudly() {
        // Um Object puro não tem propriedades: o Jackson recusa (FAIL_ON_EMPTY_BEANS)
        assertThatThrownBy(() -> Json.write(Map.of("x", new Object()))).isInstanceOf(IllegalStateException.class);
    }
}
