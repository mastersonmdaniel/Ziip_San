package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolucao da aliquota efectiva por precedencia de escopo (DESENHO-PA02
 * secao 3.3): SERVICE > CATEGORY > TARIFF > TENANT.
 *
 * <p>Regras fechadas: sem linha aplicavel = erro explicito (nunca zero
 * silencioso, no precedente de {@code TariffConfigurationException});
 * ambiguidade no mesmo nivel (duas linhas do mesmo escopo aplicaveis) = erro,
 * nunca ordem de lista.
 */
public final class ReliefRateResolver {

    /** Contexto de resolucao de um preco concreto. */
    public record ResolutionContext(String tariffRef, String categoryRef, String serviceRef) {
    }

    private ReliefRateResolver() {
    }

    /**
     * Resolve a aliquota para o contexto na data de referencia (a
     * {@code effectiveFrom} do programa, na geracao).
     *
     * @throws TariffConfigurationException sem linha aplicavel ou com ambiguidade
     */
    public static ReliefRate resolve(List<ReliefRate> rates, ResolutionContext context, LocalDate on) {
        Objects.requireNonNull(rates, "linhas de aliquota sao obrigatorias");
        Objects.requireNonNull(context, "contexto de resolucao e obrigatorio");
        Objects.requireNonNull(on, "data de resolucao e obrigatoria");

        List<ReliefRate> best = new ArrayList<>();
        int bestSpecificity = -1;
        for (ReliefRate rate : rates) {
            if (!rate.covers(on) || !applies(rate, context)) {
                continue;
            }
            int specificity = rate.scopeKind().specificity();
            if (specificity > bestSpecificity) {
                bestSpecificity = specificity;
                best.clear();
                best.add(rate);
            } else if (specificity == bestSpecificity) {
                best.add(rate);
            }
        }
        if (best.isEmpty()) {
            throw new TariffConfigurationException(
                    "Sem aliquota efectiva aplicavel ao contexto em " + on
                            + " (geracao recusada: aliquota ausente nunca e zero silencioso)");
        }
        if (best.size() > 1) {
            throw new TariffConfigurationException(
                    "Aliquota efectiva ambigua no escopo " + best.get(0).scopeKind()
                            + ": " + best.size() + " linhas aplicaveis em " + on);
        }
        return best.get(0);
    }

    private static boolean applies(ReliefRate rate, ResolutionContext context) {
        return switch (rate.scopeKind()) {
            case TENANT -> true;
            case TARIFF -> rate.matches(ReliefRate.ScopeKind.TARIFF, context.tariffRef());
            case CATEGORY -> rate.matches(ReliefRate.ScopeKind.CATEGORY, context.categoryRef());
            case SERVICE -> rate.matches(ReliefRate.ScopeKind.SERVICE, context.serviceRef());
        };
    }
}
