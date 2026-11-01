package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.rules.UnitConsistency;
import br.com.ziipsan.core.json.Json;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parâmetros do cadastro por tenant, lidos de {@code public.tenant_settings} (chave/valor JSONB, com
 * RLS por tenant). Chave ausente vale o padrão abaixo, que é o comportamento mais restritivo onde há
 * dúvida (documento do usuário obrigatório).
 *
 * <p>Chaves: {@code registry.use_block_face}, {@code registry.require_reading_sequence},
 * {@code registry.require_user_document}, {@code registry.economies_soft_limit}. Linha marcada como
 * sensível nunca é lida aqui.
 */
@Service
@Transactional(readOnly = true)
public class RegistrySettingsService {

    public static final String USE_BLOCK_FACE = "registry.use_block_face";
    public static final String REQUIRE_READING_SEQUENCE = "registry.require_reading_sequence";
    public static final String REQUIRE_USER_DOCUMENT = "registry.require_user_document";
    public static final String ECONOMIES_SOFT_LIMIT = "registry.economies_soft_limit";

    /** O que o frontend lê ({@code RegistrySettings} em types.ts), mais os dois parâmetros das regras. */
    public record Settings(boolean useBlockFace, boolean requireReadingSequence, boolean requireUserDocument,
                           int economiesSoftLimit) {

        public UnitConsistency.Policy policy() {
            return new UnitConsistency.Policy(requireUserDocument, economiesSoftLimit);
        }
    }

    @PersistenceContext
    private EntityManager em;

    public Settings current() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT setting_key, CAST(setting_value AS text) FROM public.tenant_settings "
                                + "WHERE tenant_id = :t AND NOT is_sensitive AND setting_key IN (:keys)")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("keys", List.of(USE_BLOCK_FACE, REQUIRE_READING_SEQUENCE, REQUIRE_USER_DOCUMENT,
                        ECONOMIES_SOFT_LIMIT))
                .getResultList();
        Map<String, JsonNode> values = new HashMap<>();
        for (Object[] row : rows) {
            values.put((String) row[0], Json.readTree((String) row[1]));
        }
        UnitConsistency.Policy defaults = UnitConsistency.Policy.defaults();
        return new Settings(
                bool(values.get(USE_BLOCK_FACE), false),
                bool(values.get(REQUIRE_READING_SEQUENCE), false),
                bool(values.get(REQUIRE_USER_DOCUMENT), defaults.requireUserDocument()),
                integer(values.get(ECONOMIES_SOFT_LIMIT), defaults.economiesSoftLimit()));
    }

    private static boolean bool(JsonNode node, boolean fallback) {
        return node != null && node.isBoolean() ? node.booleanValue() : fallback;
    }

    private static int integer(JsonNode node, int fallback) {
        return node != null && node.isIntegralNumber() ? node.intValue() : fallback;
    }
}
