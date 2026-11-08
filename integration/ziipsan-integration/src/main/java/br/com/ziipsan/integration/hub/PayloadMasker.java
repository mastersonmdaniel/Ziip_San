package br.com.ziipsan.integration.hub;

import br.com.ziipsan.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * P3.37: log com carga MASCARADA. Nenhuma carga de integração vai para
 * log/trilha com segredo ou dado pessoal legível: valores sob chaves
 * sensíveis viram {@code "***"}, em qualquer profundidade, e cadeias com
 * portador ({@code Bearer ...}) são mascaradas onde aparecerem.
 *
 * <p>A leitura e a escrita passam SOMENTE pela classe {@link Json} do
 * core (regra do projeto). Carga que não é um OBJETO JSON (array, texto,
 * número, ou JSON inválido) não é registrada nem parcialmente: o log
 * recebe o marcador de omissão com o tamanho — mascarar "mais ou menos"
 * um formato desconhecido vazaria justamente o que não se conhece.
 *
 * <p>Chaves sensíveis: o conjunto exato abaixo, ou qualquer chave que
 * CONTENHA um dos fragmentos (segredo, token, senha, documento...).
 * {@code credential_ref} é referência, não segredo — mas, se aparecer
 * dentro de uma carga, também é mascarada por conter "credential":
 * o log não precisa dela, a correlação é pelo {@code correlation_id}.
 */
public final class PayloadMasker {

    public static final String MASK = "***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "senha", "secret", "client_secret", "token", "access_token",
            "refresh_token", "authorization", "api_key", "apikey", "private_key",
            "credential", "credential_ref", "cvv", "card_number", "cpf", "cnpj",
            "document", "payer_document", "receiver_document");

    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "secret", "token", "password", "senha", "credential", "document", "cpf", "cnpj", "card", "cvv");

    private PayloadMasker() {
    }

    /**
     * Carga mascarada, pronta para log. Objeto JSON -> objeto com os
     * valores sensíveis mascarados (ordem das chaves preservada).
     * Qualquer outra coisa -> marcador de omissão com o tamanho.
     */
    public static String maskForLog(String payloadJson) {
        if (payloadJson == null) {
            return "{\"_payload\":\"[omitida: carga nula]\"}";
        }
        Map<String, Object> map;
        try {
            map = Json.readMap(payloadJson);
        } catch (RuntimeException notAnObject) {
            return omitted(payloadJson);
        }
        // Json.readMap devolve mapa vazio para nulo/branco/"null": só a
        // carga que é de fato um objeto vazio ("{}", com qualquer
        // espaçamento) loga como "{}"; o literal "null" e qualquer
        // outra forma vazia de ler são omitidos, não disfarçados de {}.
        if (map.isEmpty() && !"{}".equals(payloadJson.replaceAll("\\s+", ""))) {
            return omitted(payloadJson);
        }
        return Json.write(maskValue(map));
    }

    private static String omitted(String payloadJson) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("_payload", "[omitida do log: carga não é objeto JSON, " + payloadJson.length() + " caracteres]");
        return Json.write(marker);
    }

    @SuppressWarnings("unchecked")
    private static Object maskValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                out.put(key, isSensitiveKey(key) ? MASK : maskValue(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(maskValue(item));
            }
            return out;
        }
        if (value instanceof String text && containsBearer(text)) {
            return MASK;
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        for (String fragment : SENSITIVE_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBearer(String text) {
        return text.toLowerCase(Locale.ROOT).contains("bearer ");
    }
}
