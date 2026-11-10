package br.com.ziipsan.commercial.billing.social.cycle;

import java.util.List;

/**
 * II-3: formato DECLARADO da lista externa de beneficiarios (CadUnico/BPC)
 * consumida pelo ciclo da tarifa social.
 *
 * <p>O leiaute oficial CadUnico/BPC NAO consta de nenhuma fonte do
 * projecto: fica [SEM FONTE] (pendencia P-II3-LEIAUTE da estimativa).
 * Por isso a importacao nasce por PORTA com formato declarado
 * ({@link SocialListImportPort}): este record e a declaracao completa do
 * formato de prova - delimitador, ordem e nome das colunas - e a
 * integracao real troca SO esta declaracao (e o adaptador que a le),
 * sem tocar no cruzamento, na elegibilidade nem no ciclo de vida.
 *
 * <p>Colunas do formato de prova (so indices cegos e dados do beneficio,
 * minimizacao LGPD por desenho - nenhum documento, nome ou renda em
 * claro, como a P1.5/V4_2 e a ED-57 ja desenharam):
 * <ol>
 *   <li>{@code family_code_hash} - HMAC 64 hex do codigo familiar;</li>
 *   <li>{@code person_document_hash} - HMAC 64 hex do documento da pessoa;</li>
 *   <li>{@code person_role} - HOLDER ou MEMBER;</li>
 *   <li>{@code eligibility_basis} - CADUNICO, BPC ou LOCAL_PROGRAM;</li>
 *   <li>{@code cadunico_updated_on} - data ISO ou vazio (existe exatamente
 *       quando a base e CADUNICO, CHECK da V4_2);</li>
 *   <li>{@code benefit_valid_from} - data ISO, inicio da vigencia declarada;</li>
 *   <li>{@code benefit_valid_to} - data ISO ou vazio (sem fim declarado);</li>
 *   <li>{@code municipality_code} - codigo do municipio declarado na lista,
 *       ou vazio; alimenta a busca activa por municipio quando nao ha
 *       unidade correspondente. Nao e codigo IBGE validado: o formato de
 *       prova transporta o codigo tal como declarado [SEM FONTE para a
 *       codificacao oficial].</li>
 * </ol>
 */
public record DeclaredSocialListFormat(String formatId, String version, char delimiter, List<String> columns) {

    public static final String COL_FAMILY = "family_code_hash";
    public static final String COL_PERSON = "person_document_hash";
    public static final String COL_ROLE = "person_role";
    public static final String COL_BASIS = "eligibility_basis";
    public static final String COL_CADUNICO_UPDATED = "cadunico_updated_on";
    public static final String COL_VALID_FROM = "benefit_valid_from";
    public static final String COL_VALID_TO = "benefit_valid_to";
    public static final String COL_MUNICIPALITY = "municipality_code";

    public DeclaredSocialListFormat {
        if (formatId == null || formatId.isBlank()) {
            throw new IllegalArgumentException("Identificador do formato declarado e obrigatorio");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Versao do formato declarado e obrigatoria");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Formato declarado sem colunas");
        }
        columns = List.copyOf(columns);
    }

    /**
     * O formato de prova declarado deste pacote. NAO e o leiaute oficial
     * CadUnico/BPC ([SEM FONTE]): e o contrato minimo do ciclo, com os
     * mesmos campos que a ED-57 ja grava na base da P1.5 mais o municipio
     * declarado para a busca activa.
     */
    public static DeclaredSocialListFormat proofFormat() {
        return new DeclaredSocialListFormat("II3-LISTA-SOCIAL-DECLARADA", "1", ';', List.of(
                COL_FAMILY, COL_PERSON, COL_ROLE, COL_BASIS, COL_CADUNICO_UPDATED,
                COL_VALID_FROM, COL_VALID_TO, COL_MUNICIPALITY));
    }

    /** A linha de cabecalho esperada, na ordem declarada. */
    public String headerLine() {
        return String.join(String.valueOf(delimiter), columns);
    }

    /** Posicao (0-based) de uma coluna declarada; -1 se nao declarada. */
    public int indexOf(String column) {
        return columns.indexOf(column);
    }
}
