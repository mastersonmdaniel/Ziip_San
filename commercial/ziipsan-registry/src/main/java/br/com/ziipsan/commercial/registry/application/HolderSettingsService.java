package br.com.ziipsan.commercial.registry.application;

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
 * Parâmetros do tenant das situações do titular (P1.41, V3_8).
 *
 * <p>Mesma fonte de transição que {@link RegistrySettingsService}: os
 * valores vivem em {@code public.tenant_settings} (chave/valor JSONB) até a
 * tela do tenant (P1.6) operar {@code parameter_versions}; linha marcada
 * como sensível nunca é lida aqui. Sem valor definido, vale o padrão de
 * fábrica do plano — o comportamento mais protetivo onde o plano o fixa.
 * As definições (classe b/d, domínio) estão no catálogo global, registradas
 * pela V3_8.
 */
@Service
@Transactional(readOnly = true)
public class HolderSettingsService {

    public static final String DECEASED_HOLDER_POLICY = "registry.deceased_holder_policy";
    public static final String VACANT_UNIT_HOLDER = "registry.vacant_unit_holder";
    public static final String REPRESENTATION_PROOF = "registry.representation_proof";
    public static final String SOCIAL_NAME_DISPLAY = "registry.social_name_display";
    public static final String VALIDATE_DOCUMENT_ONLINE = "registry.validate_document_online";
    public static final String CPF_VALIDATION_PROVIDER = "reg.cpf_validation_provider";
    public static final String DECEASED_CHECK = "reg.deceased_check";
    public static final String DELEGATED_ACCESS = "portal.delegated_access";
    public static final String HOLDER_NAME_DISPLAY = "bill.holder_name_display";

    /** Padrões de fábrica (plano v3.0 §2.2 e linha P1.41). */
    public static final class Factory {
        public static final String DECEASED_HOLDER_POLICY = "CONTINUE_SERVICE_SUSPEND_CHARGES";
        public static final String VACANT_UNIT_HOLDER = "NO_HOLDER_BILL_HELD";
        public static final String REPRESENTATION_PROOF = "REQUIRED";
        public static final String SOCIAL_NAME_DISPLAY = "SOCIAL_EVERYWHERE_EXCEPT_FISCAL";
        public static final String VALIDATE_DOCUMENT_ONLINE = "NONE";
        public static final String DECEASED_CHECK = "DISABLED";
        public static final boolean DELEGATED_ACCESS = false;
        public static final String HOLDER_NAME_DISPLAY = "USER_HOLDER_ONLY";

        private Factory() {
        }
    }

    /** Valores do parâmetro {@code registry.vacant_unit_holder} (plano). */
    public static final class VacantHolder {
        public static final String OWNER_AS_PROVISIONAL = "OWNER_AS_PROVISIONAL_HOLDER";
        public static final String LAST_USER_UNTIL_NOTICE = "LAST_USER_UNTIL_NOTICE";
        public static final String NO_HOLDER_BILL_HELD = "NO_HOLDER_BILL_HELD";

        private VacantHolder() {
        }
    }

    /** Valores do parâmetro {@code registry.validate_document_online} (plano). */
    public static final class ValidateOnline {
        public static final String NONE = "NONE";
        public static final String STATUS_ONLY = "STATUS_ONLY";
        public static final String STATUS_AND_BIOMETRICS = "STATUS_AND_BIOMETRICS";

        private ValidateOnline() {
        }
    }

    /** Valores do parâmetro {@code registry.social_name_display}. */
    public static final class SocialNameDisplay {
        public static final String SOCIAL_EVERYWHERE_EXCEPT_FISCAL = "SOCIAL_EVERYWHERE_EXCEPT_FISCAL";
        public static final String CIVIL_NAME_ONLY = "CIVIL_NAME_ONLY";

        private SocialNameDisplay() {
        }
    }

    /** Valores do parâmetro {@code bill.holder_name_display} (decisão do dono 6). */
    public static final class HolderNameDisplay {
        public static final String USER_HOLDER_ONLY = "USER_HOLDER_ONLY";
        public static final String USER_HOLDER_AND_OWNER = "USER_HOLDER_AND_OWNER";

        private HolderNameDisplay() {
        }
    }

    public record Settings(String deceasedHolderPolicy, String vacantUnitHolder, String representationProof,
                           String socialNameDisplay, String validateDocumentOnline, String cpfValidationProvider,
                           String deceasedCheck, boolean delegatedAccess, String holderNameDisplay) {
    }

    @PersistenceContext
    private EntityManager em;

    public Settings current() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT setting_key, CAST(setting_value AS text) FROM public.tenant_settings "
                                + "WHERE tenant_id = :t AND NOT is_sensitive AND setting_key IN (:keys)")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("keys", List.of(DECEASED_HOLDER_POLICY, VACANT_UNIT_HOLDER, REPRESENTATION_PROOF,
                        SOCIAL_NAME_DISPLAY, VALIDATE_DOCUMENT_ONLINE, CPF_VALIDATION_PROVIDER, DECEASED_CHECK,
                        DELEGATED_ACCESS, HOLDER_NAME_DISPLAY))
                .getResultList();
        Map<String, JsonNode> values = new HashMap<>();
        for (Object[] row : rows) {
            values.put((String) row[0], Json.readTree((String) row[1]));
        }
        return new Settings(
                text(values.get(DECEASED_HOLDER_POLICY), Factory.DECEASED_HOLDER_POLICY),
                text(values.get(VACANT_UNIT_HOLDER), Factory.VACANT_UNIT_HOLDER),
                text(values.get(REPRESENTATION_PROOF), Factory.REPRESENTATION_PROOF),
                text(values.get(SOCIAL_NAME_DISPLAY), Factory.SOCIAL_NAME_DISPLAY),
                text(values.get(VALIDATE_DOCUMENT_ONLINE), Factory.VALIDATE_DOCUMENT_ONLINE),
                text(values.get(CPF_VALIDATION_PROVIDER), null),
                text(values.get(DECEASED_CHECK), Factory.DECEASED_CHECK),
                bool(values.get(DELEGATED_ACCESS), Factory.DELEGATED_ACCESS),
                text(values.get(HOLDER_NAME_DISPLAY), Factory.HOLDER_NAME_DISPLAY));
    }

    private static String text(JsonNode node, String fallback) {
        return node != null && node.isTextual() ? node.asText() : fallback;
    }

    private static boolean bool(JsonNode node, boolean fallback) {
        return node != null && node.isBoolean() ? node.booleanValue() : fallback;
    }
}
