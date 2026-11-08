package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Fachada da politica comercial da ED-10: compoe planilha, fotografia
 * comprovavel de ligacoes (P2.12), tetos de edital, reversao (ADR-0024)
 * e repasse de uso (ED-41/P2.12) numa avaliacao mensal por tenant.
 * Nenhum caminho desta classe bloqueia ou altera contrato: ela CALCULA
 * e COMPARA - a cobranca do SaaS em si e da P2.12.
 *
 * <p>OBSERVACAO (proposta tarifaria, DECISOES item 32): esta entrega
 * NAO executa a proposta de registro unico de cobrancas - ela nao toca
 * o calculo da conta do morador (BillCalculator/TariffEngine); trata
 * exclusivamente do preco do SaaS cobrado do tenant/cliente.
 */
public final class CommercialPolicyService {

    public record MonthlyEvaluation(UUID tenantId, YearMonth competence, String bandCode,
                                    PriceCalculator.PricedBand priced,
                                    ReferenceCeilings.CeilingComparison visan,
                                    ReferenceCeilings.CeilingComparison samae,
                                    BigDecimal effectivePrice,
                                    UsagePassThrough.PassThroughStatement passThrough) {}

    /**
     * @param reversionCompetence competencia da reversao, se ja ocorrida (ADR-0024); nula = sem reversao
     */
    public MonthlyEvaluation evaluate(PriceSheet sheet, SaasPricingParameters params,
                                      ActiveConnectionSnapshot snapshot, int poolTenants,
                                      YearMonth reversionCompetence, List<UsagePassThrough.UsageLine> usageLines) {
        if (sheet == null || snapshot == null) throw new IllegalArgumentException("planilha e fotografia sao obrigatorias");
        if (!sheet.validOn(snapshot.competence().atDay(1)))
            throw new IllegalArgumentException("planilha " + sheet.version() + " fora da vigencia na competencia " + snapshot.competence());
        long connections = snapshot.verifiedConnections();
        PriceCalculator.PricedBand priced = PriceCalculator.price(sheet, params, connections, poolTenants);
        BigDecimal effective = ReversionDiscountRule.priceForCompetence(
                priced.monthlyPrice(), params, snapshot.competence(), reversionCompetence);
        UsagePassThrough.PassThroughStatement pass = usageLines == null ? null
                : UsagePassThrough.statement(snapshot.tenantId(), snapshot.competence(), usageLines, params);
        return new MonthlyEvaluation(snapshot.tenantId(), snapshot.competence(), priced.band().code(), priced,
                ReferenceCeilings.visan(effective), ReferenceCeilings.samae(effective), effective, pass);
    }
}
