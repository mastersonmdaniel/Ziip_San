package br.com.ziipsan.commercial.billingrules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calculo da conta de campo (ED-19) — A aritmetica da emissao simultanea,
 * identica no servidor e no aparelho porque os dois executam ESTE codigo do
 * MESMO artefato {@code ziipsan-billing-rules}.
 *
 * <h2>Regra (deterministica, sem double)</h2>
 * <ol>
 *   <li>Consumo medido: {@link ConsumptionRule} (diferenca, com virada do
 *       mostrador).</li>
 *   <li>Consumo faturado: o medido, no minimo {@code minimo x economias}.</li>
 *   <li>Parcela fixa: {@code fixa por economia x economias} (cobre ate o
 *       minimo por economia).</li>
 *   <li>Agua variavel: excedente por economia =
 *       {@code (faturado DIV economias) - minimo} (divisao inteira truncada:
 *       o resto nao e cobrado, a mesma semantica DIV do motor da P2.5);
 *       o excedente atravessa as faixas progressivas (largura por economia)
 *       e o valor por economia e multiplicado pelas economias.</li>
 *   <li>Esgoto: {@code (agua variavel + parcela fixa) x percentual / 100},
 *       HALF_UP 2 casas, quando o retrato fatura esgoto.</li>
 *   <li>Debitos somam; creditos abatem ate o total das cobrancas
 *       (fixa + agua + esgoto + debitos); o excedente NAO abate e volta
 *       declarado em {@code creditsNotApplied}.</li>
 *   <li>Arredondamento: cada componente monetario e fechado em 2 casas
 *       HALF_UP uma unica vez; o total e a soma exata dos componentes ja
 *       arredondados — a conta impressa e a recalculada fecham no centavo.</li>
 * </ol>
 *
 * <p>Fronteira declarada: este modulo NAO substitui o motor de tarifa da
 * P2.5 no faturamento de escritorio. Ele e o contrato da emissao simultanea:
 * as faixas, a fixa e o percentual chegam resolvidos da versao de tarifa da
 * P2.5 no retrato da tarefa, e e com este modulo — nos dois lados — que a
 * conta de campo nasce e e recalculada.
 */
public final class FieldBillCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private FieldBillCalculator() {
    }

    public static FieldBillResult calculate(FieldBillInput in) {
        Objects.requireNonNull(in, "entrada do calculo de campo e obrigatoria");
        FieldTariffSnapshot s = in.snapshot();

        int consumption = ConsumptionRule.consumption(
                in.previousReading(), in.currentReading(), in.meterDigits());
        int minimumTotal = s.minimumM3PerEconomy() * in.economies();
        int billed = Math.max(consumption, minimumTotal);

        BigDecimal fixed = money(s.fixedChargePerEconomy()
                .multiply(BigDecimal.valueOf(in.economies())));

        int perEconomy = billed / in.economies();
        int excessPerEconomy = Math.max(0, perEconomy - s.minimumM3PerEconomy());
        BigDecimal variablePerEconomy = BigDecimal.ZERO;
        int remaining = excessPerEconomy;
        for (TariffBracket bracket : s.brackets()) {
            if (remaining <= 0) {
                break;
            }
            int inBracket = bracket.isOpen() ? remaining : Math.min(remaining, bracket.widthM3());
            variablePerEconomy = variablePerEconomy.add(
                    bracket.pricePerM3().multiply(BigDecimal.valueOf(inBracket)));
            remaining -= inBracket;
        }
        BigDecimal water = money(variablePerEconomy.multiply(BigDecimal.valueOf(in.economies())));

        BigDecimal sewer = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (s.sewerEnabled()) {
            sewer = money(water.add(fixed).multiply(s.sewerPct()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        }

        BigDecimal debits = BigDecimal.ZERO;
        for (AmountLine line : in.debits()) {
            debits = debits.add(line.amount());
        }
        debits = money(debits);

        BigDecimal charges = fixed.add(water).add(sewer).add(debits);
        BigDecimal creditsRequested = BigDecimal.ZERO;
        for (AmountLine line : in.credits()) {
            creditsRequested = creditsRequested.add(line.amount());
        }
        creditsRequested = money(creditsRequested);
        BigDecimal creditsApplied = creditsRequested.min(charges);
        BigDecimal creditsNotApplied = creditsRequested.subtract(creditsApplied);
        BigDecimal total = charges.subtract(creditsApplied);

        FieldBillResult unsigned = new FieldBillResult(in.taskId(), consumption, billed,
                water, fixed, sewer, debits, creditsApplied, creditsNotApplied, total, "PENDING");
        String fingerprint = FieldBillFingerprint.of(in, unsigned);
        return new FieldBillResult(in.taskId(), consumption, billed, water, fixed, sewer,
                debits, creditsApplied, creditsNotApplied, total, fingerprint);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
