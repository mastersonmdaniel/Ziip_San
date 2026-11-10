package br.com.ziipsan.grs.mtr.domain;

import java.util.regex.Pattern;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E5.
// Validação de formato dos códigos e classes, a partir do levantamento regulatório
// (docs/levantamento/12-regulatorio-residuos-mtr-sinir.md):
//  - Lista Brasileira (IN IBAMA 13/2012): 878 códigos de 6 dígitos; o asterisco marca resíduo
//    perigoso pela lista (449 códigos com (*)). Os sistemas oficiais usam o código SEM espaços.
//  - NBR 10004:2004: classes I, IIA, IIB. A série 2024 (10004-1/-2:2024) tem conteúdo
//    NÃO CONFIRMADO — [SEM FONTE] quanto às classes novas (pendência P-ED104-03).
//  - RSS: grupos A–E. RCC: classes A–D. Esquemas distintos, declarados.
// O catálogo nasce VAZIO (precedente do próprio módulo, V2_4): esta classe valida formato,
// não carrega código nenhum. A carga integral da lista oficial é a pendência P-ED104-02.
public final class WasteClassification {

    private static final Pattern IBAMA_CODE = Pattern.compile("\\d{6}");
    private static final Pattern RSS_GROUP = Pattern.compile("[A-E]");
    private static final Pattern RCC_CLASS = Pattern.compile("[A-D]");

    /** Edição confirmada pelo levantamento. */
    public static final String NBR_EDITION_CONFIRMED = "2004";
    /** Revisão cuja existência é confirmada mas cujo conteúdo de classes é [SEM FONTE]. */
    public static final String NBR_EDITION_UNCONFIRMED = "2024";

    private WasteClassification() {
    }

    /**
     * Código da Lista Brasileira: exatamente 6 dígitos, sem espaços. Um asterisco final é a marca
     * de periculosidade da lista e é devolvido por {@link #hazardousByList(String)}; o código
     * guardado é sempre os 6 dígitos.
     *
     * @throws IllegalArgumentException formato inválido
     */
    public static String requireIbamaCode(String raw) {
        String code = stripMarker(raw);
        if (code == null || !IBAMA_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("código da Lista Brasileira inválido: exige 6 dígitos "
                    + "(IN IBAMA 13/2012), recebido: " + raw);
        }
        return code;
    }

    /** O asterisco da IN 13/2012 marca o resíduo como perigoso pela lista. */
    public static boolean hazardousByList(String raw) {
        return raw != null && raw.trim().endsWith("*");
    }

    private static String stripMarker(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        return s.endsWith("*") ? s.substring(0, s.length() - 1).trim() : s;
    }

    /** Grupo de RSS (A–E). @throws IllegalArgumentException fora de A–E */
    public static String requireRssGroup(String raw) {
        if (raw == null || !RSS_GROUP.matcher(raw.trim().toUpperCase(java.util.Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("grupo de RSS inválido: exige A–E, recebido: " + raw);
        }
        return raw.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Classe de RCC (A–D). @throws IllegalArgumentException fora de A–D */
    public static String requireRccClass(String raw) {
        if (raw == null || !RCC_CLASS.matcher(raw.trim().toUpperCase(java.util.Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("classe de RCC inválida: exige A–D, recebido: " + raw);
        }
        return raw.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * A edição 2024 da NBR 10004 pode ser DECLARADA, mas o seu conteúdo de classes é
     * [SEM FONTE]/NÃO CONFIRMADO (P-ED104-03): nenhuma classe nova é aceite por esta via —
     * as classes válidas continuam a ser as da edição 2004 (I, IIA, IIB).
     */
    public static boolean isUnconfirmedEdition(String nbrEdition) {
        return NBR_EDITION_UNCONFIRMED.equals(nbrEdition);
    }
}
