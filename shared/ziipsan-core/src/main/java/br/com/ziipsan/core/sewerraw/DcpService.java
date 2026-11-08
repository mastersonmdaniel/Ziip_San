package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.effluent.BodEfficiency;
import br.com.ziipsan.core.effluent.EffluentCatalog;
import br.com.ziipsan.core.effluent.EffluentStandard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Geracao da Declaracao de Carga Poluidora a partir do
 * automonitoramento da ED-105, SEM duplicar nada dela: o catalogo
 * de padrao, os resultados e a eficiencia de DBO chegam como
 * classes reais da ED-105.
 *
 * <p><b>Portao da base normativa (aceite, literal):</b> a DCP sai
 * "com a base normativa conferida no catalogo depois da publicacao
 * da norma que substitui a CONAMA 430/2011, em revisao". Logo:
 * <ul>
 *   <li>se a confirmacao declarada de publicacao da norma
 *       sucessora conferida no catalogo nao vier ({@code false}),
 *       a geracao RECUSA — nenhuma DCP nasce sobre base em revisao
 *       nao publicada [SEM FONTE enquanto nao publicada];</li>
 *   <li>cada parametro declarado precisa de padrao REVISADO vigente
 *       no catalogo da ED-105 no fim do periodo — parametro sem
 *       padrao revisado no catalogo recusa a geracao inteira (a
 *       referencia normativa da linha e a do catalogo, nao uma
 *       afirmacao desta classe).</li>
 * </ul>
 */
public final class DcpService {

    /** Resultado medio de um parametro no periodo, como reportado pelo automonitoramento da ED-105. */
    public record MonitoringMean(String parameterKey, BigDecimal meanConcentration, String concentrationUnit) {
        public MonitoringMean {
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parametro do automonitoramento e obrigatorio");
            if (meanConcentration == null || meanConcentration.signum() < 0)
                throw new IllegalArgumentException("concentracao media do automonitoramento invalida para " + parameterKey);
            if (concentrationUnit == null || concentrationUnit.isBlank())
                throw new IllegalArgumentException("unidade da concentracao e obrigatoria para " + parameterKey);
        }
    }

    private DcpService() {}

    public static PollutantLoadDeclaration generate(
            UUID tenantId, UUID wastewaterUnitId, LocalDate periodStart, LocalDate periodEnd,
            EffluentCatalog catalog, EffluentStandard.Jurisdiction jurisdiction, String stateCode,
            List<MonitoringMean> means, BigDecimal effluentVolumeM3, BodEfficiency.Result bod,
            boolean successorBasisConfirmedInCatalog, String declarantRef, LocalDate declaredOn) {
        if (tenantId == null || wastewaterUnitId == null)
            throw new IllegalArgumentException("tenant e ETE sao obrigatorios na DCP");
        if (catalog == null) throw new IllegalArgumentException("catalogo de efluente da ED-105 e obrigatorio");
        if (jurisdiction == null) throw new IllegalArgumentException("esfera (federal/estadual) declarada e obrigatoria");
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart))
            throw new IllegalArgumentException("periodo da DCP invalido");
        if (!successorBasisConfirmedInCatalog)
            throw new IllegalStateException("DCP recusada: a base normativa vigente depende da publicacao"
                    + " da norma que substitui a CONAMA 430/2011 (em revisao); a confirmacao de publicacao"
                    + " conferida no catalogo da ED-105 nao foi declarada [SEM FONTE ate a publicacao]");
        if (means == null || means.isEmpty())
            throw new IllegalArgumentException("DCP exige resultados do automonitoramento da ED-105: sem eles nao ha carga a declarar");
        if (effluentVolumeM3 == null || effluentVolumeM3.signum() <= 0)
            throw new IllegalArgumentException("volume efluente do periodo (medido/declarado da operacao) deve ser maior que zero");
        if (bod == null) throw new IllegalArgumentException("eficiencia de DBO da ED-105 do periodo e obrigatoria na DCP");
        if (!bod.wastewaterUnitId().equals(wastewaterUnitId))
            throw new IllegalArgumentException("eficiencia de DBO de outra ETE nao entra nesta DCP");
        if (bod.periodStart().isBefore(periodStart) || bod.periodEnd().isAfter(periodEnd))
            throw new IllegalArgumentException("periodo da eficiencia de DBO fora do periodo da DCP");

        List<PollutantLoadDeclaration.LoadLine> lines = new ArrayList<>();
        List<String> basisRefs = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (MonitoringMean mean : means) {
            EffluentStandard standard = catalog.reviewedStandardOn(
                    mean.parameterKey(), jurisdiction, stateCode, periodEnd)
                    .orElseThrow(() -> new IllegalStateException("DCP recusada: parametro " + mean.parameterKey()
                            + " sem padrao revisado vigente no catalogo " + catalog.version()
                            + " da ED-105 na esfera declarada — a referencia normativa sai do catalogo, nao se presume"));
            BigDecimal load = mean.meanConcentration().multiply(effluentVolumeM3)
                    .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
            lines.add(new PollutantLoadDeclaration.LoadLine(mean.parameterKey(), mean.meanConcentration(),
                    mean.concentrationUnit(), load, standard.normReference()));
            if (!basisRefs.contains(standard.normReference())) basisRefs.add(standard.normReference());
            total = total.add(load);
        }
        return new PollutantLoadDeclaration(UUID.randomUUID(), tenantId, wastewaterUnitId,
                periodStart, periodEnd, catalog.version(), basisRefs, true,
                effluentVolumeM3, bod.pairCount(), bod.removalPercent(), lines,
                total.setScale(3, RoundingMode.HALF_UP), declarantRef, declaredOn);
    }
}
