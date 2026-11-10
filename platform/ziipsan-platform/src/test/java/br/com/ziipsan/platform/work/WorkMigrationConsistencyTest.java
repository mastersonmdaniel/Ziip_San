package br.com.ziipsan.platform.work;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas das migrations da P2.28 sem banco, no padrão dos MigrationConsistencyTest do
 * projeto: os CHECKs da V105 listam exatamente os enums do código, as tabelas da
 * reserva §3 existem e são registradas para RLS, a segregação está em CHECK no banco,
 * e a V9_13 define os wf.* com as classes do aceite.
 *
 * <p>Pós-revisão m3: a conferência CHECK × enum deixou de ser só contenção
 * (enum ⊆ CHECK) e passou a IGUALDADE de conjuntos nos dois sentidos — valor a
 * mais no CHECK (que o código não conhece) também reprova.
 */
class WorkMigrationConsistencyTest {

    private static final String TENANT_SQL = readResource("/db/migration/tenant/V105__work_items_and_delegations.sql");
    private static final String CORE_SQL = readResource("/db/migration/core/V9_13__p2_28_workflow_parameters.sql");

    private static String readResource(String path) {
        try (InputStream in = WorkMigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    /** Trecho do SQL entre dois marcadores (para desambiguar CHECKs repetidos por tabela). */
    private static String segment(String sql, String fromMarker, String toMarker) {
        int from = sql.indexOf(fromMarker);
        assertThat(from).as("marcador %s", fromMarker).isGreaterThanOrEqualTo(0);
        int to = toMarker == null ? sql.length() : sql.indexOf(toMarker, from + fromMarker.length());
        assertThat(to).as("marcador %s depois de %s", toMarker, fromMarker).isGreaterThan(from);
        return sql.substring(from, to);
    }

    /**
     * Literais {@code 'XXX'} da lista {@code IN (...)} que segue o marcador —
     * o conjunto EXATO gravado no CHECK (nem a mais, nem a menos).
     */
    private static Set<String> checkLiterals(String sql, String marker) {
        int markerAt = sql.indexOf(marker);
        assertThat(markerAt).as("marcador %s", marker).isGreaterThanOrEqualTo(0);
        int open = sql.indexOf('(', markerAt);
        int close = sql.indexOf(')', open);
        assertThat(open).isGreaterThanOrEqualTo(0);
        assertThat(close).isGreaterThan(open);
        Set<String> literals = new HashSet<>();
        Matcher matcher = Pattern.compile("'([A-Z][A-Z0-9_]*)'").matcher(sql.substring(open, close));
        while (matcher.find()) {
            literals.add(matcher.group(1));
        }
        return literals;
    }

    @Test
    @DisplayName("CHECKs da V105 são IGUAIS aos enums WorkItemType, WorkItemStatus, ApprovalStatus e Channel (igualdade nos dois sentidos — correção m3)")
    void checksMatchEnums() {
        assertThat(checkLiterals(TENANT_SQL, "item_type IN"))
                .as("CHECK de item_type == WorkItemType")
                .isEqualTo(enumNames(WorkItemType.class));
        assertThat(enumNames(WorkItemType.class)).hasSize(14);

        String workItemsTable = segment(TENANT_SQL,
                "CREATE TABLE IF NOT EXISTS work_items", "CREATE TABLE IF NOT EXISTS approval_tasks");
        assertThat(checkLiterals(workItemsTable, "status IN"))
                .as("CHECK de status de work_items == WorkItemStatus")
                .isEqualTo(enumNames(WorkItemStatus.class));

        String approvalTasksTable = segment(TENANT_SQL,
                "CREATE TABLE IF NOT EXISTS approval_tasks", "CREATE TABLE IF NOT EXISTS work_item_delegations");
        assertThat(checkLiterals(approvalTasksTable, "status IN"))
                .as("CHECK de status de approval_tasks == ApprovalStatus")
                .isEqualTo(enumNames(ApprovalStatus.class));
        assertThat(checkLiterals(approvalTasksTable, "decision_channel IN"))
                .as("CHECK de decision_channel == ApprovalRule.Channel")
                .isEqualTo(enumNames(ApprovalRule.Channel.class));

        // A trilha não tem enum no código (os tipos são literais do serviço):
        // o conjunto esperado é declarado aqui, e a igualdade também é nos dois sentidos.
        assertThat(checkLiterals(TENANT_SQL, "event_type IN"))
                .as("CHECK de event_type == eventos gravados pelo serviço")
                .isEqualTo(Set.of("CREATED", "STARTED", "APPROVED", "REJECTED", "CLOSED", "ESCALATED"));
    }

    @Test
    @DisplayName("as tabelas da reserva V105 existem (work_items, approval_tasks, work_item_delegations, work_item_events) e são registradas para RLS")
    void tablesAndRlsRegistration() {
        assertThat(TENANT_SQL).contains("CREATE TABLE IF NOT EXISTS work_items");
        assertThat(TENANT_SQL).contains("CREATE TABLE IF NOT EXISTS approval_tasks");
        assertThat(TENANT_SQL).contains("CREATE TABLE IF NOT EXISTS work_item_delegations");
        assertThat(TENANT_SQL).contains("CREATE TABLE IF NOT EXISTS work_item_events");
        assertThat(TENANT_SQL).contains("register_tenant_table('work_items')");
        assertThat(TENANT_SQL).contains("register_tenant_table('approval_tasks')");
        assertThat(TENANT_SQL).contains("register_tenant_table('work_item_delegations')");
        assertThat(TENANT_SQL).contains("register_tenant_table('work_item_events')");
    }

    @Test
    @DisplayName("segregação também no banco: CHECK decided_by <> requester_id e unicidades (tenant_id, id) antes das FKs")
    void segregationInDatabase() {
        assertThat(TENANT_SQL).contains("decided_by IS NULL OR decided_by <> requester_id");
        assertThat(TENANT_SQL).contains("uq_work_items_source_message");
        assertThat(TENANT_SQL).contains("uq_approval_tasks_work_item");
        assertThat(TENANT_SQL.indexOf("uq_work_items_tenant_id"))
                .isLessThan(TENANT_SQL.indexOf("fk_approval_tasks_work_item"));
    }

    @Test
    @DisplayName("a V105 não semeia dado nenhum (tipos, eventos e permissões são catálogos dos donos)")
    void noSeedData() {
        assertThat(TENANT_SQL).doesNotContain("INSERT INTO work_items");
        assertThat(TENANT_SQL).doesNotContain("INSERT INTO approval_tasks");
        assertThat(TENANT_SQL).doesNotContain("INSERT INTO work_item_delegations");
    }

    @Test
    @DisplayName("V9_13: wf.self_approval classe a com padrão false, LOCKED e ALLOWED_VALUES [false]; wf.delegation_max_days e wf.escalation_hours classe d sem padrão inventado")
    void workflowParametersDefined() {
        assertThat(CORE_SQL).contains("'wf.self_approval', 'BOOLEAN', 'a', 'false'");
        assertThat(CORE_SQL).contains("'wf.self_approval', 'LOCKED', 'false'");
        // Correção m2(a): o ALLOWED_VALUES '[false]' da V9_13 também é asserido —
        // antes só definição + LOCKED eram conferidos e o limite de valores
        // admissíveis podia sumir da migration sem o teste notar.
        assertThat(CORE_SQL).contains("'wf.self_approval', 'ALLOWED_VALUES', '[false]'");
        assertThat(CORE_SQL).contains("'wf.delegation_max_days', 'INT', 'd', NULL");
        assertThat(CORE_SQL).contains("'wf.escalation_hours', 'INT', 'd', NULL");
    }
}
