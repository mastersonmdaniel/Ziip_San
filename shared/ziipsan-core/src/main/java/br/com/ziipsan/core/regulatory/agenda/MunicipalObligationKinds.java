package br.com.ziipsan.core.regulatory.agenda;

import java.util.Set;

/**
 * MM-06 (decisao item 45): os {@link ObligationKind} cuja geracao desce
 * ao municipio — conjunto FECHADO, derivado de evidencia, nunca
 * presumido. Classe NOVA ao lado das vigentes da ED-98; nenhum ficheiro
 * da ED-98 e editado.
 *
 * <p><b>Evidencia de cada kind incluido</b> (conferida nas fontes do
 * pacote; detalhe no RELATORIO-MM-06):</p>
 * <ul>
 *   <li>{@code ESFINGE_BIMONTHLY} — ANALISE-MULTIMUNICIPIO.md §4
 *       (linha "Agenda regulatoria (ED-98 + P2.31)"): "e-Sfinge/SISAGUA
 *       ja nascem de perfis por municipio"; §2 (linha da agenda): os
 *       perfis que a alimentam (P2.31) ja sao por municipio. A remessa
 *       ao e-Sfinge e por jurisdicionado ao Tribunal de Contas (IN
 *       TC-28/2021, token da regra vigente da ED-98) e o perfil
 *       regulatorio que a sustenta vive em {@code regulatory_profiles}
 *       por {@code municipality_ibge} (platform V1_13, P2.31).</li>
 *   <li>{@code SISAGUA} — a mesma evidencia do estudo §4/§2 (nomeado ao
 *       lado do e-Sfinge como nascido de perfis por municipio).</li>
 * </ul>
 *
 * <p><b>Excluidos, nomeados</b> (sem evidencia de fonte municipal na
 * cadeia ED-98 x perfis P2.31 — ficam no escopo-empresa vigente):
 * {@code SINISA_ANNUAL_CYCLE} (ciclo nacional do prestador),
 * {@code SOCIAL_TARIFF_ANNUAL_REPORT} (relatorio do tenant a agencia —
 * ED-97, por perfil de agencia, sem eixo municipal evidenciado),
 * {@code ANNUAL_DISCHARGE_DECLARATION} (fatura do tenant),
 * {@code WATER_LOSS_BALANCE_NR15} (a ED-78 tem planos NR15 por
 * municipio, mas a fonte do KIND da agenda na cadeia ED-98/P2.31 nao
 * esta evidenciada como municipal — pendencia P-MM06-02, nao
 * presuncao), {@code TARIFF_READJUSTMENT_PUBLICATION} (data-base do
 * perfil {@code reg.*} do tenant), {@code RREO} e {@code RGF}
 * (relatorios DO ENTE — LC 101/2000; o eixo e a natureza juridica do
 * tenant, correcao D2 da ED-98), {@code WASTE_MTR_CDF_DMR_MANUAL}
 * (item manual do tenant, RAS-17) e
 * {@code EXTERNAL_INSPECTION_FOLLOWUP} (por evento/fiscalizacao,
 * JOR-24 — nao nasce da geracao de calendario).</p>
 */
public final class MunicipalObligationKinds {

    private MunicipalObligationKinds() { }

    /** Os kinds com fonte municipal evidenciada (ver o javadoc da classe). */
    public static final Set<ObligationKind> MUNICIPAL_KINDS =
            Set.of(ObligationKind.ESFINGE_BIMONTHLY, ObligationKind.SISAGUA);

    /** Este kind gera um item por municipio (quando o tenant tem perfis municipais)? */
    public static boolean isMunicipal(ObligationKind kind) {
        return MUNICIPAL_KINDS.contains(kind);
    }
}
