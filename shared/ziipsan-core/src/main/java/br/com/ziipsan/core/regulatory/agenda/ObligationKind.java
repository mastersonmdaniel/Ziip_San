package br.com.ziipsan.core.regulatory.agenda;

/**
 * ED-98 (GES-05): os tipos de obrigacao do aceite, um a um. Cada tipo
 * carrega o token pelo qual a sua regra e LIDA do catalogo de normas da
 * P3.15 ({@link #normToken()}): a regra so vira item de calendario se o
 * catalogo tiver norma casando o token -- regra sem norma no catalogo
 * nao e inventada, e reportada como nao resolvida pelo gerador.
 */
public enum ObligationKind {
    SINISA_ANNUAL_CYCLE("SINISA", false, java.util.Set.of("SINISA")),
    SOCIAL_TARIFF_ANNUAL_REPORT("TARIFA SOCIAL", false, java.util.Set.of("TARIFASOCIAL")),
    ANNUAL_DISCHARGE_DECLARATION("QUITACAO", false, java.util.Set.of("QUITACAO")),
    WATER_LOSS_BALANCE_NR15("NR 15", false, java.util.Set.of("NR15", "PERDAS")),
    ESFINGE_BIMONTHLY("TC-28/2021", false, java.util.Set.of("ESFINGE", "TC282021")),
    SISAGUA("SISAGUA", false, java.util.Set.of("SISAGUA")),
    TARIFF_READJUSTMENT_PUBLICATION("REAJUSTE", false, java.util.Set.of("REAJUSTE")),
    RREO("RREO", false, java.util.Set.of("RREO")),
    RGF("RGF", false, java.util.Set.of("RGF")),
    /** RAS-17: MTR, CDF e DMR como ITEM MANUAL enquanto a ED-104 nao for liberada. */
    WASTE_MTR_CDF_DMR_MANUAL("DMR", true, java.util.Set.of()),
    /** JOR-24: desdobramento de fiscalizacao recebida (ocorrencia da P2.25). */
    EXTERNAL_INSPECTION_FOLLOWUP("FISCALIZACAO", false, java.util.Set.of("FISCALIZACAO"));

    private final String normToken;
    private final boolean manual;
    private final java.util.Set<String> evidenceTokens;

    ObligationKind(String normToken, boolean manual, java.util.Set<String> evidenceTokens) {
        this.normToken = normToken;
        this.manual = manual;
        this.evidenceTokens = evidenceTokens;
    }

    /** Token procurado (chave/titulo) no catalogo de normas da P3.15. */
    public String normToken() { return normToken; }

    /** Item manual (RAS-17): conclusao por registro do responsavel, sem evidencia de envio. */
    public boolean isManual() { return manual; }

    /**
     * Correcao D1 (REVISAO-ED-98) — correspondencia de tipo entre a
     * evidencia da ED-97 e esta obrigacao: o {@code SubmissionRecord}
     * da ED-97 identifica o relatorio enviado pela AGENCIA e pelo
     * PERFIL (ex.: perfil {@code ARIS_SISARIS} = relatorio SISARIS da
     * ARIS); a evidencia so corresponde a esta obrigacao se a
     * agencia+perfil, normalizados (acentos/caixa/separadores fora,
     * {@link TextFolding#squash}), contiverem um dos identificadores
     * DESTE tipo (derivados do seu proprio vocabulario — token da
     * norma e nome do tipo; nenhum dado externo novo). Sem
     * correspondencia, o vinculo e recusado: a mesma evidencia nao
     * quita dois tipos distintos (sonda S7 do revisor). A amarracao
     * definitiva perfil ED-97 x obrigacao, na integracao, e a
     * pendencia P-ED98-07.
     */
    public boolean matchesSubmissionProfile(String agencyCode, String profileKey) {
        String haystack = TextFolding.squash((agencyCode == null ? "" : agencyCode)
                + "|" + (profileKey == null ? "" : profileKey));
        return evidenceTokens.stream().anyMatch(haystack::contains);
    }
}
