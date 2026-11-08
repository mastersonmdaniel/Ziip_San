package br.com.ziipsan.intelligence.losses.nr15;

import br.com.ziipsan.intelligence.losses.dmc.WaterBalanceCalculator;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Montagem do balanco padronizado anual da NR 15 (ED-78) a partir das
 * fotos por DMC da ED-85: SOMAS, e nada alem de somas sobre os
 * componentes derivados que a ED-85 ja calculou e gravou.
 *
 * <p>Regras de montagem:
 * <ul>
 *   <li><b>Completude do periodo (Anexo I, obs. 4):</b> o periodo de
 *       referencia e de 12 meses, janeiro a dezembro. Para CADA DMC
 *       presente, as 12 fotos mensais do ano sao exigidas; mes
 *       faltante recusa nomeando DMC e mes - balanco anual parcial
 *       nao vira declaracao.</li>
 *   <li><b>Foto duplicada</b> (mesmo DMC e mes) recusa: somar duas
 *       vezes seria um balanco errado silencioso.</li>
 *   <li><b>Perdas aparentes/reais:</b> soma anulavel - um mes sem o
 *       componente torna o ano sem o componente (a ED-85 so conhece
 *       a perda real quando as aparentes sao declaradas).</li>
 *   <li><b>Percentual de agua nao faturada:</b> razao anual
 *       ANF/entrada liquida x 100 - a mesma definicao que a ED-85
 *       aplica por DMC, agora sobre os totais somados (razao de
 *       indicador, nao recomposicao de balanco). <b>Convencao
 *       herdada (correcao ED-78-CORR-1, ressalva R1 da revisao):</b>
 *       com entrada liquida somada &lt;= 0, o percentual e 0,0 - e
 *       NAO null -, exatamente como o {@code nrwPct} da ED-85 faz
 *       por DMC. A convencao NAO foi alterada nesta entrega:
 *       divergir aqui separaria a saida da NR 15 do produtor do
 *       numero. A decisao sobre o caso e CONJUNTA com a ED-85 e
 *       esta registrada na pendencia P-ED78-08.</li>
 *   <li><b>Volume produzido:</b> lido da P4.2 pela porta; ausente =
 *       recusa. A divergencia contra a entrada somada e GRAVADA no
 *       balanco (nunca ajustada).</li>
 * </ul>
 */
public final class Nr15BalanceAssembler {

    private Nr15BalanceAssembler() {
    }

    public static StandardizedWaterBalance assemble(UUID tenantId, int baseYear,
                                                      List<DmcBalanceSnapshot> snapshots,
                                                      ProducedVolumePort producedVolume) {
        Objects.requireNonNull(tenantId, "tenant do balanco padronizado obrigatorio");
        Objects.requireNonNull(snapshots, "fotos da ED-85 obrigatorias");
        Objects.requireNonNull(producedVolume, "porta do volume produzido da P4.2 obrigatoria");
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("balanco padronizado sem nenhuma foto da ED-85 no ano " + baseYear);
        }
        Set<UUID> dmcs = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (DmcBalanceSnapshot snapshot : snapshots) {
            if (snapshot.period().getYear() != baseYear) {
                throw new IllegalArgumentException("foto da ED-85 fora do ano-base " + baseYear
                        + ": " + snapshot.period());
            }
            String key = snapshot.dmcId() + "@" + snapshot.period();
            if (!seen.add(key)) {
                throw new IllegalArgumentException("foto duplicada da ED-85 (DMC " + snapshot.dmcId()
                        + ", " + snapshot.period() + "): a soma contaria o mes em dobro");
            }
            dmcs.add(snapshot.dmcId());
        }
        for (UUID dmc : dmcs) {
            for (int month = 1; month <= 12; month++) {
                YearMonth expected = YearMonth.of(baseYear, month);
                if (!seen.contains(dmc + "@" + expected)) {
                    throw new IllegalArgumentException("balanco anual incompleto: DMC " + dmc
                            + " sem foto da ED-85 em " + expected
                            + " (Anexo I da NR 15: periodo de 12 meses, janeiro a dezembro)");
                }
            }
        }
        double produced = producedVolume.producedVolumeM3(tenantId, baseYear)
                .orElseThrow(() -> new IllegalStateException("NR15_PRODUCED_VOLUME_MISSING: volume produzido"
                        + " do ano " + baseYear + " nao lido da P4.2 (variavel "
                        + ProducedVolumePort.SINISA_VARIABLE_KEY + "); a declaracao nao usa zero"
                        + " nem substitui pela soma da ED-85 em silencio"));
        if (produced < 0 || Double.isNaN(produced)) {
            throw new IllegalArgumentException("volume produzido da P4.2 invalido: " + produced);
        }
        double systemInput = sum(snapshots, s -> s.systemInputM3());
        double netInput = sum(snapshots, s -> s.netInputM3());
        double nonRevenue = sum(snapshots, s -> s.nonRevenueWaterM3());
        double nonRevenuePercent = netInput <= 0 ? 0.0 : nonRevenue / netInput * 100.0;
        WaterBalanceCalculator.ConfidenceGrade weakest = snapshots.stream()
                .map(DmcBalanceSnapshot::confidence)
                .min(java.util.Comparator.comparingInt(Nr15BalanceAssembler::confidenceRank))
                .orElseThrow();
        return new StandardizedWaterBalance(
                tenantId, baseYear, dmcs.size(), snapshots.size(),
                systemInput,
                sum(snapshots, s -> s.exportedM3()),
                netInput,
                sum(snapshots, s -> s.billedMeteredAdjustedM3()),
                sum(snapshots, s -> s.billedUnmeteredM3()),
                sum(snapshots, s -> s.billedTotalM3()),
                sum(snapshots, s -> s.authorizedUnbilledM3()),
                sum(snapshots, s -> s.authorizedConsumptionM3()),
                sum(snapshots, s -> s.totalLossesM3()),
                sumNullable(snapshots, true),
                sumNullable(snapshots, false),
                nonRevenue,
                nonRevenuePercent,
                produced,
                produced - systemInput,
                weakest);
    }

    private static int confidenceRank(WaterBalanceCalculator.ConfidenceGrade grade) {
        return switch (grade) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
    }

    private interface Extractor {
        double get(DmcBalanceSnapshot snapshot);
    }

    private static double sum(List<DmcBalanceSnapshot> snapshots, Extractor extractor) {
        return snapshots.stream().mapToDouble(extractor::get).sum();
    }

    private static Double sumNullable(List<DmcBalanceSnapshot> snapshots, boolean apparent) {
        double total = 0.0;
        for (DmcBalanceSnapshot snapshot : snapshots) {
            Double value = apparent ? snapshot.apparentLossesM3OrNull() : snapshot.realLossesM3OrNull();
            if (value == null) return null;
            total += value;
        }
        return total;
    }
}
