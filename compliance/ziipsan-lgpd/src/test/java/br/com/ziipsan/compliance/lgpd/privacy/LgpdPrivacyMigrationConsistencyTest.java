package br.com.ziipsan.compliance.lgpd.privacy;

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
 * Guardas da V23_1 sem banco, no padrão dos MigrationConsistencyTest do projeto: os
 * CHECKs da migration listam EXATAMENTE os enums do código, as tabelas novas são
 * registradas para RLS/auditoria, a referência do prazo é o art. 19, II e a pré-carga
 * de fábrica não semeia linha inventada.
 */
class LgpdPrivacyMigrationConsistencyTest {

    private static final String SQL =
            readResource("/db/migration/tenant/V23_1__lgpd_privacy.sql");
    private static final String FACTORY_SQL =
            readResource("/db/migration/tenant/R__lgpd_factory_processing_records.sql");

    private static String readResource(String path) {
        try (InputStream in = LgpdPrivacyMigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    /**
     * Literais 'XXX' da lista IN (...) que segue o marcador, como conjunto exato.
     * A lista é localizada pelo "IN (" DEPOIS do marcador (nunca pelo parêntese de um
     * VARCHAR(n) no caminho) e fechada por varredura de profundidade de parênteses.
     */
    private static Set<String> checkLiterals(String sql, String marker) {
        int at = sql.indexOf(marker);
        assertThat(at).as("marcador %s", marker).isGreaterThanOrEqualTo(0);
        int inAt = sql.indexOf("IN (", at);
        assertThat(inAt).as("IN ( depois de %s", marker).isGreaterThan(at);
        int open = inAt + 3;
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
        assertThat(close).as("fecha da lista IN de %s", marker).isGreaterThan(open);
        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(sql.substring(open, close));
        Set<String> literals = new java.util.HashSet<>();
        while (m.find()) {
            literals.add(m.group(1));
        }
        return literals;
    }

    @Test
    @DisplayName("CHECK de legal_basis da V23_1 é exatamente o enum LegalBasis (= CHECK da V2_5)")
    void legalBasisCheckMatchesEnum() {
        assertThat(checkLiterals(SQL, "legal_basis VARCHAR(30) NOT NULL CHECK (legal_basis IN"))
                .isEqualTo(enumNames(LegalBasis.class));
    }

    @Test
    @DisplayName("CHECK de origin_channel é exatamente o enum de canais da abertura")
    void originChannelCheckMatchesEnum() {
        assertThat(checkLiterals(SQL, "origin_channel IN"))
                .isEqualTo(enumNames(DsrIntake.Channel.class));
    }

    @Test
    @DisplayName("CHECK de status da política é exatamente o enum PrivacyPolicy.Status")
    void policyStatusCheckMatchesEnum() {
        assertThat(checkLiterals(SQL, "status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'"))
                .isEqualTo(enumNames(PrivacyPolicy.Status.class));
    }

    @Test
    @DisplayName("CHECK de origin do registro é exatamente o enum ProcessingRecord.Origin")
    void originCheckMatchesEnum() {
        assertThat(checkLiterals(SQL, "origin VARCHAR(10) NOT NULL DEFAULT 'TENANT' CHECK (origin IN"))
                .isEqualTo(enumNames(ProcessingRecord.Origin.class));
    }

    @Test
    @DisplayName("tabelas novas registradas para RLS/auditoria e uma só versão ativa/publicada")
    void tablesRegisteredAndUniqueness() {
        assertThat(SQL)
                .contains("register_tenant_table('lgpd_processing_records')")
                .contains("register_tenant_table('lgpd_processing_record_columns')")
                .contains("register_tenant_table('lgpd_privacy_policies')")
                .contains("uq_processing_records_one_active")
                .contains("uq_privacy_policies_one_published")
                .contains("fk_dsr_protocol")
                .contains("purpose_code VARCHAR(40)")
                // a unicidade de (tenant_id, code) em consent_purposes é ÍNDICE único
                // (V3_12), não constraint: a guarda das FKs de finalidade tem de olhar
                // pg_index, senão a FK nunca é criada (defeito achado na revisão)
                .contains("pg_index")
                .contains("indisunique");
    }

    @Test
    @DisplayName("referência do prazo corrigida para o art. 19, II na migration")
    void deadlineReferenceIsArt19() {
        assertThat(SQL).contains("LGPD art. 19, II (não art. 18)");
    }

    /**
     * SQL executável da repetível: linhas de comentário ({@code --}) removidas, para a
     * asserção valer sobre o que o banco executaria — nunca sobre o que o cabeçalho
     * menciona. (A versão anterior deste teste exigia "ON CONFLICT DO NOTHING" no
     * arquivo inteiro e era satisfeita pelo COMENTÁRIO: vacuosa — defeito D3 da
     * revisão independente, corrigido aqui.)
     */
    private static String executableSql(String sql) {
        return sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("pré-carga de fábrica não semeia nenhuma operação inventada")
    void factoryPreloadInventsNothing() {
        assertThat(FACTORY_SQL).doesNotContain("INSERT INTO lgpd_processing_records");
        String executable = executableSql(FACTORY_SQL);
        // Nenhum SQL de carga existe nesta entrega: o executável é só o set_config
        // do tenant — sem INSERT e sem mecanismo de upsert alegado sem existir.
        assertThat(executable).doesNotContain("INSERT");
        assertThat(executable).doesNotContain("ON CONFLICT");
        assertThat(executable).contains("set_config('app.current_tenant'");
        // A honestidade do arquivo também é guarda: o cabeçalho declara a ausência.
        assertThat(FACTORY_SQL).contains("NENHUM SQL DE CARGA");
        assertThat(FACTORY_SQL).contains("NENHUMA LINHA É SEMEADA");
    }
}
