package br.com.ziipsan.commercial.collection.dunning.negativation;

import java.util.List;

/**
 * Cobertura do item 2.15 do SAMAE no recorte da ED-30 (negativacao e
 * protesto por arquivo). O item 2.15 ("Cobranca") se reparte entre P4.1,
 * ED-29, ED-30 e ED-31; este registro cobre SOMENTE a fatia da ED-30.
 *
 * <p>MARCADOR HONESTO (mesmo da ED-31): os codigos SG individuais da
 * fatia de negativacao NAO foram confirmados nas fontes disponiveis
 * nesta entrega — nenhum codigo SG e inventado aqui; as linhas usam as
 * exigencias do aceite da ED-30 e a atribuicao de codigos SG fica
 * PENDENTE de conferencia contra o roteiro do SAMAE.
 */
public final class Samae215NegCoverage {

    private Samae215NegCoverage() {}

    public record Entry(String requirement, String feature) {}

    public static final List<Entry> ED30_SLICE = List.of(
            new Entry("Remessa ao biro e a central de protesto gerada e retorno importado, pelos canais de neg.channels",
                    "RemittanceAndReturn + NegativationPorts.BureauFilePort [layout SEM FONTE]"),
            new Entry("Inclusao sem carta de aviso previo com entrega registrada e recusada",
                    "ListingEligibility (PRIOR_NOTICE_WITHOUT_DELIVERY_PROOF)"),
            new Entry("Aviso previo com prova de entrega e intervalo minimo (neg.prior_notice_by, neg.notice_min_days)",
                    "ListingEligibility (PRIOR_NOTICE_INTERVAL_NOT_MET)"),
            new Entry("Perfis excluidos (neg.excluded_profiles) com motivo registrado por unidade recusada",
                    "ListingEligibility.Decision com todos os RefusalReason"),
            new Entry("Conta contestada ou em revisao fica fora",
                    "CollectionGates.listingBlocks (P4.1) via ListingEligibility"),
            new Entry("Titular falecido nao e negativado, pela situacao da P1.41 (CAD-65)",
                    "NegativationPorts.HolderSituationPort + HOLDER_DECEASED"),
            new Entry("Repactuacao por superendividamento e divida prescrita fora (regras da P4.1, CAD-75/RAS-15)",
                    "CollectionGates + Prescription item a item, via ListingEligibility"),
            new Entry("Exclusao marcada no pagamento e enviada em no maximo 5 dias uteis (travado)",
                    "ListingLedger.markPaid/sendPendingExclusion + EXCLUSION_SEND_MAX_BUSINESS_DAYS"),
            new Entry("Baixa quando a conta e cancelada (20b FAT-43) e expiracao em 5 anos (travado)",
                    "ListingLedger.cancelAccount/expireOverdue + LISTING_EXPIRY_YEARS"),
            new Entry("Exclusao a pedido por OS NEGATIVE_LISTING_EXCLUSION",
                    "ListingLedger.excludeByServiceOrder"),
            new Entry("Relatorio de inclusoes e exclusoes", "ListingReport"));

    public static List<String> requirements() { return ED30_SLICE.stream().map(Entry::requirement).toList(); }
}
