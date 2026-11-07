package br.com.ziipsan.commercial.billing.cycle;

import java.util.List;

/**
 * Catalogo de atividades do PRODUTO (P2.5/F1): a definicao de fabrica que o servico
 * de ciclo grava em {@code cycle_activities} na abertura da primeira referencia do
 * tenant. A migration V4_3 nao semeia linha nenhuma, no padrao do modulo (tarifa,
 * motivos de encerramento e anormalidades nascem vazios porque sao politica do
 * tenant); o catalogo de atividades, ao contrario, e estrutura do produto -- ordem,
 * obrigatoriedade, repeticao e precedencia nao sao escolha do tenant -- e por isso
 * a carga e do servico, a partir desta unica definicao.
 *
 * <p>Cadeia de precedencia (cada atividade exige a anterior realizada):
 * <pre>
 *   READING -> TRANSMISSION -> BILLING -> CLOSING
 * </pre>
 * A transmissao e atividade propria, e nao parte da leitura, porque o aceite recusa
 * o faturamento de rota de leitura NAO TRANSMITIDA: sem uma atividade de transmissao
 * nao ha como distinguir "leu" de "transmitiu". Leitura e transmissao sao repetiveis
 * (releitura e retransmissao sao operacao normal de campo); faturamento nao e
 * repetivel (a idempotencia da conta, V4_1/{@code BillIdempotency}, veda a segunda
 * conta do ciclo) e fechamento tambem nao.
 */
public final class ProductActivityCatalog {

    /** Definicao de uma atividade do produto. */
    public record Definition(CycleActivityCode code, String name, int sequenceOrder,
                             boolean mandatory, boolean repeatable, CycleActivityCode predecessor) {
    }

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(CycleActivityCode.READING, "Leitura", 1, true, true, null),
            new Definition(CycleActivityCode.TRANSMISSION, "Transmissao da rota de leitura", 2, true, true,
                    CycleActivityCode.READING),
            new Definition(CycleActivityCode.BILLING, "Faturamento", 3, true, false,
                    CycleActivityCode.TRANSMISSION),
            new Definition(CycleActivityCode.CLOSING, "Fechamento do grupo", 4, true, false,
                    CycleActivityCode.BILLING));

    private ProductActivityCatalog() {
    }

    /** As definicoes do produto, na ordem do ciclo. */
    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    /** A definicao de um codigo. */
    public static Definition of(CycleActivityCode code) {
        for (Definition definition : DEFINITIONS) {
            if (definition.code() == code) {
                return definition;
            }
        }
        throw new IllegalArgumentException("Atividade fora do catalogo do produto: " + code);
    }
}
