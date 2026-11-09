package br.com.ziipsan.core.regulatory.agenda;

import java.util.Set;

/**
 * ED-98 (GES-05): natureza juridica do tenant -- um dos dois eixos da
 * geracao do calendario de obrigacoes (o outro e o perfil {@code reg.*}).
 *
 * <p>{@link #PUBLIC_LAW} e o conjunto das naturezas de direito publico,
 * jurisdicionadas ao Tribunal de Contas (remessa ao e-Sfinge, IN
 * TC-28/2021). RREO/RGF sao do ENTE (administracao direta): apos a
 * correcao D2 (REVISAO-ED-98), as regras de RREO/RGF exigem a natureza
 * {@link #ADMINISTRACAO_DIRETA_ENTE} E a flag do perfil
 * ({@link TenantObligationProfile#fiscalReportsApplicable()}) — a flag
 * sozinha nao os impoe a autarquia, empresa publica ou concessionaria
 * privada, e a natureza sozinha nao os impoe ao ente sem a flag.</p>
 */
public enum TenantLegalNature {
    ADMINISTRACAO_DIRETA_ENTE,
    AUTARQUIA,
    FUNDACAO_PUBLICA,
    EMPRESA_PUBLICA,
    SOCIEDADE_ECONOMIA_MISTA,
    CONCESSIONARIA_PRIVADA;

    /** Naturezas jurisdicionadas ao TC (e-Sfinge, IN TC-28/2021). */
    public static final Set<TenantLegalNature> PUBLIC_LAW = Set.of(
            ADMINISTRACAO_DIRETA_ENTE, AUTARQUIA, FUNDACAO_PUBLICA,
            EMPRESA_PUBLICA, SOCIEDADE_ECONOMIA_MISTA);

    public boolean isPublicLaw() { return PUBLIC_LAW.contains(this); }
}
