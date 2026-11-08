package br.com.ziipsan.platform.signing;

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
 * Guardas da V1_14 sem banco, no padrao dos MigrationConsistencyTest do
 * projeto: CHECKs = enums do codigo, qualificada amarrada ao aparato completo
 * e a tabela de assinaturas registrada para RLS.
 */
class SigningMigrationConsistencyTest {

    private static final String SQL = readResource("/db/migration/tenant/V1_14__document_signatures.sql");

    private static String readResource(String path) {
        try (InputStream in = SigningMigrationConsistencyTest.class.getResourceAsStream(path)) {
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
        int inAt = sql.indexOf("IN (", at);
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
        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(sql.substring(open, close));
        Set<String> literals = new java.util.HashSet<>();
        while (m.find()) {
            literals.add(m.group(1));
        }
        return literals;
    }

    @Test
    @DisplayName("CHECK de level da V1_14 e exatamente o enum SignatureLevel")
    void levelCheckMatchesEnum() {
        assertThat(checkLiterals(SQL, "level VARCHAR(20) NOT NULL CHECK (level IN"))
                .isEqualTo(enumNames(SignatureLevel.class));
    }

    @Test
    @DisplayName("CHECK de document_type da V1_14 e exatamente o enum SignableDocumentType")
    void documentTypeCheckMatchesEnum() {
        assertThat(checkLiterals(SQL, "document_type VARCHAR(40) NOT NULL CHECK (document_type IN"))
                .isEqualTo(enumNames(SignableDocumentType.class));
    }

    @Test
    @DisplayName("CHECK de outcome e exatamente o enum de desfechos da verificacao")
    void outcomeCheckMatchesEnum() {
        assertThat(checkLiterals(SQL, "outcome VARCHAR(20) NOT NULL CHECK (outcome IN"))
                .isEqualTo(enumNames(SignatureVerification.Outcome.class));
    }

    @Test
    @DisplayName("qualificada amarrada a PAdES/PSC/politica/ACT/token e tabela registrada para RLS")
    void qualifiedGuardAndRegistration() {
        assertThat(SQL).contains("signature_format = 'PAdES'");
        assertThat(SQL).contains("register_tenant_table('document_signatures')");
        assertThat(SQL).doesNotContain("register_tenant_table('signature_verification_attempts')");
    }

    @Test
    @DisplayName("fora da qualificada, PSC e ACT sao NULL (D5): psc_provider/timestamp_authority sao colunas so da QUALIFIED")
    void nonQualifiedHasNoPscOrAuthorityColumns() {
        assertThat(SQL).contains("psc_provider IS NULL");
        assertThat(SQL).contains("timestamp_authority IS NULL");
    }
}
