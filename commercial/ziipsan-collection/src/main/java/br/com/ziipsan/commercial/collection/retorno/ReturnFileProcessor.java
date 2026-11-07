package br.com.ziipsan.commercial.collection.retorno;

import br.com.ziipsan.core.document.DocumentNumber;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ingestao do arquivo de retorno bancario (P3.1, R5a) com idempotencia por NSA e hash.
 *
 * <p>Regra central: a chave de idempotencia e {@code (tenant, nsa, sha256 do conteudo)}.
 * Reprocessar o mesmo arquivo devolve {@link Resultado#REPROCESSADO_SEM_EFEITO} e NAO
 * grava nada de novo - sem novo arquivo, sem novos detalhes e, portanto, sem segunda
 * baixa. Mesmo NSA com conteudo diferente (hash diferente) e um arquivo novo e legitimo
 * (retorno retificado) e e processado normalmente.
 *
 * <p>Antes de qualquer gravacao, o CNPJ do recebedor do cabecalho e os CNPJs de pagador
 * das linhas sao validados com a validacao de CNPJ alfanumerico da P1.39
 * ({@link DocumentNumber#isValidCnpj}); documento invalido recusa o arquivo inteiro com
 * erro explicito - arquivo parcialmente aceito criaria baixa sem lastro.
 *
 * <p>As colunas FEBRABAN 150 (captura, forma de pagamento, agencia arrecadadora,
 * autenticacao, numero da linha e codigo de barras) viajam nas linhas e sao gravadas
 * SOMENTE no detalhe do retorno (ver {@code ReturnFileDetailEntity}), nunca em
 * {@code payments}. Pagamentos Pix/cartao nao passam por este processador.
 *
 * <p>A persistencia fica atras da porta {@link ReturnFileStore}; a implementacao JPA e
 * fiacao da integracao. Este servico nao conhece banco nem relógio escondido: a data de
 * recebimento vem no comando.
 */
public final class ReturnFileProcessor {

    /** Linha do arquivo de retorno (detalhe FEBRABAN 150). */
    public record ReturnLine(int lineNumber, String barcode, String captureForm, String paymentForm,
                             String collectingBranch, String authentication, String payerDocument,
                             BigDecimal paidAmount, LocalDate paidOn) {
        public ReturnLine {
            if (lineNumber < 1) {
                throw new IllegalArgumentException("line_number deve ser >= 1: " + lineNumber);
            }
            if (paidAmount == null || paidAmount.signum() <= 0) {
                throw new IllegalArgumentException("valor pago da linha " + lineNumber + " deve ser positivo: " + paidAmount);
            }
            Objects.requireNonNull(paidOn, "data do pagamento da linha " + lineNumber + " e obrigatoria");
        }
    }

    /**
     * @param nsa              numero sequencial do arquivo atribuido pelo banco
     * @param fileName         nome do arquivo recebido (rastro; nao entra na idempotencia)
     * @param content          conteudo bruto do arquivo (bytes); o hash e calculado sobre ele
     * @param receiverDocument CNPJ do recebedor/cedente do cabecalho (aceita alfanumerico, P1.39)
     * @param receivedOn       data de recebimento do arquivo
     * @param lines            linhas de detalhe do retorno
     */
    public record ReturnFileCommand(String nsa, String fileName, byte[] content, String receiverDocument,
                                    LocalDate receivedOn, List<ReturnLine> lines) {
    }

    public enum Resultado { PROCESSADO, REPROCESSADO_SEM_EFEITO }

    /** @param fileId id do arquivo (o preexistente, quando reprocessado) */
    public record IngestionOutcome(Resultado resultado, UUID fileId, String fileHash, int detailCount) {
    }

    /** Porta de persistencia do retorno. A busca e a gravacao sao por tenant. */
    public interface ReturnFileStore {
        Optional<UUID> findFileIdByNsaAndHash(UUID tenantId, String nsa, String fileHash);

        /** Grava arquivo + detalhes + pagamentos de uma so vez (transacao do adaptador). */
        UUID saveNewFile(UUID tenantId, ReturnFileCommand command, String fileHash);
    }

    private final ReturnFileStore store;

    public ReturnFileProcessor(ReturnFileStore store) {
        this.store = Objects.requireNonNull(store, "ReturnFileStore e obrigatorio");
    }

    /** SHA-256 em hexadecimal minusculo do conteudo bruto. */
    public static String sha256Hex(byte[] content) {
        Objects.requireNonNull(content, "conteudo do arquivo e obrigatorio");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

    public IngestionOutcome process(UUID tenantId, ReturnFileCommand command) {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(command, "comando do arquivo de retorno e obrigatorio");
        if (command.nsa() == null || command.nsa().isBlank()) {
            throw new IllegalArgumentException("NSA do arquivo de retorno e obrigatorio");
        }
        Objects.requireNonNull(command.receivedOn(), "data de recebimento e obrigatoria");
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("arquivo de retorno sem linhas de detalhe: NSA " + command.nsa());
        }
        // P1.39: CNPJ alfanumerico do recebedor e dos pagadores, antes de gravar qualquer coisa.
        if (!DocumentNumber.isValidCnpj(command.receiverDocument())) {
            throw new IllegalArgumentException("CNPJ do recebedor invalido no cabecalho do retorno: "
                    + DocumentNumber.normalize(command.receiverDocument()));
        }
        for (ReturnLine line : command.lines()) {
            if (line.payerDocument() != null && !DocumentNumber.isValidCnpj(line.payerDocument())) {
                throw new IllegalArgumentException("CNPJ do pagador invalido na linha " + line.lineNumber()
                        + ": " + DocumentNumber.normalize(line.payerDocument()));
            }
        }
        String hash = sha256Hex(command.content());
        Optional<UUID> existing = store.findFileIdByNsaAndHash(tenantId, command.nsa(), hash);
        if (existing.isPresent()) {
            // Reprocessamento: nenhum efeito novo. A baixa nao pode duplicar.
            return new IngestionOutcome(Resultado.REPROCESSADO_SEM_EFEITO, existing.get(), hash, 0);
        }
        UUID fileId = store.saveNewFile(tenantId, command, hash);
        return new IngestionOutcome(Resultado.PROCESSADO, fileId, hash, command.lines().size());
    }

    /** Mes de arrecadacao (DAD-19): dia 1 do mes do pagamento, sem relacao com a competencia da conta. */
    public static LocalDate collectionMonthOf(LocalDate paidOn) {
        Objects.requireNonNull(paidOn, "data do pagamento e obrigatoria");
        return paidOn.withDayOfMonth(1);
    }

    /** Conteudo textual entra sempre como UTF-8 explicito (o hash nao pode depender do charset da JVM). */
    public static byte[] utf8(String content) {
        return Objects.requireNonNull(content, "conteudo e obrigatorio").getBytes(StandardCharsets.UTF_8);
    }
}
