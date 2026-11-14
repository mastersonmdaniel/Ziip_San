package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.ziipsan.commercial.billing.tariff.TariffSchedule;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolucao por DATA DE EMISSAO (DESENHO-PA02 secao 5.2; F-08 CONFIRMADO
 * INTEGRALMENTE pela NT 6.7.2/6.7.3): fatura emitida a partir do corte usa a
 * versao desonerada para a TOTALIDADE dos volumes, ainda que o periodo de
 * utilizacao seja anterior — sem apuramento proporcional AO CORTE. Fatura
 * emitida antes do corte usa a resolucao vigente intacta (incluindo rateio
 * pre-corte, se houver).
 *
 * <p><b>Correccao R-01 (REVISAO-PA-02, GRAVE) — periodos multi-versao:</b> o
 * "sem rateio" da NT proibe repartir o periodo PELO CORTE entre tabela
 * antiga e desonerada; nao elimina as fatias que ja existem por FRONTEIRA
 * DE VERSAO TARIFARIA (ex.: reajuste anual com {@code valid_from} no meio
 * do periodo). A resolucao compoe FATIA A FATIA: cada fatia do schedule de
 * origem mapeia para a SUA desonerada ligada (gerada daquela versao — ver
 * {@code RelievedVersionGenerator.generateForAllSources}); o motor recebe
 * um schedule com uma fatia desonerada por fatia de origem, com os precos
 * certos de cada versao, e o rateio por dias entre fatias continua a ser o
 * do motor (e rateio por fronteira de versao, nao pelo corte).
 *
 * <ul>
 *   <li>Todas as fatias com ligacao ⇒ desonerado fatia a fatia
 *       ({@code relievedApplied = true}) — nunca bloqueia.</li>
 *   <li>Alguma fatia SEM ligacao ⇒ o conjunto NAO esta desonerado:
 *       devolve o schedule vigente intacto com
 *       {@code missingSourceVersionIds} preenchido e deixa a GUARDA decidir
 *       (falha fechada ou via provisoria marcada). NUNCA substitui precos
 *       de outra versao em silencio (era o defeito CE2 do revisor) e nunca
 *       aplica a desonerada de uma so fatia a totalidade do periodo.</li>
 * </ul>
 *
 * <p>Vive na camada de montagem do schedule: o {@code TariffEngine} nao muda.
 *
 * <p><b>Q-D1 = C (decisao do dono; a NT e SILENTE sobre retificacoes):</b> a
 * retificacao/substituicao mantem SEMPRE a tabela original da fatura
 * retificada, sem excepcao por motivo — incluindo motivo tipo 02 (erro de
 * preco/tarifa). O provisorio do desenho (original excepto tipo 02) esta
 * MORTO e nao se implementa.
 */
public final class EmissionDateResolver {

    private EmissionDateResolver() {
    }

    /**
     * Resultado da resolucao para uma emissao.
     *
     * @param sourceVersionIds        versoes de origem das fatias do periodo
     *                                (vazio quando a resolucao nao se aplica)
     * @param missingSourceVersionIds fatias de origem SEM ligacao desonerada;
     *                                nao-vazio ⇒ {@code relievedApplied = false}
     *                                e a guarda decide o destino da emissao
     */
    public record Resolution(TariffSchedule schedule, boolean relievedApplied,
                             List<UUID> sourceVersionIds, List<UUID> missingSourceVersionIds) {
        public Resolution {
            sourceVersionIds = sourceVersionIds == null ? List.of() : List.copyOf(sourceVersionIds);
            missingSourceVersionIds = missingSourceVersionIds == null
                    ? List.of() : List.copyOf(missingSourceVersionIds);
        }

        /** Primeira versao de origem (conveniencia para periodos de versao unica). */
        public UUID sourceVersionId() {
            return sourceVersionIds.isEmpty() ? null : sourceVersionIds.get(0);
        }
    }

