package br.com.ziipsan.commercial.billing.printing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Pedido de geracao de um lote para a grafica (P2.8).
 *
 * @param batchId        id do lote, atribuido pelo chamador (idempotencia e
 *                       dele: regenerar o mesmo lote e decisao explicita dele)
 * @param generatedAt    instante da geracao, gravado no cabecalho do arquivo
 * @param profileKey     perfil regulatorio vigente (P2.31) contra o qual o
 *                       conteudo minimo da P2.32 e conferido
 * @param brailleEnabled valor vigente do parametro
 *                       {@code bill.braille_enabled} (P1.3, classe d): conta
 *                       marcada em braile com o parametro desligado recusa o lote
 * @param bills          contas do lote, na ordem de impressao desejada
 */
public record PrintFileRequest(
        UUID batchId,
        OffsetDateTime generatedAt,
        String profileKey,
        boolean brailleEnabled,
        List<PrintBillSource> bills) {

    public PrintFileRequest {
        if (batchId == null) {
            throw new IllegalArgumentException("batchId e obrigatorio");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt e obrigatorio");
        }
        if (profileKey == null || profileKey.isBlank()) {
            throw new IllegalArgumentException("profileKey e obrigatoria");
        }
        // [Correcao da revisao cruzada] A profileKey vai literal ao cabecalho
        // do arquivo (campo PERFIL=): com separador ou quebra de linha ela
        // corromperia o leiaute, e era o unico valor de texto livre do lote
        // sem essa trava (os demais passam por field()/PortalLinkingCode).
        if (profileKey.contains(PrintLayout.DELIMITER) || profileKey.contains("\n")
                || profileKey.contains("\r") || !profileKey.equals(profileKey.strip())) {
            throw new IllegalArgumentException(
                    "profileKey com caractere incompativel com o leiaute do arquivo da grafica");
        }
        if (bills == null || bills.isEmpty()) {
            throw new IllegalArgumentException("lote da grafica sem contas nao gera arquivo");
        }
        bills = List.copyOf(bills);
    }
}
