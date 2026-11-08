package br.com.ziipsan.core.certification.env14001;

import br.com.ziipsan.core.effluent.SludgeAspectSummary;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A LEITURA do aspecto ambiental do lodo pela ED-70 (aceite da ED-70
 * e da ED-105; JOR-16, OPE-25): a geracao e a destinacao do lodo sao
 * REGISTRADAS pela ED-105 e valem para todo tenant com estacao, COM A
 * NORMA LIGADA OU DESLIGADA — a obrigacao e REGULATORY, nao depende
 * da ISO 14001. Por isso esta classe NAO recebe
 * {@link Iso14001Activation} e NAO passa pela porta
 * {@code requireEnabled()}: ela le o resumo real da ED-105
 * ({@link SludgeAspectSummary}) e nao registra nada — nenhum metodo
 * desta classe cria, altera ou apaga geracao/destinacao, e o pacote
 * da ED-70 nao tem tabela de lodo (ver o SQL pendente: nenhuma tabela
 * de residuo).
 *
 * <p>Quando a norma esta ligada, o aspecto declarativo correspondente
 * e o {@link EnvironmentalAspect} de template de lodo
 * ({@code WTP-SLUDGE}/{@code WWTP-SLUDGE} do catalogo sanitation);
 * quando esta desligada, esta leitura continua disponivel e os totais
 * continuam sendo os da ED-105 — e exatamente o caso que o aceite
 * manda testar nos dois estados.</p>
 */
public record SludgeAspectView(UUID tenantId, Map<String, BigDecimal> generatedByKindAndUnit,
                               long dispatchedCount, long awaitingDestinationCount) {

    public SludgeAspectView {
        Objects.requireNonNull(tenantId, "tenantId da leitura do lodo e obrigatorio");
        Objects.requireNonNull(generatedByKindAndUnit,
                "generatedByKindAndUnit (totais da ED-105) e obrigatorio");
        generatedByKindAndUnit = Map.copyOf(generatedByKindAndUnit);
        if (dispatchedCount < 0 || awaitingDestinationCount < 0) {
            throw new IllegalArgumentException("contagens da leitura do lodo nao podem ser negativas");
        }
    }

    /**
     * Le o resumo REAL da ED-105. Nao exige ativacao alguma: a
     * obrigacao do lodo vale com a norma ligada ou desligada (aceite).
     */
    public static SludgeAspectView read(SludgeAspectSummary summary) {
        Objects.requireNonNull(summary, "resumo do lodo da ED-105 (SludgeAspectSummary) e obrigatorio");
        return new SludgeAspectView(summary.tenantId(), summary.generatedByKindAndUnit(),
                summary.dispatchedCount(), summary.awaitingDestinationCount());
    }

    /** Verdadeiro quando ha registro de lodo ainda sem destinacao realizada (ED-105). */
    public boolean hasAwaitingDestination() {
        return awaitingDestinationCount > 0;
    }

    /** Total gerado de um tipo de residuo numa unidade de quantidade (zero quando ausente). */
    public BigDecimal generated(String residueKind, String quantityUnit) {
        Objects.requireNonNull(residueKind, "residueKind e obrigatorio");
        Objects.requireNonNull(quantityUnit, "quantityUnit e obrigatoria");
        return generatedByKindAndUnit.getOrDefault(residueKind + "|" + quantityUnit, BigDecimal.ZERO);
    }
}
