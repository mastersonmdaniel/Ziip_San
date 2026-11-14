package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Politica de arredondamento da transformacao de desoneracao (DESENHO-PA02
 * secao 4.2, com a decisao do dono Q-D3 = B): a precisao da tabela publicada e
 * <b>parametro por tenant</b>.
 *
 * <p><b>Correccao R-06 (REVISAO-PA-02):</b> o DEFEITO da politica deriva da
 * <b>precisao da tabela de origem</b> — a escala dos precos da versao de
 * origem ({@link #tenantDefaultFor(TariffVersion)}) — e nao de uma constante
 * cega. O valor {@value #DEFAULT_SCALE} casas fica apenas como RECURSO para
 * quando a precisao da origem e indeterminavel (ex.: cadastro do programa
 * antes de existir versao de origem associada). Isto fecha a leitura da
 * NT 6.3.2 ("mantidas as casas decimais da tabela vigente") com Q-D3 = B:
 * defeito = precisao da origem; divergencia explicita continua recusada na
 * geracao ({@link #requireCompatibleWithSource(int)}).
 *
 * <p>A politica efectiva usada grava-se em cada ligacao
 * ({@code tariff_relief_links.rounding_policy}); nunca fica implicita.
 */
public record ReliefRoundingPolicy(int scale) {

    /** Recurso quando a precisao da origem e indeterminavel: 4 casas, a precisao interna vigente. */
    public static final int DEFAULT_SCALE = 4;

    public ReliefRoundingPolicy {
        if (scale < 0 || scale > 6) {
            throw new TariffConfigurationException(
                    "Casas decimais da tabela desonerada fora de [0, 6]: " + scale);
        }
    }

    /**
     * Defeito DERIVADO da tabela de origem (R-06): a escala dos precos da
     * versao de origem (precos por m3 das faixas e parcelas fixas das regras
     * de categoria). Se os precos da origem tiverem escalas distintas, vale a
     * MAIOR (a precisao mais fina da tabela — arredondar abaixo dela perderia
     * casas que a tabela vigente tem). Sem precos legiveis, cai no recurso
     * {@link #DEFAULT_SCALE}.
     */
    public static ReliefRoundingPolicy tenantDefaultFor(TariffVersion source) {
        if (source == null) {
            return tenantDefault();
        }
        TreeSet<Integer> scales = new TreeSet<>();
        source.rules().forEach(rule -> {
            if (rule.minCharge() != null) {
                scales.add(rule.minCharge().scale());
            }
            rule.blocks().forEach(block -> {
                if (block.pricePerM3() != null) {
                    scales.add(block.pricePerM3().scale());
                }
            });
        });
        if (scales.isEmpty()) {
            return tenantDefault();
        }
        return new ReliefRoundingPolicy(scales.last());
    }

    /** Recurso (origem indeterminavel): {@value #DEFAULT_SCALE} casas. Ver R-06. */
    public static ReliefRoundingPolicy tenantDefault() {
        return new ReliefRoundingPolicy(DEFAULT_SCALE);
    }

    /** Politica do tenant; {@code null} no cadastro cai no defeito declarado, nunca noutro valor. */
    public static ReliefRoundingPolicy ofTenant(Integer declaredScale) {
        return declaredScale == null ? tenantDefault() : new ReliefRoundingPolicy(declaredScale);
    }

    /**
     * Politica efectiva de um programa sobre uma origem: a declarada pelo
     * tenant, ou — nao havendo declaracao ({@code null}) — a derivada da
     * precisao da tabela de origem (R-06).
     */
    public static ReliefRoundingPolicy ofTenantOrSource(Integer declaredScale, TariffVersion source) {
        return declaredScale == null ? tenantDefaultFor(source) : new ReliefRoundingPolicy(declaredScale);
    }

    /** Arredonda um preco transformado na precisao da politica (HALF_UP, regime vigente). */
    public BigDecimal apply(BigDecimal value) {
        Objects.requireNonNull(value, "valor a arredondar e obrigatorio");
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * Porta de geracao (NT 6.3.2 x Q-D3 = B — ver ADENDO-FONTES-PRIMARIAS): a
     * NT fixa criterio uniforme definido no ato, <b>mantidas as casas decimais
     * da tabela vigente</b>. O parametro do tenant regista esse criterio; uma
     * politica cuja precisao divirja da precisao da tabela de origem e
     * recusada na geracao — parametrizacao livre que contradiga a NT nao se
     * implementa.
     *
     * @param sourceTableScale casas decimais da tabela vigente de origem
     *                         (no DDL vivo, {@code NUMERIC(14,4)} = 4)
     */
    public ReliefRoundingPolicy requireCompatibleWithSource(int sourceTableScale) {
        if (scale != sourceTableScale) {
            throw new TariffConfigurationException(
                    "Politica de arredondamento (" + scale + " casas) viola a NT 6.3.2: "
                            + "mantem-se o numero de casas decimais da tabela vigente ("
                            + sourceTableScale + ")");
        }
        return this;
    }

    /** Rotulo gravado na ligacao, ex.: {@code HALF_UP@4}. */
    public String label() {
        return "HALF_UP@" + scale;
    }
}
