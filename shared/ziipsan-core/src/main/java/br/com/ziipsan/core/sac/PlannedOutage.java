package br.com.ziipsan.core.sac;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Interrupcao programada (ED-35; NR 11, arts. 39 a 41): cadastro por area
 * (localidade, setor, rota \u2014 SEM mapa; area calculada pelo GIS e da ED-54),
 * janela, motivo, unidades afetadas e, dentre elas, as unidades essenciais
 * marcadas no cadastro da P1.3 ({@code unit_essential_users}, V3_12), que
 * exigem fornecimento de emergencia. A previsao de retorno so muda por
 * operador identificado. Localidade, setor e rota sao TODOS obrigatorios
 * no cadastro da area (correcao pos-reprovacao, M3). Identificadores sao
 * {@link UUID}, convencao do repo (correcao M4).
 */
public final class PlannedOutage {

    public record Area(String locality, String sector, String route) {
        public Area {
            if (locality == null || locality.isBlank())
                throw new IllegalArgumentException("localidade da area obrigatoria");
            if (sector == null || sector.isBlank())
                throw new IllegalArgumentException("setor da area obrigatorio");
            if (route == null || route.isBlank())
                throw new IllegalArgumentException("rota da area obrigatoria");
        }
    }

    /** Unidade essencial da P1.3: kind em HOSPITAL, SCHOOL, COLLECTIVE_CARE (V3_12). */
    public record EssentialUnit(UUID consumerUnitId, String essentialKind) {
        public EssentialUnit {
            Objects.requireNonNull(consumerUnitId, "unidade essencial obrigatoria");
            if (!List.of("HOSPITAL", "SCHOOL", "COLLECTIVE_CARE").contains(essentialKind))
                throw new IllegalArgumentException("tipo de unidade essencial desconhecido: " + essentialKind);
        }
    }

    private final UUID outageId;
    private final Area area;
    private final LocalDateTime scheduledStart;
    private LocalDateTime expectedReturn;
    private final String reason;
    private final List<UUID> affectedUnitIds;
    private final List<EssentialUnit> essentialUnits;
    private UUID lastReturnUpdateBy;

    public PlannedOutage(UUID outageId, Area area, LocalDateTime scheduledStart,
                         LocalDateTime expectedReturn, String reason,
                         List<UUID> affectedUnitIds, List<EssentialUnit> essentialUnits) {
        this.outageId = Objects.requireNonNull(outageId, "interrupcao obrigatoria");
        this.area = Objects.requireNonNull(area, "area obrigatoria");
        this.scheduledStart = Objects.requireNonNull(scheduledStart, "inicio obrigatorio");
        this.expectedReturn = Objects.requireNonNull(expectedReturn, "previsao de retorno obrigatoria");
        if (!expectedReturn.isAfter(scheduledStart))
            throw new IllegalArgumentException("previsao de retorno deve ser posterior ao inicio");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("motivo da interrupcao obrigatorio");
        this.reason = reason;
        this.affectedUnitIds = List.copyOf(affectedUnitIds);
        this.essentialUnits = List.copyOf(essentialUnits);
        for (EssentialUnit e : this.essentialUnits)
            if (!this.affectedUnitIds.contains(e.consumerUnitId()))
                throw new IllegalArgumentException(
                        "unidade essencial fora da area afetada: " + e.consumerUnitId());
    }

    public boolean emergencySupplyRequired() { return !essentialUnits.isEmpty(); }

    /** Previsao de retorno atualizada PELO OPERADOR (identificado, obrigatorio). */
    public void updateExpectedReturn(LocalDateTime newReturn, UUID operatorId) {
        Objects.requireNonNull(newReturn, "nova previsao obrigatoria");
        Objects.requireNonNull(operatorId, "atualizacao da previsao exige o operador");
        if (!newReturn.isAfter(scheduledStart))
            throw new IllegalArgumentException("nova previsao deve ser posterior ao inicio");
        this.expectedReturn = newReturn;
        this.lastReturnUpdateBy = operatorId;
    }

    public UUID outageId() { return outageId; }
    public Area area() { return area; }
    public LocalDateTime scheduledStart() { return scheduledStart; }
    public LocalDateTime expectedReturn() { return expectedReturn; }
    public String reason() { return reason; }
    public List<UUID> affectedUnitIds() { return affectedUnitIds; }
    public List<EssentialUnit> essentialUnits() { return essentialUnits; }
    public UUID lastReturnUpdateBy() { return lastReturnUpdateBy; }
    public boolean activeOn(LocalDateTime when) {
        return !when.isBefore(scheduledStart) && when.isBefore(expectedReturn);
    }
    /** Interrupcao ja encerrada no instante dado (janela inteira no passado). */
    public boolean finishedOn(LocalDateTime when) {
        return !when.isBefore(expectedReturn);
    }
}
