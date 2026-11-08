package br.com.ziipsan.intelligence.losses.pressure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ajuste de valvula redutora de pressao (VRP) registrado por OS
 * (ED-86, OPE-04): cada ajuste guarda a OS que o executou, QUEM
 * ajustou, QUANDO, o MOTIVO, o VALOR ANTERIOR e o VALOR NOVO do
 * setpoint (mca) — os seis dados literais do aceite, todos
 * obrigatorios. Ajuste sem mudanca de valor nao e ajuste e recusa.
 *
 * <p>{@link #requireContinuity} confere a cadeia de uma mesma VRP:
 * em ordem cronologica, o valor anterior de cada ajuste e o valor
 * novo do ajuste precedente — cadeia quebrada denuncia ajuste nao
 * registrado e recusa.
 */
public record PrvAdjustment(UUID id, UUID tenantId, UUID zoneId, String prvCode,
                            UUID workOrderId, UUID adjustedByUserId, LocalDateTime adjustedAt,
                            String reason, double previousSetpointMca, double newSetpointMca) {

    public PrvAdjustment {
        Objects.requireNonNull(id, "ajuste de VRP sem id");
        Objects.requireNonNull(tenantId, "ajuste de VRP sem tenant");
        Objects.requireNonNull(zoneId, "ajuste de VRP exige a zona de pressao");
        if (prvCode == null || prvCode.isBlank()) throw new IllegalArgumentException(
                "codigo da valvula redutora de pressao obrigatorio");
        Objects.requireNonNull(workOrderId, "ajuste de VRP exige a OS que o registrou");
        Objects.requireNonNull(adjustedByUserId, "ajuste de VRP exige quem ajustou");
        Objects.requireNonNull(adjustedAt, "ajuste de VRP exige quando foi ajustado");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException(
                "motivo do ajuste de VRP obrigatorio");
        if (previousSetpointMca < 0 || Double.isNaN(previousSetpointMca))
            throw new IllegalArgumentException("valor anterior do setpoint invalido: " + previousSetpointMca);
        if (newSetpointMca < 0 || Double.isNaN(newSetpointMca))
            throw new IllegalArgumentException("valor novo do setpoint invalido: " + newSetpointMca);
        if (previousSetpointMca == newSetpointMca) throw new IllegalArgumentException(
                "ajuste sem mudanca de valor nao e ajuste: " + previousSetpointMca + " -> " + newSetpointMca);
    }

    /** Variacao do setpoint (mca): positiva = aumento, negativa = reducao. */
    public double deltaMca() { return newSetpointMca - previousSetpointMca; }

    /**
     * Cadeia de ajustes de UMA VRP (mesmo tenant, zona e codigo), em
     * qualquer ordem de entrada: ordenada por instante, cada valor
     * anterior deve ser o valor novo do ajuste precedente e nenhum
     * instante se repete.
     */
    public static void requireContinuity(List<PrvAdjustment> adjustments) {
        Objects.requireNonNull(adjustments, "cadeia de ajustes obrigatoria");
        if (adjustments.size() < 2) return;
        PrvAdjustment first = adjustments.get(0);
        for (PrvAdjustment a : adjustments) {
            if (!a.tenantId().equals(first.tenantId()) || !a.zoneId().equals(first.zoneId())
                    || !a.prvCode().equals(first.prvCode()))
                throw new IllegalArgumentException("cadeia mistura VRPs distintas (tenant/zone/prvCode)");
        }
        List<PrvAdjustment> sorted = adjustments.stream()
                .sorted(java.util.Comparator.comparing(PrvAdjustment::adjustedAt)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            PrvAdjustment prev = sorted.get(i - 1);
            PrvAdjustment cur = sorted.get(i);
            if (cur.adjustedAt().equals(prev.adjustedAt()))
                throw new IllegalArgumentException("dois ajustes da mesma VRP no mesmo instante: " + cur.adjustedAt());
            if (cur.previousSetpointMca() != prev.newSetpointMca())
                throw new IllegalArgumentException(
                        "cadeia quebrada na VRP " + cur.prvCode() + ": ajuste de " + cur.adjustedAt()
                                + " declara anterior " + cur.previousSetpointMca()
                                + " mas o ajuste precedente deixou " + prev.newSetpointMca()
                                + " (ajuste nao registrado?)");
        }
    }
}
