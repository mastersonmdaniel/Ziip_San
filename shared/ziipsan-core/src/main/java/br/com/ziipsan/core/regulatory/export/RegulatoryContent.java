package br.com.ziipsan.core.regulatory.export;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Conteudo do pacote regulatorio de saida (ED-97, GES-04): as secoes
 * AGREGADAS do aceite. Sao numeros por categoria/faixa — nenhum registro
 * nominal mora aqui (a separacao agregado x nominal e de
 * {@link ExportPackage}).
 */
public final class RegulatoryContent {

    private RegulatoryContent() { }

    /** Secoes de conteudo nomeadas pelo aceite (GES-04). */
    public enum Section {
        CONSUMO_MEDIO_POR_CATEGORIA_E_FAIXA,
        RECEITA_E_USUARIOS_SUBSIDIADOS,
        CUSTO_DO_SUBSIDIO,
        CONTRATOS_DE_GRANDES_USUARIOS,
        TAXA_DE_INADIMPLENCIA_POR_CATEGORIA,
        RELATORIO_ANUAL_BENEFICIARIOS_TARIFA_SOCIAL
    }

    /** Natureza do custo do subsidio (Lei 11.445/2007, art. 31). */
    public enum SubsidyKind { TARIFARIO, FISCAL, INTERNO }

    public record CategoryConsumption(String category, String band, BigDecimal averageConsumptionM3,
                                      long activeEconomies) {
        public CategoryConsumption {
            Objects.requireNonNull(category); Objects.requireNonNull(band);
            Objects.requireNonNull(averageConsumptionM3);
            if (averageConsumptionM3.signum() < 0 || activeEconomies < 0)
                throw new IllegalArgumentException("consumo/economias negativos");
        }
    }

    public record SubsidyCost(SubsidyKind kind, BigDecimal amount, long subsidizedUsers,
                              BigDecimal subsidizedRevenue) {
        public SubsidyCost {
            Objects.requireNonNull(kind); Objects.requireNonNull(amount);
            Objects.requireNonNull(subsidizedRevenue);
            if (amount.signum() < 0 || subsidizedRevenue.signum() < 0 || subsidizedUsers < 0)
                throw new IllegalArgumentException("subsidio negativo");
        }
    }

    public record LargeUserContract(String contractRef, String category, BigDecimal contractedVolumeM3) {
        public LargeUserContract {
            Objects.requireNonNull(contractRef); Objects.requireNonNull(category);
            Objects.requireNonNull(contractedVolumeM3);
            if (contractRef.isBlank()) throw new IllegalArgumentException("contrato sem referencia");
        }
    }

    public record DelinquencyRate(String category, BigDecimal rate) {
        public DelinquencyRate {
            Objects.requireNonNull(category); Objects.requireNonNull(rate);
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("taxa de inadimplencia fora de [0,1]: " + rate);
        }
    }

    /** O pacote de conteudo agregado completo de um periodo. */
    public record ContentPack(List<CategoryConsumption> consumption, List<SubsidyCost> subsidies,
                              List<LargeUserContract> largeUsers, List<DelinquencyRate> delinquency,
                              int annualSocialBeneficiaries) {
        public ContentPack {
            consumption = consumption == null ? List.of() : List.copyOf(consumption);
            subsidies = subsidies == null ? List.of() : List.copyOf(subsidies);
            largeUsers = largeUsers == null ? List.of() : List.copyOf(largeUsers);
            delinquency = delinquency == null ? List.of() : List.copyOf(delinquency);
            if (annualSocialBeneficiaries < 0) throw new IllegalArgumentException("beneficiarios negativos");
        }
        /** Secoes presentes (para a comparacao com o leiaute do aceite). */
        public boolean covers(Section s) {
            return switch (s) {
                case CONSUMO_MEDIO_POR_CATEGORIA_E_FAIXA -> !consumption.isEmpty();
                case RECEITA_E_USUARIOS_SUBSIDIADOS, CUSTO_DO_SUBSIDIO -> !subsidies.isEmpty();
                case CONTRATOS_DE_GRANDES_USUARIOS -> !largeUsers.isEmpty();
                case TAXA_DE_INADIMPLENCIA_POR_CATEGORIA -> !delinquency.isEmpty();
                // Correcao D4 (REVISAO-ED-97): a secao anual so esta
                // coberta com beneficiarios > 0 — com >= 0 a condicao era
                // vacuamente verdadeira (o construtor ja impoe >= 0) e um
                // pacote vazio "cobria" a secao anual.
                case RELATORIO_ANUAL_BENEFICIARIOS_TARIFA_SOCIAL -> annualSocialBeneficiaries > 0;
            };
        }
    }
}
