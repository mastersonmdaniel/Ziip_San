package br.com.sigsan.core.json;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

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

    @Test
    @DisplayName("readTree: nulo e em branco viram nó ausente, sem entradas")
    void readTreeEmpty() {
        for (String vazio : new String[]{null, "", "   "}) {
            JsonNode node = Json.readTree(vazio);
            assertThat(node.isMissingNode()).as("entrada [%s]", vazio).isTrue();
            assertThat(node.size()).isZero();
        }
    }

    @Test
    @DisplayName("readTree: lê array e objeto, com aspas e barra invertida preservadas")
    void readTreeReads() {
        JsonNode array = Json.readTree("[\"a\",\"b, c\"]");
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).as("a vírgula dentro da string não separa itens").isEqualTo(2);

        JsonNode obj = Json.readTree(Json.write(Map.of("t", "aspas \" e barra \\")));
        assertThat(obj.get("t").asString()).isEqualTo("aspas \" e barra \\");
    }

    @Test
    @DisplayName("readTree: JSON inválido lança IllegalStateException; toString() antigo e CSV não passam")
    void readTreeInvalid() {
        for (String invalido : new String[]{"[a, b]", "{a=b}", "A.1,B.2", "1, 2", "{\"a\":"}) {
            assertThatThrownBy(() -> Json.readTree(invalido))
                    .as("entrada [%s]", invalido)
                    .isInstanceOf(IllegalStateException.class)
                    // a mensagem não repete o conteúdo (pode ser dado de titular)
                    .hasMessageNotContaining(invalido);
        }
    }

    @Test
    @DisplayName("readMap: nulo, em branco e o literal null viram mapa vazio e mutável")
    void readMapEmpty() {
        for (String vazio : new String[]{null, "", " ", "null"}) {
            Map<String, Object> m = Json.readMap(vazio);
            assertThat(m).as("entrada [%s]", vazio).isEmpty();
            m.put("k", "v"); // mutável: quem lê costuma acrescentar uma chave e regravar
        }
    }

    @Test
    @DisplayName("readMap: preserva chaves, ordem e tipos")
    void readMapReads() {
        Map<String, Object> m = Json.readMap("{\"b\":1,\"a\":\"x\",\"c\":{\"d\":true}}");
        assertThat(m).containsOnlyKeys("b", "a", "c");
        assertThat(m.keySet()).containsExactly("b", "a", "c");
        assertThat(m.get("b")).isEqualTo(1);
        assertThat(m.get("c")).isEqualTo(Map.of("d", true));
    }

    @Test
    @DisplayName("readMap: JSON inválido ou que não é objeto lança IllegalStateException")
    void readMapInvalid() {
        for (String invalido : new String[]{"{a=b}", "[1,2]", "42", "\"texto\"", "{\"a\":1} lixo"}) {
            assertThatThrownBy(() -> Json.readMap(invalido))
                    .as("entrada [%s]", invalido)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
