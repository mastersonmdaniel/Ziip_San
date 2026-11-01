package br.com.ziipsan.commercial.collection.surcharge;

import java.math.BigDecimal;

/**
 * Juros de mora. Não há valor padrão: o tenant declara {@link #none()} ou os parâmetros do
 * contrato/regulador.
 *
 * <p><b>Só existe um modo de acúmulo: juros simples pro rata die</b>, isto é,
 * {@code principal x (taxa mensal / 100) x dias corridos / base de dias do mês}, sem
 * capitalização (sem juros sobre juros). O modo do GSAN (taxa mensal multiplicada pela diferença
 * de <i>meses de calendário</i>, com 0 juros se a diferença for menor que 1) <b>não foi
 * implementado</b> por ser uma decisão de negócio que o dono ainda não tomou (relatório 11,
 * R18): ele cobraria 1 mês cheio de uma só vez ao virar o mês e nada nos dias anteriores.
 */
public sealed interface InterestRule {

    /** Sem juros de mora (declaração explícita). */
    record None() implements InterestRule {
    }

    /**
     * @param monthlyPercent taxa mensal em percentual (ex.: {@code 1.00} é 1% ao mês)
     * @param monthBasisDays dias que formam o mês na proporção diária (convenção do contrato,
     *                       normalmente 30)
     * @param capPercent     teto dos juros em percentual do principal, ou {@code null} sem teto
     */
    record SimplePerDay(BigDecimal monthlyPercent, int monthBasisDays, BigDecimal capPercent)
            implements InterestRule {
        public SimplePerDay {
            if (monthlyPercent == null || monthlyPercent.signum() < 0) {
                throw new IllegalArgumentException("A taxa mensal de juros é obrigatória e não pode ser negativa");
            }
            if (monthBasisDays < 1 || monthBasisDays > 31) {
                throw new IllegalArgumentException("A base de dias do mês deve estar entre 1 e 31");
            }
            if (capPercent != null && capPercent.signum() < 0) {
                throw new IllegalArgumentException("Teto de juros negativo");
            }
        }
    }

    static InterestRule none() {
        return new None();
    }

    static InterestRule simplePerDay(BigDecimal monthlyPercent, int monthBasisDays, BigDecimal capPercent) {
        return new SimplePerDay(monthlyPercent, monthBasisDays, capPercent);
    }
}
