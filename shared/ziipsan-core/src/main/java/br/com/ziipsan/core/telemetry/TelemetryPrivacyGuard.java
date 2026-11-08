package br.com.ziipsan.core.telemetry;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P4.8 / PLT-42: o portão de privacidade da telemetria de produto — e o
 * TESTE OBRIGATÓRIO do aceite: "evento de telemetria com identificador de
 * pessoa ou com conteúdo é recusado".
 *
 * <p>A ingestão recebe o JSON do evento e o lê SÓ pela classe
 * {@link Json} do core. Antes de qualquer campo virar
 * {@link TelemetryEvent}, TODAS as chaves do objeto (em qualquer nível de
 * aninhamento, inclusive dentro de coleções/arrays) são conferidas contra
 * a lista de proibidas: identificador
 * de pessoa, contato, documento, sessão/gravação e conteúdo digitado.
 * Uma única chave proibida recusa o evento INTEIRO com 422 — nunca se
 * "limpa" o evento e se aceita o resto, porque aceitar parcial ensinaria
 * o emissor a mandar dado pessoal e confiar na tesoura.
 *
 * <p>A conferência é por chave normalizada (minúsculas, sem
 * {@code _}/{@code -}/{@code .}) e também por fragmento
 * ({@code person}, {@code session}, {@code content}, {@code email},
 * {@code cpf}, {@code recording}, {@code keystroke}): variações de nome
 * ({@code user_id}, {@code userId}, {@code personIdentifier}) caem na
 * mesma recusa. Chaves permitidas são lista FECHADA
 * ({@link #ALLOWED_KEYS}): chave desconhecida também recusa — a
 * telemetria agregada não precisa de atributo livre nenhum.
 */
public final class TelemetryPrivacyGuard {

    /** Chaves permitidas no evento bruto (normalizadas). Lista fechada. */
    static final Set<String> ALLOWED_KEYS = Set.of(
            "tenantid", "role", "kind",
            "featurekey", "wizardkey", "taskkey",
            "durationmillis", "occurredat");

    /** Fragmentos que denunciam pessoa, sessão ou conteúdo em qualquer nome de chave. */
    static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "person", "user", "actor", "requester", "author", "subjectid",
            "email", "cpf", "cnpj", "phone", "name",
            "session", "recording", "keystroke",
            "content", "typed", "text", "body", "message", "comment", "input", "payload");

    private TelemetryPrivacyGuard() {
    }

    static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[_\\-.]", "");
    }

    /** Verdadeiro quando a chave é proibida (fragmento) ou simplesmente não é permitida. */
    static boolean isForbiddenKey(String key) {
        String n = normalizeKey(key);
        if (ALLOWED_KEYS.contains(n)) {
            return false;
        }
        return true;
    }

    static boolean hasForbiddenFragment(String key) {
        String n = normalizeKey(key);
        return FORBIDDEN_FRAGMENTS.stream().anyMatch(n::contains);
    }

    /**
     * Recusa explícita e nomeada quando a chave carrega pessoa/sessão/
     * conteúdo — a mensagem distingue esse caso (o do teste obrigatório)
     * de uma chave apenas desconhecida.
     */
    private static void checkKeys(Map<String, Object> map, String path) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String keyPath = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
            if (hasForbiddenFragment(e.getKey())) {
                throw ApiException.Validation.of(new ApiException.FieldError(keyPath,
                        "telemetry-forbidden-field",
                        "Evento de telemetria recusado: o campo '" + keyPath
                                + "' é identificador de pessoa, sessão ou conteúdo — a telemetria de produto "
                                + "(PLT-42) é só agregada por tenant e por papel."));
            }
            if (isForbiddenKey(e.getKey())) {
                throw ApiException.Validation.of(new ApiException.FieldError(keyPath,
                        "telemetry-unknown-field",
                        "Evento de telemetria recusado: campo não previsto '" + keyPath
                                + "' (a lista de campos da telemetria agregada é fechada)."));
            }
            checkValue(e.getValue(), keyPath);
        }
    }

    /**
     * Desce em QUALQUER estrutura aninhada sob uma chave já conferida:
     * mapa, coleção (a lista/array que o {@code Json} produz) ou array,
     * em qualquer profundidade — mapa dentro de lista, lista dentro de
     * lista e lista dentro de mapa são todos varridos, e as chaves de
     * qualquer mapa encontrado passam pela mesma conferência de
     * {@link #checkKeys}. Elementos escalares (texto, número, booleano,
     * {@code null}) não têm chaves a conferir e não mudam o veredito:
     * um vetor plano de escalares sob chave permitida continua
     * passando, como antes da correção.
     */
    private static void checkValue(Object value, String path) {
        if (value instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) nested;
            checkKeys(nestedMap, path);
        } else if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                checkValue(element, path);
            }
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                checkValue(element, path);
            }
        }
    }

    /**
     * Valida o JSON bruto e devolve o evento tipado. Recusa (422) com
     * código {@code telemetry-forbidden-field} quando há identificador de
     * pessoa ou conteúdo — o caso do teste obrigatório do aceite.
     */
    public static TelemetryEvent parseAndValidate(String eventJson) {
        if (eventJson == null || eventJson.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError("event", "invalid",
                    "Evento de telemetria vazio."));
        }
        Map<String, Object> map = Json.readMap(eventJson);
        checkKeys(map, "");
        try {
            UUID tenantId = UUID.fromString(requiredString(map, "tenantId"));
            String role = requiredString(map, "role");
            TelemetryEvent.Kind kind = TelemetryEvent.Kind.valueOf(requiredString(map, "kind"));
            Instant occurredAt = Instant.parse(requiredString(map, "occurredAt"));
            Long duration = map.get("durationMillis") == null ? null
                    : ((Number) map.get("durationMillis")).longValue();
            return new TelemetryEvent(tenantId, role, kind,
                    optionalString(map, "featureKey"), optionalString(map, "wizardKey"),
                    optionalString(map, "taskKey"), duration, occurredAt);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.Validation.of(new ApiException.FieldError("event", "invalid",
                    "Evento de telemetria malformado: " + e.getClass().getSimpleName() + "."));
        }
    }

    private static String requiredString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null || v.toString().isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(key, "invalid",
                    "Campo obrigatório do evento de telemetria: " + key + "."));
        }
        return v.toString();
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}
