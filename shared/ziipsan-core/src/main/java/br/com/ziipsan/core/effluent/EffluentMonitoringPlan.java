package br.com.ziipsan.core.effluent;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Plano de automonitoramento do efluente de UMA ETE, por periodo:
 * plano de coleta, amostragem e analise sobre o laboratorio e a
 * cadeia de custodia da ED-50. As quantidades planejadas por ponto
 * de lancamento e parametro sao DEFINIDAS NO PLANO (pelo prestador/
 * responsavel tecnico, a partir da licenca e da norma vigente para
 * a sua ETE): REGRA espelhada da ED-50 — nenhuma frequencia nem
 * quantidade nasce de fabrica, porque as fontes do projeto nao
 * fixam os numeros do automonitoramento. Linha sem quantidade
 * positiva nao entra no plano.
 */
public record EffluentMonitoringPlan(
        UUID id,
        UUID tenantId,
        UUID wastewaterUnitId,
        YearMonth periodStart,
        YearMonth periodEnd,
        String catalogVersion,
        List<PlanLine> lines) {

    /** Linha do plano: ponto de coleta (codigo do prestador) x parametro x quantidade no periodo. */
    public record PlanLine(String samplingPointCode, String parameterKey, int plannedCount) {
        public PlanLine {
            if (samplingPointCode == null || samplingPointCode.isBlank())
                throw new IllegalArgumentException("samplingPointCode da linha do plano e obrigatorio");
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey da linha do plano e obrigatorio");
            if (plannedCount <= 0) throw new IllegalArgumentException(
                    "plannedCount deve ser maior que zero para " + parameterKey
                            + ": a quantidade planejada vem do plano do tenant, nunca de fabrica");
        }
    }

    public EffluentMonitoringPlan {
        if (id == null) throw new IllegalArgumentException("id do plano e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId do plano e obrigatorio");
        if (wastewaterUnitId == null)
            throw new IllegalArgumentException("a ETE do plano de automonitoramento e obrigatoria");
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart))
            throw new IllegalArgumentException("periodo do plano invalido");
        if (catalogVersion == null || catalogVersion.isBlank())
            throw new IllegalArgumentException("o plano referencia a versao do catalogo de padrao de efluente");
        if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("plano de automonitoramento sem linhas nao e plano");
        lines = List.copyOf(lines);
        long distinct = lines.stream()
                .map(l -> l.samplingPointCode() + "|" + l.parameterKey()).distinct().count();
        if (distinct != lines.size())
            throw new IllegalArgumentException("plano com linha duplicada (mesmo ponto e parametro)");
    }

    public boolean covers(YearMonth month) {
        return !month.isBefore(periodStart) && !month.isAfter(periodEnd);
    }

    /** Quantidade planejada para a linha (ponto, parametro); 0 se a linha nao existe. */
    public int plannedFor(String samplingPointCode, String parameterKey) {
        return lines.stream()
                .filter(l -> l.samplingPointCode().equals(samplingPointCode)
                        && l.parameterKey().equals(parameterKey))
                .mapToInt(PlanLine::plannedCount)
                .sum();
    }
}
