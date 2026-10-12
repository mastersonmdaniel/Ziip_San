package br.com.sigsan.core.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serialização para colunas {@code jsonb}.
 *
 * <p>Existe por causa de um erro que se repetia em vários módulos: gravar {@code map.toString()} ou
 * {@code list.toString()} numa coluna {@code jsonb}. {@code Map.of("a", "b").toString()} é
 * {@code {a=b}} e {@code List.of("x", "y").toString()} é {@code [x, y]}: nenhum dos dois é JSON, e o
 * PostgreSQL recusa o INSERT ("invalid input syntax for type json"). O erro só aparece com dado
 * real: os testes sempre passavam {@code null}, que caía no default {@code "{}"}.
 *
 * <p>Esta classe lança exceção se não conseguir serializar. A alternativa comum, devolver
 * {@code "{}"} no {@code catch}, apagaria o dado em silêncio.
 */
public final class Json {

    // Datas em ISO-8601 (texto legível e estável), e não como número de segundos desde 1970.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Json() {
    }

    /** JSON do valor. {@code null} vira a palavra JSON {@code null}. */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o valor para JSON: "
                    + value.getClass().getName(), e);
        }
    }

    /** JSON do valor, ou {@code defaultJson} (por exemplo {@code "{}"} ou {@code "[]"}) se ele for nulo. */
    public static String orDefault(Object value, String defaultJson) {
        return value == null ? defaultJson : write(value);
    }
}
