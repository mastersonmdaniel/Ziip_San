package br.com.ziipsan.commercial.billingrules.servicecatalog;

/**
 * Natureza da receita de um item de serviço (ED-34), Lei 11.445/2007, art. 10-A, II:
 * tarifária, complementar, acessória ou de projeto associado. As três últimas são as
 * "outras fontes de receita" do art. 10-A, sujeitas a compartilhamento com a modicidade
 * tarifária ({@code rev.sharing_pct}) e, quando exigido, a homologação regulatória
 * ({@code rev.homologation_required}).
 */
public enum RevenueNature {
    TARIFF,
    COMPLEMENTARY,
    ANCILLARY,
    ASSOCIATED_PROJECT;

    /** Receitas do art. 10-A (não tarifárias) exigem percentual de compartilhamento declarado. */
    public boolean requiresSharingPct() {
        return this != TARIFF;
    }
}
