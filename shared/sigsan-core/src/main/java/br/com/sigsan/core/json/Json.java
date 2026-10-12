package br.com.sigsan.core.json;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

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
 *
 * <p>Usa Jackson 3 ({@code tools.jackson}), o padrão do Spring Boot 4: datas {@code java.time} são
 * suportadas sem módulo extra e saem em ISO-8601 por padrão. Com Jackson 2 isso dependia do módulo
 * {@code jackson-datatype-jsr310}, que o Boot 3 trazia por transitividade e o Boot 4 não traz mais.
 */
public final class Json {

    // FAIL_ON_EMPTY_BEANS ligado explicitamente: o Jackson 3 o desliga por padrão, e um objeto sem
    // propriedades serializáveis viraria "{}" em silêncio, o mesmo apagamento que esta classe evita.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

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
}
