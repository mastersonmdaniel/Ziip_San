package br.com.ziipsan.integration.erp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-62: indicadores de compras, RH e orcamento LIDOS do ERP do cliente
 * para o painel estrategico (ADR-0028, item i). O produto NAO constroi
 * modulos de compras, RH ou orcamento: aqui so existe a LEITURA, a
 * validacao e a disponibilizacao — inclusive para a alinea t do roteiro
 * VISAN ({@link #visanItemT}). Valor, competencia e origem sao os que o
 * ERP devolveu; nada e calculado nem estimado aqui.
 */
public final class ErpIndicators {
    private ErpIndicators() {}

    public enum Area { PURCHASES, HR, BUDGET }

    public record ErpIndicator(UUID tenantId, ErpProvider provider, Area area, String indicatorCode,
                               BigDecimal value, LocalDate competence, String sourceReference) {
        public ErpIndicator {
            Objects.requireNonNull(tenantId, "tenant do indicador e obrigatorio");
            Objects.requireNonNull(provider, "ERP de origem do indicador e obrigatorio");
            Objects.requireNonNull(area, "area do indicador (compras/RH/orcamento) e obrigatoria");
            if (indicatorCode == null || indicatorCode.isBlank()) throw new IllegalArgumentException("codigo do indicador lido do ERP e obrigatorio");
            Objects.requireNonNull(value, "valor do indicador e o devolvido pelo ERP (nunca calculado aqui)");
            Objects.requireNonNull(competence, "competencia do indicador e obrigatoria");
            if (sourceReference == null || sourceReference.isBlank()) throw new IllegalArgumentException("referencia de origem no ERP e obrigatoria (rastreabilidade)");
        }
    }

    /** Visao do painel estrategico: somente leitura, filtrada por tenant e competencia. */
    public static List<ErpIndicator> strategicPanel(List<ErpIndicator> read, UUID tenantId, LocalDate competence) {
        Objects.requireNonNull(read, "indicadores lidos sao obrigatorios");
        Objects.requireNonNull(tenantId, "tenant do painel e obrigatorio");
        Objects.requireNonNull(competence, "competencia do painel e obrigatoria");
        return read.stream()
                .filter(i -> i.tenantId().equals(tenantId) && i.competence().equals(competence))
                .toList();
    }

    /** Alinea t do roteiro VISAN: os mesmos indicadores lidos, sem recorte novo nem calculo. */
    public static List<ErpIndicator> visanItemT(List<ErpIndicator> read, UUID tenantId, LocalDate competence) {
        return strategicPanel(read, tenantId, competence);
    }
}
