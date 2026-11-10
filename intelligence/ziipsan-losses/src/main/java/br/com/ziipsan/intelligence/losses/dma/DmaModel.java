package br.com.ziipsan.intelligence.losses.dma;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modelo do II-6 — DMA (distrito de medicao e controlo agregado) sobre
 * a base viva GIS-03/ED-85. ADITIVO: nao altera nenhuma classe da
 * ED-85 (pacote {@code dmc}); agrega DMCs existentes.
 *
 * <p><b>Base (declarada):</b> o DMC continua a ser o
 * {@code DmcDistrict} da ED-85 — ancora em {@code dma_district}
 * (GIS-03, V2_2) + zona DMA de {@code gis_zone} (V13_2). Um DMA, aqui,
 * e um AGRUPAMENTO nomeado de codigos de DMC do mesmo setor: a
 * agregacao soma balancos de DMCs ja calculados, nao recadastra
 * distrito nenhum e nao duplica geometria.
 *
 * <p><b>Aritmetica:</b> todos os volumes sao {@link BigDecimal} e
 * todas as operacoes sao exactas (soma/subtracao/multiplicacao sem
 * arredondamento). Componente que a base nao expuser entra como
 * {@link Optional} vazio e sai DECLARADA ausente no resultado —
 * nunca estimada em silencio (regra da estimativa II-6 §2).
 */
public final class DmaModel {

    private DmaModel() {}

    /** Agrupamento DMA: codigo proprio + setor + codigos dos DMCs membros (da ED-85). */
    public record DmaDefinition(String dmaCode, String sectorCode, List<String> memberDmcCodes) {
        public DmaDefinition {
            if (dmaCode == null || dmaCode.isBlank())
                throw new IllegalArgumentException("codigo do DMA obrigatorio");
            if (sectorCode == null || sectorCode.isBlank())
                throw new IllegalArgumentException("setor do DMA obrigatorio (agregacao por setor, base ED-85)");
            Objects.requireNonNull(memberDmcCodes, "DMCs membros do DMA obrigatorios");
            memberDmcCodes = List.copyOf(memberDmcCodes);
            if (memberDmcCodes.isEmpty())
                throw new IllegalArgumentException("DMA sem nenhum DMC membro nao agrega nada");
            if (memberDmcCodes.stream().distinct().count() != memberDmcCodes.size())
                throw new IllegalArgumentException("DMC duplicado na definicao do DMA: " + memberDmcCodes);
        }
    }

    /**
     * Entradas do balanco de UM DMC num periodo arbitrario
     * [periodStart, periodEnd] (nao so mensal — diferenca declarada
     * face ao balanco mensal da ED-85). Volumes em m3, exactos.
     * Componentes obrigatorias: entrada do sistema, exportado em
     * bloco, faturado medido, faturado nao medido, autorizado nao
     * faturado. Componentes opcionais (a base pode nao expor):
     * perdas aparentes — sem elas a perda real fica AUSENTE.
     */
    public record DmcPeriodInputs(UUID dmcId, String dmcCode,
                                  LocalDate periodStart, LocalDate periodEnd,
                                  BigDecimal systemInputM3, BigDecimal exportedM3,
                                  BigDecimal billedMeteredM3, BigDecimal billedUnmeteredM3,
                                  BigDecimal authorizedUnbilledM3,
                                  Optional<BigDecimal> apparentLossesM3) {
        public DmcPeriodInputs {
            Objects.requireNonNull(dmcId, "DMC do balanco obrigatorio");
            if (dmcCode == null || dmcCode.isBlank())
                throw new IllegalArgumentException("codigo do DMC obrigatorio");
            Objects.requireNonNull(periodStart, "inicio do periodo obrigatorio");
            Objects.requireNonNull(periodEnd, "fim do periodo obrigatorio");
            if (periodEnd.isBefore(periodStart))
                throw new IllegalArgumentException("periodo invertido: " + periodStart + ".." + periodEnd);
            requireDeclared(systemInputM3, "volume de entrada macromedido");
            requireDeclared(exportedM3, "volume exportado em bloco medido");
            requireDeclared(billedMeteredM3, "volume faturado medido");
            requireDeclared(billedUnmeteredM3, "volume faturado nao medido");
            requireDeclared(authorizedUnbilledM3, "volume autorizado nao faturado");
            Objects.requireNonNull(apparentLossesM3, "perdas aparentes: Optional obrigatorio (vazio = ausente declarado)");
            apparentLossesM3.ifPresent(v -> requireNonNegative(v, "perdas aparentes"));
            if (exportedM3.compareTo(systemInputM3) > 0)
                throw new IllegalArgumentException("exportado em bloco maior que a entrada do sistema");
        }
        private static void requireDeclared(BigDecimal v, String what) {
            if (v == null) throw new IllegalArgumentException(
                    what + " nao declarado: o balanco exacto nao calcula com marcador (OPE-01, herdado da ED-85)");
            requireNonNegative(v, what);
        }
        private static void requireNonNegative(BigDecimal v, String what) {
            if (v.signum() < 0) throw new IllegalArgumentException(what + " negativo: " + v);
        }
    }

    /**
     * Resultado do balanco de um DMC no periodo. Identidades exactas:
     * netInput = systemInput - exported; billedTotal = billedMetered +
     * billedUnmetered; authorized = billedTotal + authorizedUnbilled;
     * totalLosses = netInput - authorized; nonRevenueWater =
     * netInput - billedTotal; realLosses = totalLosses - apparentLosses
     * QUANDO as aparentes foram declaradas (senao Optional vazio e o
     * nome da componente consta de {@code absentComponents}).
     */
    public record DmcPeriodBalance(UUID dmcId, String dmcCode,
                                   LocalDate periodStart, LocalDate periodEnd,
                                   BigDecimal netInputM3, BigDecimal billedTotalM3,
                                   BigDecimal authorizedConsumptionM3, BigDecimal totalLossesM3,
                                   Optional<BigDecimal> apparentLossesM3,
                                   Optional<BigDecimal> realLossesM3,
                                   BigDecimal nonRevenueWaterM3,
                                   List<String> absentComponents) {
        public DmcPeriodBalance { absentComponents = List.copyOf(absentComponents); }
    }

    /** Agregado do DMA: soma exacta dos balancos dos DMCs membros no MESMO periodo. */
    public record DmaPeriodBalance(String dmaCode, String sectorCode,
                                   LocalDate periodStart, LocalDate periodEnd,
                                   int memberCount,
                                   BigDecimal netInputM3, BigDecimal billedTotalM3,
                                   BigDecimal authorizedConsumptionM3, BigDecimal totalLossesM3,
                                   Optional<BigDecimal> apparentLossesM3,
                                   Optional<BigDecimal> realLossesM3,
                                   BigDecimal nonRevenueWaterM3,
                                   List<String> absentComponents) {
        public DmaPeriodBalance { absentComponents = List.copyOf(absentComponents); }
    }

    /** Amostra da serie de vazoes do DMA (L/h), com instante — alimentacao viva e integracao (P-II6-SERIES). */
    public record FlowSample(LocalDateTime instant, BigDecimal flowLitresPerHour) {
        public FlowSample {
            Objects.requireNonNull(instant, "instante da amostra obrigatorio");
            Objects.requireNonNull(flowLitresPerHour, "vazao da amostra obrigatoria");
            if (flowLitresPerHour.signum() < 0)
                throw new IllegalArgumentException("vazao negativa: " + flowLitresPerHour);
        }
    }
}
