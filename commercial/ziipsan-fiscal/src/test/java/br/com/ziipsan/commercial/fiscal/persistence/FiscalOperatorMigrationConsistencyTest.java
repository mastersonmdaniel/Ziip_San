package br.com.ziipsan.commercial.fiscal.persistence;

import br.com.ziipsan.commercial.fiscal.queue.FiscalOperatorActionStatus;
import br.com.ziipsan.commercial.fiscal.queue.FiscalOperatorActionType;
import br.com.ziipsan.commercial.fiscal.queue.FiscalOperatorPermissions;
import br.com.ziipsan.commercial.fiscal.queue.FiscalPendencyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência da V4_11 (P2.33) com o código: os literais dos CHECKs são exatamente
 * os enums das ações/pendências, as permissões próprias estão no desenho e o
 * dicionário fiscal da V4_5 NÃO é recriado.
 */
class FiscalOperatorMigrationConsistencyTest {

    private static String v411() throws IOException {
        try (InputStream in = FiscalOperatorMigrationConsistencyTest.class.getResourceAsStream(
                "/db/migration/tenant/V4_11__fiscal_operator_queue.sql")) {
            assertThat(in).as("migration V4_11 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("tabelas da camada do operador, registradas como tabelas de tenant, com idempotência única")
    void operatorTables() throws IOException {
        String sql = v411();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS fiscal_operator_actions")
                .contains("CREATE TABLE IF NOT EXISTS fiscal_pendency_work_items")
                .contains("uq_fiscal_operator_actions_idempotency")
                .contains("uq_fiscal_operator_actions_pending_cancellation")
                .contains("uq_fiscal_pendency_open")
                .contains("register_tenant_table('fiscal_operator_actions')")
                .contains("register_tenant_table('fiscal_pendency_work_items')");
    }

    @Test
    @DisplayName("CHECKs da V4_11 listam exatamente os enums de ação, estado e pendência do código")
    void checksMatchEnums() throws IOException {
        String sql = v411();
        for (FiscalOperatorActionType type : FiscalOperatorActionType.values()) {
            assertThat(sql).as("tipo de ação %s no CHECK", type).contains("'" + type.name() + "'");
        }
        for (FiscalOperatorActionStatus status : FiscalOperatorActionStatus.values()) {
            assertThat(sql).as("estado %s no CHECK", status).contains("'" + status.name() + "'");
        }
        for (FiscalPendencyKind kind : FiscalPendencyKind.values()) {
            assertThat(sql).as("pendência %s no CHECK", kind).contains("'" + kind.name() + "'");
        }
        // E nenhum literal a mais nos CHECKs: as listas são as dos enums.
        assertThat(sql).contains("CHECK (action_type IN ('RESEND', 'VOIDING', 'CANCELLATION_REQUEST'))")
                .contains("CHECK (status IN ('REQUESTED', 'PENDING', 'COMPLETED', 'FAILED'))")
                .contains("CHECK (pendency_kind IN ('REJECTION', 'CONTINGENCY', 'CANCELLATION_PENDING'))");
    }

    @Test
    @DisplayName("as quatro permissões são códigos recurso:ação distintos (permissão própria por ação)")
    void permissionsAreDistinctResourceActionCodes() {
        var all = Arrays.asList(FiscalOperatorPermissions.READ, FiscalOperatorPermissions.RESEND,
                FiscalOperatorPermissions.VOID_NUMBER, FiscalOperatorPermissions.REQUEST_CANCELLATION);
        assertThat(all).doesNotHaveDuplicates();
        assertThat(all).allMatch(p -> p.matches("fiscal:[a-z-]+"));
    }

    @Test
    @DisplayName("a V4_11 NÃO recria o dicionário da V4_5 nem a extensão da V4_7")
    void dictionaryNotDuplicated() throws IOException {
        String sql = v411();
        String lower = sql.toLowerCase();
        assertThat(lower).doesNotContainPattern(
                "create\\s+table\\s+(if\\s+not\\s+exists\\s+)?[\"\\w.]*fiscal_(documents|series|issuers|nfage_details|nfage_events)\\b");
    }
}