    /**
     * Resolve o schedule efectivo de uma emissao, fatia a fatia.
     *
     * @param periodSchedule schedule resolvido pela vigencia do periodo (via vigente)
     * @param periodFrom     inicio do periodo de leitura
     * @param periodTo       fim do periodo de leitura
     * @param issuedOn       data de emissao da fatura (o corte e por ela)
     * @param program        programa aplicavel (ver {@link ReliefProgramSelector}),
     *                       ou {@code null} se nao houver
     * @param links          ligacoes do programa (origem -> desonerada + versoes geradas)
     * @param relievedById   versoes desoneradas geradas, por id
     */
    public static Resolution resolveForEmission(TariffSchedule periodSchedule, LocalDate periodFrom,
                                                LocalDate periodTo, LocalDate issuedOn,
                                                ReliefProgram program, List<ReliefLink> links,
                                                Map<UUID, TariffVersion> relievedById) {
        Objects.requireNonNull(periodSchedule, "schedule do periodo e obrigatorio");
        Objects.requireNonNull(periodFrom, "inicio do periodo e obrigatorio");
        Objects.requireNonNull(periodTo, "fim do periodo e obrigatorio");
        Objects.requireNonNull(issuedOn, "data de emissao e obrigatoria");
        if (program == null || !program.status().engagesEmissionGuard()
                || issuedOn.isBefore(program.effectiveFrom())) {
            return new Resolution(periodSchedule, false, List.of(), List.of());
        }
        Objects.requireNonNull(links, "ligacoes do programa sao obrigatorias");
        Objects.requireNonNull(relievedById, "versoes desoneradas sao obrigatorias");

        List<TariffSchedule.PeriodSlice> slices = periodSchedule.slices(periodFrom, periodTo);
        List<UUID> sourceIds = new ArrayList<>();
        List<UUID> missing = new ArrayList<>();
        List<TariffVersion> relievedSlices = new ArrayList<>();
        for (TariffSchedule.PeriodSlice slice : slices) {
            TariffVersion source = slice.version();
            sourceIds.add(source.id());
            ReliefLink link = links.stream()
                    .filter(l -> l.programId().equals(program.id())
                            && l.targetKind() == ReliefLink.TargetKind.TARIFF_VERSION
                            && l.sourceVersionId().equals(source.id()))
                    .findFirst()
                    .orElse(null);
            if (link == null) {
                missing.add(source.id());
                continue;
            }
            TariffVersion relieved = relievedById.get(link.relievedVersionId());
            if (relieved == null) {
                throw new TariffConfigurationException(
                        "Ligacao de desoneracao sem a versao gerada " + link.relievedVersionId());
            }
            // Fotografia da fatia de origem com os precos desonerados DESTA
            // versao: cobertura = a fatia [from, to] da origem, precos = os
            // da desonerada ligada a esta origem.
            relievedSlices.add(new TariffVersion(relieved.id(), slice.from(), slice.to(),
                    relieved.rules(), relieved.socialRule()));
        }
        if (!missing.isEmpty()) {
            // Conjunto nao desonerado: schedule vigente intacto; a GUARDA
            // decide (falha fechada ou via provisoria) — nunca precos de
            // outra versao em silencio, nunca desonerada parcial.
            return new Resolution(periodSchedule, false, sourceIds, missing);
        }
        return new Resolution(new TariffSchedule(relievedSlices), true, sourceIds, List.of());
    }

    /**
     * Q-D1 = C: a retificacao usa SEMPRE a versao original da fatura
     * retificada — qualquer que seja a data da retificacao e qualquer que
     * seja o motivo (incluindo tipo 02, erro de preco/tarifa).
     *
     * @param originalVersion versao gravada na fatura original (snapshot)
     * @param substitutionTypeCode motivo da substituicao — lido e
     *        deliberadamente SEM efeito na escolha da tabela
     */
    public static TariffVersion resolveForRectification(TariffVersion originalVersion,
                                                        String substitutionTypeCode) {
        Objects.requireNonNull(originalVersion, "versao original da fatura e obrigatoria");
        // substitutionTypeCode nao participa na decisao: Q-D1 = C nao tem
        // excepcao por motivo. O parametro existe para a chamada ser auditavel.
        return originalVersion;
    }
}
