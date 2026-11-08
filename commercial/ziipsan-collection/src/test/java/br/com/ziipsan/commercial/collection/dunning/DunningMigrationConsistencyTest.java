package br.com.ziipsan.commercial.collection.dunning;

import br.com.ziipsan.commercial.collection.dunning.installment.InstallmentEnums;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas do SQL PENDENTE (sem numero de versao — ver o cabecalho do
 * arquivo): CHECKs = enums do codigo, tabelas registradas para RLS,
 * chaves de parametro do codigo presentes nas definicoes e o minimo
 * existencial como padrao da DEFINICAO com a fonte do Decreto.
 */
class DunningMigrationConsistencyTest {

    private static final String PATH = "/db/pendente/PENDENTE__dunning_installments_campaigns_prescription.sql";
    private static final String SQL = readResource(PATH);

    private static String readResource(String path) {
        try (InputStream in = DunningMigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    private static Set<String> checkLiterals(String sql, String marker) {
        int at = sql.indexOf(marker);
        assertThat(at).as("marcador %s", marker).isGreaterThanOrEqualTo(0);
        int inAt = sql.indexOf("IN", at);
        int open = sql.indexOf('(', inAt);
        int depth = 0;
        int close = -1;
        for (int i = open; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                close = i;
                break;
            }
        }
        Matcher m = Pattern.compile("'([A-Z0-9_]+)'").matcher(sql.substring(open, close));
        Set<String> literals = new java.util.HashSet<>();
        while (m.find()) {
            literals.add(m.group(1));
        }
        return literals;
    }

    @Test
    @DisplayName("CHECKs do acordo = enums de status, metodo de juros e origem")
    void agreementChecksMirrorEnums() {
        assertThat(checkLiterals(SQL, "status VARCHAR"))
                .isEqualTo(enumNames(InstallmentEnums.Status.class));
        assertThat(checkLiterals(SQL, "interest_method VARCHAR"))
                .isEqualTo(enumNames(InstallmentEnums.InterestMethod.class));
        assertThat(checkLiterals(SQL, "source VARCHAR"))
                .isEqualTo(enumNames(InstallmentEnums.Source.class));
    }

    @Test
    @DisplayName("CHECKs da prescricao = enums de natureza e pretensao; aprovacao dupla da campanha no banco")
    void prescriptionAndCampaignChecks() {
        assertThat(checkLiterals(SQL, "provider_nature VARCHAR"))
                .isEqualTo(enumNames(Prescription.ProviderNature.class));
        assertThat(checkLiterals(SQL, "claim_type VARCHAR"))
                .isEqualTo(enumNames(Prescription.ClaimType.class));
        assertThat(SQL).contains("proposed_by <> approved_by");
    }

    @Test
    @DisplayName("todas as tabelas registradas para RLS e DAD-21 presente (protocol_id, service_order_id, legacy_reference)")
    void tablesRegisteredAndDad21Columns() {
        for (String table : new String[]{"installment_agreements", "installment_agreement_bills",
                "installment_schedule", "regularization_campaigns", "campaign_adherences",
                "repactuation_cases", "prescription_rules", "payment_promises", "dunning_actions"}) {
            assertThat(SQL).as("register_tenant_table de %s", table)
                    .contains("register_tenant_table('" + table + "')");
        }
        assertThat(SQL).contains("protocol_id UUID NOT NULL")
                .contains("service_order_id UUID NOT NULL")
                .contains("policy_version_id UUID NOT NULL")
                .contains("legacy_reference TEXT")
                .contains("replaces_agreement_id UUID");
    }

    @Test
    @DisplayName("todas as chaves de parametro do codigo estao nas definicoes do SQL")
    void parameterKeysDeclared() {
        for (String key : new String[]{
                DunningParameters.MIN_DOWN_PAYMENT_PCT, DunningParameters.MIN_DOWN_PAYMENT,
                DunningParameters.ENTRY_DUE_DAYS, DunningParameters.MAX_INSTALLMENTS,
                DunningParameters.MIN_INSTALLMENT, DunningParameters.MAX_DISCOUNT_PCT,
                DunningParameters.PRINCIPAL_DISCOUNT_LIMIT_PCT, DunningParameters.EXISTENTIAL_MINIMUM,
                DunningParameters.REPACTUATION_EFFECTS, DunningParameters.RULER,
                DunningParameters.FEDERAL_ASSIGNMENT_ENABLED, DunningParameters.COLLECTION_SCORING_ENABLED,
                DunningParameters.INCOME_BASED}) {
            assertThat(SQL).as("definicao de %s", key).contains("'" + key + "'");
        }
    }

    @Test
    @DisplayName("minimo existencial: padrao da definicao com a fonte (Decreto 11.567/2023); interruptores nascem desligados")
    void existentialMinimumAndSwitchesInDefinitions() {
        assertThat(SQL).contains("'dun.existential_minimum', 'DECIMAL', 'c', '600.00'")
                .contains("Decreto 11.567/2023")
                .contains("'dun.federal_assignment_enabled', 'BOOLEAN', 'd', 'false'")
                .contains("'ai.collection_scoring_enabled', 'BOOLEAN', 'd', 'false'");
        assertThat(SQL).contains("period_years INT CHECK (period_years IS NULL OR period_years >= 1)")
                .contains("Decreto 20.910/1932");
    }

    @Test
    @DisplayName("D10/D1/D5: FK da adesao para o acordo, Pix nao aceita '', signature_level com dominio, refs dos documentos e permissao registrada")
    void correctionCoherences() {
        assertThat(SQL).contains("fk_campaign_adherences_agreement")
                .contains("REFERENCES installment_agreements (tenant_id, id)")
                .contains("pix_link <> ''")
                .contains("signature_level VARCHAR(10) CHECK (signature_level IN ('SIMPLE', 'ADVANCED', 'QUALIFIED'))")
                .contains("statement_document_ref TEXT")
                .contains("term_document_ref TEXT")
                .contains("down_payment_waiver_by UUID")
                .contains("down_payment_waiver_reference TEXT")
                .contains("down_payment_waived_minimums TEXT");
        // D7: a lacuna do art. 206 esta DECLARADA na semente, sem prazo
        assertThat(SQL).contains("ARTICLE_206_UNSPECIFIED")
                .contains("Codigo Civil, art. 206");
    }
}
