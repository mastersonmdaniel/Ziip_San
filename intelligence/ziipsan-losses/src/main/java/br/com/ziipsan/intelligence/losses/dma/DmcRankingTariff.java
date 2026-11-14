package br.com.ziipsan.intelligence.losses.dma;

import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.Entry;
import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.Ranking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aplicacao do ranking de DMC por retorno a um tenant, resolvendo a
 * tarifa pela {@link DmcRankingTariffPort} (P-II6-TARIFA).
 *
 * <p>Sem porta, ou com a porta a devolver vazio (fonte inexistente —
 * {@code [A DEFINIR]}, ver a javadoc da porta), o ranking e o do
 * motor com tarifa vazia: modo {@code LOSS_VOLUME_ONLY}
 * <b>declarado</b> no resultado, indice = volume de perdas
 * recuperavel. Nenhum valor por omissao, nenhum fallback numerico,
 * nenhuma tarifa presumida entra por esta classe: ela so encaminha
 * para o motor o que a fonte der — e a validacao do motor (tarifa
 * declarada tem de ser positiva) aplica-se ao que vier da fonte,
 * sem correccao silenciosa.
 */
public final class DmcRankingTariff {

    private DmcRankingTariff() {
    }

    /**
     * @param entries DMCs com os volumes do balanco do periodo
     * @param tenantId tenant dono do ranking (a tarifa e do tenant,
     *                 nunca global presumida)
     * @param tariffPortOrNull fonte da tarifa do tenant, ou
     *        {@code null} enquanto nao existir fonte real
     *        ({@code [A DEFINIR]} — pendencia P-PDCAGIS4-03)
     */
    public static Ranking rankForTenant(List<Entry> entries, UUID tenantId,
                                        DmcRankingTariffPort tariffPortOrNull) {
        Objects.requireNonNull(entries, "entradas do ranking obrigatorias");
        Objects.requireNonNull(tenantId, "tenant do ranking obrigatorio (a tarifa e do tenant)");
        Optional<BigDecimal> tariff = tariffPortOrNull == null
                ? Optional.empty()
                : Objects.requireNonNull(tariffPortOrNull.tariffPerM3(tenantId),
                        "a fonte de tarifa devolveu null (vazio = [A DEFINIR], nunca null)");
        return DmcReturnRanking.rank(entries, tariff);
    }
}
