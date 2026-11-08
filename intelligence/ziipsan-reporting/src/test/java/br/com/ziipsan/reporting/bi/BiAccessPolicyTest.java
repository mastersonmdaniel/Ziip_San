package br.com.ziipsan.reporting.bi;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-47: política usuário x fonte (só o próprio tenant), auditoria
 * SG-1252 das concessões E das negações, e o invariante LGPD do evento:
 * nenhum campo guarda o texto da consulta.
 */
class BiAccessPolicyTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static final class ListAudit implements BiAccessAuditPort {
        final List<BiAccessEvent> events = new ArrayList<>();

        @Override
        public void record(BiAccessEvent event) {
            events.add(event);
        }
    }

    private static SupersetDataSource sourceOf(UUID tenant) {
        return new SupersetDataSource("bi-" + tenant, tenant, TenantBiRole.roleNameFor(tenant),
                new SupersetDataSource.Endpoint("db-replica.internal", 5432, "ziipsan"),
                SupersetDataSource.Kind.READ_REPLICA);
    }

    @Test
    void usuarioDoProprioTenantPassaEAudita() {
        ListAudit audit = new ListAudit();
        new BiAccessPolicy(audit).authorize(
                new BiUserIdentity(USER, TENANT_A, Set.of("bi-user")), sourceOf(TENANT_A), NOW);
        assertThat(audit.events).hasSize(1);
        assertThat(audit.events.get(0).action()).isEqualTo(BiAccessEvent.Action.DATASET_ACCESS);
        assertThat(audit.events.get(0).tenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void usuarioDeOutroTenantEhNegadoEAuditaAntesDeLancar() {
        ListAudit audit = new ListAudit();
        BiAccessPolicy policy = new BiAccessPolicy(audit);
        assertThatThrownBy(() -> policy.authorize(
                new BiUserIdentity(USER, TENANT_B, Set.of("bi-user")), sourceOf(TENANT_A), NOW))
                .isInstanceOf(BiAccessDeniedException.class);
        assertThat(audit.events).hasSize(1);
        assertThat(audit.events.get(0).action()).isEqualTo(BiAccessEvent.Action.ACCESS_DENIED);
    }

    @Test
    void usuarioSemTenantNaoUsaFonteAlguma() {
        ListAudit audit = new ListAudit();
        BiAccessPolicy policy = new BiAccessPolicy(audit);
        assertThatThrownBy(() -> policy.authorize(
                new BiUserIdentity(USER, null, Set.of("platform-admin")), sourceOf(TENANT_A), NOW))
                .isInstanceOf(BiAccessDeniedException.class);
        assertThat(audit.events).extracting(BiAccessEvent::action)
                .containsExactly(BiAccessEvent.Action.ACCESS_DENIED);
    }

    @Test
    void politicaSemAuditoriaNaoExiste() {
        assertThatThrownBy(() -> new BiAccessPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("SG-1252");
    }

    @Test
    void eventoDeConsultaGuardaResumoNuncaOTexto() {
        String sql = "SELECT * FROM bi_rpt_contas WHERE documento = '12345678901'";
        BiAccessEvent event = BiAccessEvent.queryExecuted(TENANT_A, USER, "bi-a", sql, NOW);
        assertThat(event.queryHash()).hasSize(64);
        assertThat(event.queryLength()).isEqualTo(sql.length());
        String json = event.toJson();
        assertThat(json).contains(event.queryHash());
        assertThat(json).doesNotContain("12345678901");
        assertThat(json).doesNotContain("SELECT");
        // O record tem exatamente os 8 campos declarados: nao ha onde
        // esconder texto de consulta, parametro ou resultado.
        assertThat(BiAccessEvent.class.getRecordComponents()).hasSize(8);
    }

    @Test
    void resumoSoExisteEmConsultaExecutada() {
        assertThatThrownBy(() -> new BiAccessEvent(TENANT_A, USER,
                BiAccessEvent.Action.LOGIN, "bi-a", null, "abc", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        // E consulta executada sem resumo nao se constroi pela fabrica
        // generica: queryExecuted e o unico caminho.
        assertThatThrownBy(() -> BiAccessEvent.of(TENANT_A, USER,
                BiAccessEvent.Action.QUERY_EXECUTED, "bi-a", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
