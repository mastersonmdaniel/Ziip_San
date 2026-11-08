package br.com.ziipsan.core.quality;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Plano de amostragem de um sistema, por periodo. As quantidades
 * planejadas por ponto e parametro sao DEFINIDAS NO PLANO (pelo
 * prestador/responsavel tecnico, a partir da norma vigente para o seu
 * sistema): REGRA DURA da ED-50 — nenhuma frequencia minima nem
 * quantidade nasce de fabrica, porque as fontes do projeto nao fixam
 * os numeros do plano (eles dependem do sistema e da populacao, na
 * Portaria GM/MS 888/2021) e copia-los de memoria e proibido. Linha
 * sem quantidade positiva nao entra no plano.
 */
public record SamplingPlan(
        UUID id,
        String supplySystem,
        YearMonth periodStart,
        YearMonth periodEnd,
        String catalogVersion,
        List<PlanLine> lines) {

    public record PlanLine(UUID pointId, String parameterKey, int plannedCount) {
        public PlanLine {
            if (pointId == null) throw new IllegalArgumentException("pointId da linha do plano e obrigatorio");
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey da linha do plano e obrigatorio");
            if (plannedCount <= 0) throw new IllegalArgumentException(
                    "plannedCount deve ser maior que zero para " + parameterKey
                            + ": a quantidade planejada vem do plano do tenant, nunca de fabrica");
        }
    }

    public SamplingPlan {
        if (id == null) throw new IllegalArgumentException("id do plano e obrigatorio");
        if (supplySystem == null || supplySystem.isBlank())
            throw new IllegalArgumentException("supplySystem do plano e obrigatorio");
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart))
            throw new IllegalArgumentException("periodo do plano invalido");
        if (catalogVersion == null || catalogVersion.isBlank())
            throw new IllegalArgumentException("o plano referencia a versao do catalogo normativo");
        if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("plano sem linhas nao e plano");
        lines = List.copyOf(lines);
        long distinct = lines.stream().map(l -> l.pointId() + "|" + l.parameterKey()).distinct().count();
        if (distinct != lines.size())
            throw new IllegalArgumentException("plano com linha duplicada (mesmo ponto e parametro)");
    }

    public boolean covers(YearMonth month) {
        return !month.isBefore(periodStart) && !month.isAfter(periodEnd);
    }

    /** Quantidade planejada no mes para a linha (ponto, parametro); 0 se a linha nao existe. */
    public int plannedFor(UUID pointId, String parameterKey) {
        return lines.stream()
                .filter(l -> l.pointId().equals(pointId) && l.parameterKey().equals(parameterKey))
                .mapToInt(PlanLine::plannedCount)
                .sum();
    }
}
