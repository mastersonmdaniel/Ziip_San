package br.com.ziipsan.intelligence.losses.nr15;

import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta das contagens de medicao para os indicadores de cobertura da
 * NR 15 (ED-78): hidrometacao e macromedicao.
 *
 * <p>Fontes do aceite: o diagnostico (art. 5o) apresenta a
 * "incidencia da hidrometacao de agua, SINISA IAG1003" e o indicador
 * da NR 9 Nivel II-02, "Indice de macromedicao relativo ao volume
 * disponibilizado de agua"; o Plano acompanha a "evolucao da
 * hidrometacao e macromedicao" (art. 9o, III). As contagens brutas
 * (ligacoes com hidrometro, ligacoes ativas, volume macromedido e
 * volume disponibilizado) sao dos donos do dado: micromedicao/
 * cadastro e macromedicao da ED-17 sobre os pontos de medicao -
 * referenciados aqui por periodo, sem recadastro. A ED-78 calcula
 * apenas as RAZOES (indicadores), e razao sem as duas contagens e
 * AUSENTE, nunca estimada.
 *
 * <p><b>[SEM FONTE] nesta VM:</b> o texto do Glossario SINISA com a
 * formula oficial do IAG1003 e a ficha do II-02 da NR 9 nao foram
 * lidos aqui; as razoes implementadas sao o significado literal dos
 * nomes citados na NR 15 (parte/total x 100). A conferencia contra o
 * glossario vigente e a pendencia P-ED78-02 - se o glossario definir
 * denominador diverso, troca-se o fornecedor das contagens, nao o
 * desenho.
 */
public interface MeteringCoveragePort {

    /** Contagens brutas de um periodo (mes ou ano agregado pelo chamador). */
    record CoverageCounts(long meteredConnections, long activeConnections,
                          double macroMeasuredVolumeM3, double systemInputVolumeM3) {
        public CoverageCounts {
            if (meteredConnections < 0 || activeConnections < 0) {
                throw new IllegalArgumentException("contagens de ligacoes negativas");
            }
            if (meteredConnections > activeConnections) {
                throw new IllegalArgumentException("ligacoes hidrometradas (" + meteredConnections
                        + ") maiores que ligacoes ativas (" + activeConnections + ")");
            }
            if (macroMeasuredVolumeM3 < 0 || Double.isNaN(macroMeasuredVolumeM3)
                    || systemInputVolumeM3 < 0 || Double.isNaN(systemInputVolumeM3)) {
                throw new IllegalArgumentException("volumes de macromedicao invalidos");
            }
            if (macroMeasuredVolumeM3 > systemInputVolumeM3 && systemInputVolumeM3 > 0) {
                throw new IllegalArgumentException("volume macromedido maior que o disponibilizado");
            }
        }

        /** Incidencia da hidrometacao (SINISA IAG1003): hidrometradas/ativas x 100. */
        public Double hydroMeteringPercentOrNull() {
            return activeConnections == 0 ? null
                    : meteredConnections * 100.0 / activeConnections;
        }

        /** Indice de macromedicao (NR 9 II-02): macromedido/disponibilizado x 100. */
        public Double macroMeteringPercentOrNull() {
            return systemInputVolumeM3 <= 0 ? null
                    : macroMeasuredVolumeM3 * 100.0 / systemInputVolumeM3;
        }
    }

    /** Contagens do periodo, ou vazio quando a origem nao as tem. */
    Optional<CoverageCounts> countsOf(UUID tenantId, YearMonth period);

    /**
     * Contagens agregadas do ano civil (para a declaracao anual), ou
     * vazio. Metodo separado do mensal de proposito: hidrometacao e
     * estoque de ligacoes e macromedicao e fluxo do ano - quem agrega
     * e a fiacao, a partir das leituras mensais; sem agregado
     * declarado, os indices anuais ficam ausentes.
     */
    default Optional<CoverageCounts> annualCountsOf(UUID tenantId, int baseYear) {
        return Optional.empty();
    }

    /** Exige as contagens do periodo (uso do Plano: serie sem buraco silencioso). */
    default CoverageCounts requireCounts(UUID tenantId, YearMonth period) {
        Objects.requireNonNull(tenantId, "tenant das contagens obrigatorio");
        Objects.requireNonNull(period, "periodo das contagens obrigatorio");
        return countsOf(tenantId, period).orElseThrow(() -> new IllegalStateException(
                "contagens de hidrometacao/macromedicao ausentes para " + period
                        + " (ED-17/cadastro): o indicador fica ausente, nunca estimado"));
    }
}
