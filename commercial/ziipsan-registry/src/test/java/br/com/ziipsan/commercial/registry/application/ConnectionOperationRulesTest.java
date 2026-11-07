package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import br.com.ziipsan.commercial.registry.domain.ConnectionStatus.Kind;
import br.com.ziipsan.commercial.registry.domain.ConnectionStatus.SystemRole;
import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-13: MATRIZ COMPLETA das transições das operações de ligação —
 * 6 operações x 6 papéis vigentes = 36 combinações (12 válidas +
 * 24 inválidas), geradas da própria tabela de
 * {@link ConnectionOperation} para nenhuma combinação ficar de fora
 * (correção M2 da revisão), mais o consumo mínimo lido da ligação.
 */
class ConnectionOperationRulesTest {

    private static ConnectionStatus status(Kind kind, SystemRole role) {
        ConnectionStatus s = new ConnectionStatus();
        s.setKind(kind);
        s.setCode(kind + "-" + role);
        s.setSystemRole(role);
        s.setActive(true);
        return s;
    }

    private static Kind kindFor(ConnectionOperation op) {
        // PLUG so existe para esgoto; as demais valem para os dois tipos.
        return op == ConnectionOperation.PLUG ? Kind.SEWER : Kind.WATER;
    }

    static Stream<Arguments> fullMatrix() {
        return java.util.Arrays.stream(ConnectionOperation.values())
                .flatMap(op -> java.util.Arrays.stream(SystemRole.values())
                        .map(from -> Arguments.of(op, from)));
    }

    @ParameterizedTest(name = "{0} de {1}")
    @MethodSource("fullMatrix")
    @DisplayName("matriz 6x6: passa exatamente nas origens admitidas da tabela (12 validas, 24 invalidas)")
    void fullTransitionMatrix(ConnectionOperation op, SystemRole from) {
        Kind kind = kindFor(op);
        if (op.allowedFrom().contains(from)) {
            assertThat(ConnectionOperationRules.requireTransition(
                    op, kind, from, status(kind, op.targetRole()))).isNotNull();
        } else {
            assertThatThrownBy(() -> ConnectionOperationRules.requireTransition(
                    op, kind, from, status(kind, op.targetRole())))
                    .isInstanceOf(ApiException.Conflict.class)
                    .hasMessageContaining("nao parte de uma ligacao");
        }
    }

    @Test
    @DisplayName("a tabela tem exatamente 12 transições válidas e SUSPENDED tem saída para ACTIVE")
    void tableShapeAndSuspendedExit() {
        int valid = java.util.Arrays.stream(ConnectionOperation.values())
                .mapToInt(op -> op.allowedFrom().size()).sum();
        assertThat(valid).isEqualTo(12);
        // Correcao A1: ligacao suspensa NAO e beco sem saida.
        assertThat(ConnectionOperation.RESTORE.allowedFrom()).contains(SystemRole.SUSPENDED);
        assertThat(ConnectionOperation.RECONNECT.allowedFrom()).contains(SystemRole.SUSPENDED);
        assertThat(ConnectionOperationRules.requireTransition(
                ConnectionOperation.RESTORE, Kind.WATER, SystemRole.SUSPENDED,
                status(Kind.WATER, SystemRole.ACTIVE))).isNotNull();
        assertThat(ConnectionOperationRules.requireTransition(
                ConnectionOperation.RECONNECT, Kind.SEWER, SystemRole.SUSPENDED,
                status(Kind.SEWER, SystemRole.ACTIVE))).isNotNull();
    }

