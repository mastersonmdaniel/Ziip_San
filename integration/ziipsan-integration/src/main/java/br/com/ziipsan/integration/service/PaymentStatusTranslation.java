package br.com.ziipsan.integration.service;

import java.util.List;

/**
 * Tradução do {@code status} de pagamento entre o vocabulário canónico (o que a
 * tabela {@code payments} grava depois da convergência V112, decisão 75, PD-2) e
 * o vocabulário FEBRABAN exposto pelo contrato da API pública {@code /v1/payments}
 * (PD-3: o contrato fica preservado).
 *
 * <p>Vocabulário canónico (21 valores, CHECK {@code chk_payments_status_unified}):
 * os 15 FEBRABAN + RECEBIDO, CLASSIFICADO, BAIXADO, DUPLICIDADE, NAO_IDENTIFICADO,
 * TRANSFERIDO. Três pares divergentes normalizam-se nos dois sentidos:
 * <ul>
 *   <li>{@code CLASSIFIED} ↔ {@code CLASSIFICADO};</li>
 *   <li>{@code DUPLICATE} ↔ {@code DUPLICIDADE};</li>
 *   <li>{@code DOC_NOT_FOUND} ↔ {@code NAO_IDENTIFICADO}.</li>
 * </ul>
 * Os restantes valores passam tal como estão nos dois sentidos: os 12 valores
 * FEBRABAN sem correspondente P3.1 são valores legítimos do vocabulário unificado,
 * e os P3.1 sem correspondente FEBRABAN ({@code BAIXADO}, {@code RECEBIDO},
 * {@code TRANSFERIDO}) não têm tradução FEBRABAN — expô-los com outro literal
 * (ex.: {@code BAIXADO} como {@code CLASSIFIED}) seria inventar uma equivalência
 * que o diagnóstico recusa (DIAGNOSTICO-P-D72-V151-01 §2.2: {@code CLASSIFIED} é
 * "casado com o documento", não "conta baixada").
 *
 * <p>Na direcção do filtro (o chamador pede um literal FEBRABAN), os dois membros
 * do par casam: um filtro {@code status=CLASSIFIED} devolve tanto as linhas
 * gravadas como {@code CLASSIFIED} como as gravadas como {@code CLASSIFICADO} —
 * do ponto de vista do consumidor da API, é a mesma situação. O inverso vale
 * para um filtro com o literal canónico ({@code CLASSIFICADO} casa os dois).
 *
 * <p>Classe pura, sem I/O: testada sem banco em {@code PaymentStatusTranslationTest}.
 */
public final class PaymentStatusTranslation {

    private PaymentStatusTranslation() {
    }

    /**
     * Literais canónicos que um filtro da API deve casar. O argumento é o literal
     * que o chamador passou (vocabulário FEBRABAN exposto, ou — por tolerância — o
     * literal canónico). Um valor desconhecido passa tal como está (a consulta
     * devolve vazio, como hoje); {@code null} devolve lista vazia (sem filtro).
     */
    public static List<String> canonicalStatusesForApiFilter(String apiStatus) {
        if (apiStatus == null) {
            return List.of();
        }
        return switch (apiStatus) {
            case "CLASSIFIED", "CLASSIFICADO" -> List.of("CLASSIFIED", "CLASSIFICADO");
            case "DUPLICATE", "DUPLICIDADE" -> List.of("DUPLICATE", "DUPLICIDADE");
            case "DOC_NOT_FOUND", "NAO_IDENTIFICADO" -> List.of("DOC_NOT_FOUND", "NAO_IDENTIFICADO");
            default -> List.of(apiStatus);
        };
    }

    /**
     * Literal FEBRABAN a expor para um {@code status} canónico lido da tabela.
     * Os três pares traduzem-se; o resto passa tal como está (incl. {@code null}).
     */
    public static String apiStatusForCanonical(String canonicalStatus) {
        if (canonicalStatus == null) {
            return null;
        }
        return switch (canonicalStatus) {
            case "CLASSIFICADO" -> "CLASSIFIED";
            case "DUPLICIDADE" -> "DUPLICATE";
            case "NAO_IDENTIFICADO" -> "DOC_NOT_FOUND";
            default -> canonicalStatus;
        };
    }
}
