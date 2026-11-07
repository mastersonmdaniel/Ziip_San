package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import br.com.ziipsan.commercial.registry.domain.ConnectionStatus.SystemRole;

import java.util.Set;

/**
 * Operações de ligação da ED-13, executadas como EFEITOS de OS do núcleo
 * da P1.31 (ADR-0039): cada operação é um código de efeito registrado no
 * {@code EffectHandlerRegistry} do motor, com alvo tipado
 * {@code WATER_CONNECTION}/{@code SEWER_CONNECTION} e a situação-alvo
 * identificada pelo seu papel de sistema no catálogo único
 * {@code connection_statuses} da P2.23.
 *
 * <p>O código concreto da situação-alvo vem na carga do efeito
 * ({@code statusCode}): o catálogo do tenant pode ter vários códigos com
 * o mesmo papel, e escolher um deles sem declaração seria inventar dado.
 * A regra de transição ({@link ConnectionOperationRules}) confere que o
 * papel da situação-alvo é o papel desta operação, que o papel vigente
 * admite a operação e que a operação admite o TIPO da ligação; fora
 * disso, recusa explícita.
 *
 * <p>Semântica distinta de cada volta a {@code ACTIVE} (correção da
 * revisão: CONNECT e RESTORE eram idênticos, e SUSPENDED era um beco
 * sem saída):
 * <ul>
 *   <li>CONNECT — nascimento do fornecimento: só de NO_CONNECTION
 *     (imóvel que nunca teve a ligação, ou cuja ligação foi encerrada
 *     no cadastro e substituída);</li>
 *   <li>RESTORE — restabelecimento de ligação DESLIGADA (suprimida) ou
 *     SUSPENSA: a ligação existia fisicamente e volta a ativa por
 *     decisão cadastral/comercial;</li>
 *   <li>RECONNECT — religação de ligação CORTADA ou SUSPENSA por corte:
 *     é o par operacional do CUT (a P3.4 tem a sua regra de religação);
 *     SUSPENDED entra aqui porque a suspensão operacional (ex.: corte
 *     temporário) se desfaz por religação, e em RESTORE porque a
 *     suspensão cadastral também precisa de saída — as duas OS têm
 *     tipo, preço (ED-34) e fluxo próprios.</li>
 * </ul>
 * PLUG (tamponamento) é operação SÓ DE ESGOTO: tamponar ramal de água
 * não é esta operação (o desligamento da água é SUPPRESS).
 */
public enum ConnectionOperation {

    /** Ligar: nasce o fornecimento (sem ligação -> ativa). */
    CONNECT("CONNECTION_CONNECT", SystemRole.ACTIVE,
            Set.of(SystemRole.NO_CONNECTION),
            Set.of(ConnectionStatus.Kind.WATER, ConnectionStatus.Kind.SEWER)),
    /** Cortar: ligada e abastecendo -> cortada (papel CUT do catálogo). */
    CUT("CONNECTION_CUT", SystemRole.CUT, Set.of(SystemRole.ACTIVE),
            Set.of(ConnectionStatus.Kind.WATER, ConnectionStatus.Kind.SEWER)),
    /** Suprimir: retirada da ligação -> desligada (débito conforme a bifurcação do tenant). */
    SUPPRESS("CONNECTION_SUPPRESS", SystemRole.DISCONNECTED,
            Set.of(SystemRole.ACTIVE, SystemRole.CUT, SystemRole.SUSPENDED),
            Set.of(ConnectionStatus.Kind.WATER, ConnectionStatus.Kind.SEWER)),
    /** Restabelecer: ligação suprimida/desligada ou suspensa volta a ativa. */
    RESTORE("CONNECTION_RESTORE", SystemRole.ACTIVE,
            Set.of(SystemRole.DISCONNECTED, SystemRole.SUSPENDED),
            Set.of(ConnectionStatus.Kind.WATER, ConnectionStatus.Kind.SEWER)),
    /** Religar: ligação cortada ou suspensa por corte volta a ativa. */
    RECONNECT("CONNECTION_RECONNECT", SystemRole.ACTIVE,
            Set.of(SystemRole.CUT, SystemRole.SUSPENDED),
            Set.of(ConnectionStatus.Kind.WATER, ConnectionStatus.Kind.SEWER)),
    /** Tamponar: tamponamento do ramal de ESGOTO -> desligada. */
    PLUG("CONNECTION_PLUG", SystemRole.DISCONNECTED,
            Set.of(SystemRole.ACTIVE, SystemRole.CUT, SystemRole.SUSPENDED),
            Set.of(ConnectionStatus.Kind.SEWER));

    private final String effectCode;
    private final SystemRole targetRole;
    private final Set<SystemRole> allowedFrom;
    private final Set<ConnectionStatus.Kind> allowedKinds;

    ConnectionOperation(String effectCode, SystemRole targetRole, Set<SystemRole> allowedFrom,
                        Set<ConnectionStatus.Kind> allowedKinds) {
        this.effectCode = effectCode;
        this.targetRole = targetRole;
        this.allowedFrom = allowedFrom;
        this.allowedKinds = allowedKinds;
    }

    public String effectCode() {
        return effectCode;
    }

    public SystemRole targetRole() {
        return targetRole;
    }

    public Set<SystemRole> allowedFrom() {
        return allowedFrom;
    }

    public Set<ConnectionStatus.Kind> allowedKinds() {
        return allowedKinds;
    }

    public boolean allowsKind(ConnectionStatus.Kind kind) {
        return allowedKinds.contains(kind);
    }

    /** Operações sobre a água que disparam a bifurcação sewer.out_of_use_on_water_cut. */
    public boolean triggersSewerOutOfUseCascade() {
        return this == CUT || this == SUPPRESS;
    }

    public static ConnectionOperation fromEffectCode(String effectCode) {
        for (ConnectionOperation op : values()) {
            if (op.effectCode.equals(effectCode)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Codigo de efeito de ligacao desconhecido: " + effectCode);
    }
}
