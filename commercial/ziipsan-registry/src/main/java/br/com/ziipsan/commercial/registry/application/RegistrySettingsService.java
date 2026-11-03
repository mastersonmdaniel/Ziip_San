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
 * Parâmetros do cadastro por tenant.
 *
 * <p>P0.19: {@code registry.require_user_document} é parâmetro do tenant com vigência e
 * aceite (tabelas {@code parameter_versions} / {@code parameter_acceptances}, V1_5), e
 * não mais configuração da aplicação. Precedência para essa chave: versão aceita e
 * vigente → {@code public.tenant_settings} (transição: tenants sem versão de parâmetro
 * mantêm o valor antigo; a remoção do fallback é da tela do tenant, P1.6) → padrão
 * (documento obrigatório, o comportamento mais restritivo). As demais chaves continuam
 * em {@code public.tenant_settings} (chave/valor JSONB, com RLS por tenant); linha
 * marcada como sensível nunca é lida aqui.
 *
 * <p>Chaves em tenant_settings: {@code registry.use_block_face},
 * {@code registry.require_reading_sequence}, {@code registry.economies_soft_limit}
 * (e {@code registry.require_user_document} só como fallback de transição).
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
        Boolean parameterValue = acceptedParameterBoolean(REQUIRE_USER_DOCUMENT);
        boolean requireUserDocument = parameterValue != null
                ? parameterValue
                : bool(values.get(REQUIRE_USER_DOCUMENT), defaults.requireUserDocument());
        return new Settings(
                bool(values.get(USE_BLOCK_FACE), false),
                bool(values.get(REQUIRE_READING_SEQUENCE), false),
                requireUserDocument,
                integer(values.get(ECONOMIES_SOFT_LIMIT), defaults.economiesSoftLimit()));
    }

    /**
     * Valor aceito e vigente de um parâmetro booleano em {@code parameter_versions}
     * (tabela do schema do tenant, com RLS), ou {@code null} quando não há versão
     * aceita e vigente. Versão aceita com valor que não é booleano é erro de dado e
     * falha alto, em vez de cair silenciosamente para o fallback. A data de referência
     * é CURRENT_DATE do banco; a data local do tenant (fuso da P0.19) chega na
     * aplicação do fuso ao calendário e ao SLA (P2.1, P3.4, P2.15).
     */
    private Boolean acceptedParameterBoolean(String definitionKey) {
        @SuppressWarnings("unchecked")
        List<String> rows = em.createNativeQuery(
                        "SELECT CAST(v.value AS text) FROM parameter_versions v "
                                + "WHERE v.tenant_id = :t AND v.definition_key = :key "
                                + "AND v.status = 'APPROVED' "
                                + "AND v.valid_from <= CURRENT_DATE "
                                + "AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE) "
                                + "AND EXISTS (SELECT 1 FROM parameter_acceptances a "
                                + "WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id "
                                + "AND a.content_hash = v.content_hash) "
                                + "ORDER BY v.valid_from DESC LIMIT 1")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("key", definitionKey)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        JsonNode node = Json.readTree(rows.get(0));
        if (node == null || !node.isBoolean()) {
            throw new IllegalStateException(
                    "Parâmetro aceito '" + definitionKey + "' com valor não booleano: " + rows.get(0));
        }
        return node.booleanValue();
    }

    private static boolean bool(JsonNode node, boolean fallback) {
        return node != null && node.isBoolean() ? node.booleanValue() : fallback;
    }

    private static int integer(JsonNode node, int fallback) {
        return node != null && node.isIntegralNumber() ? node.intValue() : fallback;
    }
}