    @Test
    @DisplayName("CONNECT x RESTORE diferenciados: CONNECT só nasce de NO_CONNECTION; RESTORE não nasce")
    void connectAndRestoreAreDistinct() {
        assertThatThrownBy(() -> ConnectionOperationRules.requireTransition(
                ConnectionOperation.CONNECT, Kind.WATER, SystemRole.DISCONNECTED,
                status(Kind.WATER, SystemRole.ACTIVE)))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> ConnectionOperationRules.requireTransition(
                ConnectionOperation.RESTORE, Kind.WATER, SystemRole.NO_CONNECTION,
                status(Kind.WATER, SystemRole.ACTIVE)))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("PLUG é só de esgoto: em ligação de água recusa, mesmo com origem e papel certos")
    void plugIsSewerOnly() {
        assertThatThrownBy(() -> ConnectionOperationRules.requireTransition(
                ConnectionOperation.PLUG, Kind.WATER, SystemRole.ACTIVE,
                status(Kind.WATER, SystemRole.DISCONNECTED)))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("nao se aplica a ligacao de WATER");
        assertThat(ConnectionOperationRules.requireTransition(
                ConnectionOperation.PLUG, Kind.SEWER, SystemRole.ACTIVE,
                status(Kind.SEWER, SystemRole.DISCONNECTED))).isNotNull();
        // As demais operacoes aceitam os dois tipos.
        for (ConnectionOperation op : ConnectionOperation.values()) {
            if (op == ConnectionOperation.PLUG) {
                continue;
            }
            SystemRole from = op.allowedFrom().iterator().next();
            assertThat(ConnectionOperationRules.requireTransition(
                    op, Kind.WATER, from, status(Kind.WATER, op.targetRole()))).isNotNull();
            assertThat(ConnectionOperationRules.requireTransition(
                    op, Kind.SEWER, from, status(Kind.SEWER, op.targetRole()))).isNotNull();
        }
    }

    @Test
    @DisplayName("situação-alvo de papel diferente do papel da operação recusa (todas as operações)")
    void targetRoleMismatchRefused() {
        for (ConnectionOperation op : ConnectionOperation.values()) {
            Kind kind = kindFor(op);
            SystemRole wrongTarget = op.targetRole() == SystemRole.ACTIVE
                    ? SystemRole.CUT : SystemRole.ACTIVE;
            SystemRole from = op.allowedFrom().iterator().next();
            assertThatThrownBy(() -> ConnectionOperationRules.requireTransition(
                    op, kind, from, status(kind, wrongTarget)))
                    .isInstanceOf(ApiException.Conflict.class)
                    .hasMessageContaining("exige situacao-alvo de papel");
        }
    }

    @Test
    @DisplayName("situação-alvo de outro tipo e situação desativada recusam")
    void wrongKindAndInactiveRefused() {
        assertThatThrownBy(() -> ConnectionOperationRules.requireTransition(
                ConnectionOperation.CONNECT, Kind.WATER, SystemRole.NO_CONNECTION,
                status(Kind.SEWER, SystemRole.ACTIVE)))
                .isInstanceOf(ApiException.BadRequest.class);
        ConnectionStatus inactive = status(Kind.WATER, SystemRole.ACTIVE);
        inactive.setActive(false);
        assertThatThrownBy(() -> ConnectionOperationRules.requireTransition(
                ConnectionOperation.CONNECT, Kind.WATER, SystemRole.NO_CONNECTION, inactive))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    @DisplayName("cada operação tem código de efeito próprio, resolvível de volta")
    void effectCodesRoundTrip() {
        for (ConnectionOperation op : ConnectionOperation.values()) {
            assertThat(ConnectionOperation.fromEffectCode(op.effectCode())).isEqualTo(op);
        }
        assertThatThrownBy(() -> ConnectionOperation.fromEffectCode("CONNECTION_UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("consumo mínimo da ligação: medido 7 com mínimo 10 fatura 10; medido 12 fatura 12")
    void minimumFromConnectionStatus() {
        ConnectionStatus s = status(Kind.WATER, SystemRole.ACTIVE);
        s.setMinimumConsumption(true);
        s.setMinimumBillingConsumption(10);
        assertThat(ConnectionOperationRules.minimumBilledConsumption(s, 7)).isEqualTo(10);
        assertThat(ConnectionOperationRules.minimumBilledConsumption(s, 12)).isEqualTo(12);
    }

    @Test
    @DisplayName("sem flag de mínimo, fatura o medido; flag sem valor declarado é erro explícito")
    void minimumFlagWithoutValueIsExplicitError() {
        ConnectionStatus s = status(Kind.WATER, SystemRole.ACTIVE);
        assertThat(ConnectionOperationRules.minimumBilledConsumption(s, 7)).isEqualTo(7);
        s.setMinimumConsumption(true);
        assertThatThrownBy(() -> ConnectionOperationRules.minimumBilledConsumption(s, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimum_billing_consumption");
    }
}
