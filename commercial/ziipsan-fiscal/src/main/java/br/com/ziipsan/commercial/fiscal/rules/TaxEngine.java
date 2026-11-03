package br.com.ziipsan.commercial.fiscal.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Motor tributário puro do porte Java do motor fiscal C# do Ziip Cloud (FISCAL-PORTE-JAVA §1,
 * §5). Puro de propósito: sem I/O, sem banco, sem relógio — os mesmos vetores rodam no JUnit e,
 * mais tarde, no emulium Android (ADR-0031).
 *
 * <p>Regras aplicadas aqui, todas tiradas do documento de porte:
 *
 * <ul>
 *   <li>Arredondamento <b>half-even</b> (HALF_EVEN), escala 2, para cada tributo (NT 007/2026
 *       apud o documento; a NT aplica half-even e tolerância de R$ 0,01 só a PIS e Cofins —
 *       para IBS e CBS, conferir NTs 004/008/009 e o manual da NFS-e antes da homologação).</li>
 *   <li>ISSQN, IRRF e CSRF como retenções sobre o valor bruto; IBS e CBS <b>informativos</b>
 *       em 2026 (não compõem o líquido nem são retidos aqui).</li>
 *   <li>O líquido do vetor de Florianópolis é {@code bruto − IRRF − CSRF}: o ISS é calculado
 *       e destacado, mas <b>não</b> é descontado do líquido nesse vetor (R$ 5.440,00 − R$ 81,60
 *       − R$ 252,96 = R$ 5.105,44; descontando o ISS o resultado seria R$ 4.996,64). Leitura
 *       adotada: ISS próprio, recolhido à parte pelo prestador, e não retido pelo tomador
 *       nesse exemplo. Se o C# tratar o ISS como retido em outro regime, o vetor — e não
 *       este motor — precisa ser corrigido primeiro (FISCAL-PORTE-JAVA §5: divergência
 *       bloqueia o merge).</li>
 * </ul>
 *
 * <p>O que este esqueleto <b>não</b> inventa: o documento de porte cita "Zona Franca" e
 * "Simples" como vetores do C# sem publicar os números. Não há preset com alíquota para
 * esses regimes aqui: quem chama passa as alíquotas explicitamente em {@link TaxRates}.
 * O preset com número publicado é só o padrão (Florianópolis + IBS/CBS informativos).
 * Os presets de Simples e Zona Franca entram quando os vetores numéricos do C# forem
 * anexados ao documento de porte (pendência registrada no relatório da frente).
 */
public final class TaxEngine {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private TaxEngine() {
    }

    /** Alíquotas de um cálculo, como frações (0.02 = 2%). Todas obrigatórias; zero desliga. */
    public record TaxRates(
            BigDecimal issRate,
            BigDecimal irrfRate,
            BigDecimal csrfRate,
            BigDecimal cbsRate,
            BigDecimal ibsRate) {
        public TaxRates {
            if (issRate == null || irrfRate == null || csrfRate == null
                    || cbsRate == null || ibsRate == null) {
                throw new IllegalArgumentException("Todas as aliquotas sao obrigatorias (use zero para desligar)");
            }
        }
    }

    /** Resultado de um cálculo, todos os valores com escala 2 e half-even. */
    public record TaxResult(
            BigDecimal gross,
            BigDecimal iss,
            BigDecimal irrf,
            BigDecimal csrf,
            BigDecimal cbsInformative,
            BigDecimal ibsInformative,
            BigDecimal net) {
    }

    /**
     * Preset com os números publicados no documento de porte: ISS 2% (faixa de Florianópolis
     * no vetor), IRRF 1,5%, CSRF 4,65%, CBS informativa 0,9% e IBS informativa 0,1% (2026).
     * As alíquotas de IBS/CBS de 2027 em diante vêm de {@code tax_rate} com vigência (R40,
     * ADR-0002) e nunca de constante — este preset é só o vetor de 2026.
     */
    public static TaxRates standard2026Rates() {
        return new TaxRates(
                new BigDecimal("0.02"),
                new BigDecimal("0.015"),
                new BigDecimal("0.0465"),
                new BigDecimal("0.009"),
                new BigDecimal("0.001"));
    }

    /** Calcula cada tributo sobre o bruto e o líquido como bruto − IRRF − CSRF (ver Javadoc). */
    public static TaxResult calculate(BigDecimal gross, TaxRates rates) {
        if (gross == null || gross.signum() < 0) {
            throw new IllegalArgumentException("O valor bruto e obrigatorio e nao pode ser negativo");
        }
        BigDecimal iss = tax(gross, rates.issRate());
        BigDecimal irrf = tax(gross, rates.irrfRate());
        BigDecimal csrf = tax(gross, rates.csrfRate());
        BigDecimal cbs = tax(gross, rates.cbsRate());
        BigDecimal ibs = tax(gross, rates.ibsRate());
        BigDecimal net = gross.subtract(irrf).subtract(csrf).setScale(SCALE, ROUNDING);
        return new TaxResult(
                gross.setScale(SCALE, ROUNDING), iss, irrf, csrf, cbs, ibs, net);
    }

    /** Arredondamento half-even isolado, para o vetor 0,125 → 0,12 e 0,135 → 0,14. */
    public static BigDecimal roundHalfEven(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("O valor e obrigatorio");
        }
        return value.setScale(SCALE, ROUNDING);
    }

    private static BigDecimal tax(BigDecimal gross, BigDecimal rate) {
        return gross.multiply(rate).setScale(SCALE, ROUNDING);
    }
}
