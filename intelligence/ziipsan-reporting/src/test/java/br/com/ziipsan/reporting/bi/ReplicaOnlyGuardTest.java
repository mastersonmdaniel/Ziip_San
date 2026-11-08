package br.com.ziipsan.reporting.bi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-47 — teste do aceite: "nenhum relatório nem consulta de BI no
 * primário, com teste que confere a conexão de CADA fonte de dados".
 * A guarda confere a lista inteira e nomeia cada fonte reprovada; uma
 * única fonte errada reprova o conjunto.
 */
class ReplicaOnlyGuardTest {

    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final SupersetDataSource.Endpoint PRIMARY =
            new SupersetDataSource.Endpoint("db-primary.internal", 5432, "ziipsan");
    private static final SupersetDataSource.Endpoint REPLICA =
            new SupersetDataSource.Endpoint("db-replica.internal", 5432, "ziipsan");

    private final ReplicaOnlyGuard guard = new ReplicaOnlyGuard(PRIMARY);

    private static SupersetDataSource source(String name, SupersetDataSource.Endpoint endpoint,
                                             SupersetDataSource.Kind kind) {
        return new SupersetDataSource(name, A, TenantBiRole.roleNameFor(A), endpoint, kind);
    }

    @Test
    void fonteNaReplicaPassa() {
        assertThat(guard.checkAll(List.of(
                source("bi-tenant-a", REPLICA, SupersetDataSource.Kind.READ_REPLICA)))).isEmpty();
    }

    @Test
    void fonteNoPrimarioEhReprovadaMesmoDeclaradaComoReplica() {
        List<ReplicaOnlyGuard.Violation> violations = guard.checkAll(List.of(
                source("bi-tenant-a", REPLICA, SupersetDataSource.Kind.READ_REPLICA),
                source("bi-escondida", PRIMARY, SupersetDataSource.Kind.READ_REPLICA)));
        // O tipo MENTE ("replica") mas o endpoint e o do primario: as duas
        // fontes foram conferidas e so a escondida aparece, nomeada.
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).dataSourceName()).isEqualTo("bi-escondida");
        assertThat(violations.get(0).code()).isEqualTo("bi-datasource-on-primary");
    }

    @Test
    void tipoDiferenteDeReplicaEhReprovado() {
        List<ReplicaOnlyGuard.Violation> violations = guard.checkAll(List.of(
                source("bi-outra", REPLICA, SupersetDataSource.Kind.OTHER)));
        assertThat(violations).extracting(ReplicaOnlyGuard.Violation::code)
                .containsExactly("bi-datasource-not-replica");
    }

    @Test
    void requireReplicaOnlyLancaComQualquerViolacao() {
        assertThatThrownBy(() -> guard.requireReplicaOnly(List.of(
                source("bi-x", PRIMARY, SupersetDataSource.Kind.PRIMARY))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fonteComRoleDeOutroTenantNemSeConstroi() {
        UUID outro = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertThatThrownBy(() -> new SupersetDataSource("bi-compartilhada", A,
                TenantBiRole.roleNameFor(outro), REPLICA, SupersetDataSource.Kind.READ_REPLICA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role do próprio tenant");
    }

    @Test
    void uriComCredencialEmbutidaEhRecusada() {
        assertThatThrownBy(() -> SupersetDataSource.fromReferenceUri(
                "bi-a", A, "postgresql://bi_role:segredo@db-replica.internal:5432/ziipsan",
                SupersetDataSource.Kind.READ_REPLICA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credenciais");
        SupersetDataSource ok = SupersetDataSource.fromReferenceUri(
                "bi-a", A, "postgresql://db-replica.internal:5432/ziipsan",
                SupersetDataSource.Kind.READ_REPLICA);
        assertThat(ok.endpoint()).isEqualTo(REPLICA);
        assertThat(ok.roleName()).isEqualTo(TenantBiRole.roleNameFor(A));
    }
}
