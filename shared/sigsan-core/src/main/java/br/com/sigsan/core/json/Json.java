package br.com.sigsan.core.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serialização e leitura de colunas {@code jsonb}.
 *
 * <p>Existe por causa de um erro que se repetia em vários módulos: gravar {@code map.toString()} ou
 * {@code list.toString()} numa coluna {@code jsonb}. {@code Map.of("a", "b").toString()} é
 * {@code {a=b}} e {@code List.of("x", "y").toString()} é {@code [x, y]}: nenhum dos dois é JSON, e o
 * PostgreSQL recusa o INSERT ("invalid input syntax for type json"). O erro só aparece com dado
 * real: os testes sempre passavam {@code null}, que caía no default {@code "{}"}.
 *
 * <p>Esta classe lança exceção se não conseguir serializar ou ler. A alternativa comum, devolver
 * {@code "{}"} (ou um mapa vazio) no {@code catch}, apagaria o dado em silêncio.
 *
 * <p>Usa Jackson 3 ({@code tools.jackson}), o padrão do Spring Boot 4: datas {@code java.time} são
 * suportadas sem módulo extra e saem em ISO-8601 por padrão. Com Jackson 2 isso dependia do módulo
 * {@code jackson-datatype-jsr310}, que o Boot 3 trazia por transitividade e o Boot 4 não traz mais.
 */
public final class Json {

    // FAIL_ON_EMPTY_BEANS ligado explicitamente: o Jackson 3 o desliga por padrão, e um objeto sem
    // propriedades serializáveis viraria "{}" em silêncio, o mesmo apagamento que esta classe evita.
    // FAIL_ON_TRAILING_TOKENS também explícito: sem ele, "1, 2" seria lido como 1 e o resto descartado,
    // e um CSV antigo ("A.1,B.2") passaria por JSON válido.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private Json() {
    }

    /** JSON do valor. {@code null} vira a palavra JSON {@code null}. */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Não foi possível serializar o valor para JSON: "
                    + value.getClass().getName(), e);
        }
    }

    /** JSON do valor, ou {@code defaultJson} (por exemplo {@code "{}"} ou {@code "[]"}) se ele for nulo. */
    public static String orDefault(Object value, String defaultJson) {
        return value == null ? defaultJson : write(value);
    }

    /**
     * Árvore do JSON. Nulo ou em branco devolve {@link MissingNode} ("nada declarado"), que responde
     * {@code size() == 0} e {@code isMissingNode()}. JSON inválido lança {@link IllegalStateException}:
     * quem lê uma coluna {@code jsonb} precisa saber que o conteúdo não é o que parece.
     */
    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw invalid(json, e);
        }
    }

    /**
     * Objeto JSON como mapa mutável, na ordem das chaves. Nulo, em branco ou o literal {@code null}
     * devolvem mapa vazio. JSON inválido ou que não seja objeto (array, número) lança
     * {@link IllegalStateException}: tratar como vazio apagaria o conteúdo na próxima gravação.
     */
    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            LinkedHashMap<String, Object> map = MAPPER.readValue(json, MAP_TYPE);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (JacksonException e) {
            throw invalid(json, e);
        }
    }

    // A mensagem não repete o conteúdo (nem a do Jackson, que cita o token lido): colunas jsonb guardam
    // metadados de titulares (LGPD) e a exceção acaba em log. O tamanho e o tipo do erro bastam para
    // distinguir um campo truncado de um formato errado; o detalhe fica na causa.
    private static IllegalStateException invalid(String json, JacksonException e) {
        return new IllegalStateException("Conteúdo não é JSON válido no formato esperado ("
                + json.length() + " caracteres, " + e.getClass().getSimpleName() + ")", e);
    }
}
