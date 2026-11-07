package br.com.ziipsan.commercial.billing.cashback;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência do cashback (P2.34) sobre as tabelas da V4_12. Como
 * as portas da P2.6 ({@code NfageDocumentoStore}), <b>não tem implementação
 * nesta entrega</b>: a implementação JPA sobre
 * {@code cashback_list_imports/entries/accesses/returns} é da integração.
 * Os records abaixo são o vocabulário da porta — espelham as colunas da
 * V4_12 sem inventar nenhuma.
 */
public interface CashbackStore {

    /** Entrada da lista da RFB: o domicílio é elegível no período (V4_12, {@code cashback_list_entries}). */
    record EntradaLista(UUID entryId, UUID importId, String domicileKeyHash, YearMonth assessmentMonth) {
    }

    /**
     * Devolução já registrada (V4_12, {@code cashback_returns}).
     * {@code listImportId}/{@code listEntryId} são NOT NULL na V4_12 (FKs à
     * importação e à entrada da lista que fundamentam a elegibilidade):
     * sem eles a gravação é impossível — corrigido na revisão cruzada.
     */
    record DevolucaoRegistrada(UUID id, UUID tenantId, String domicileKeyHash, YearMonth assessmentMonth,
                               UUID billId, LocalDate billReferenceMonth,
                               UUID listImportId, UUID listEntryId,
                               BigDecimal cbsTaxAmount, BigDecimal ibsTaxAmount,
                               BigDecimal cbsReturnAmount, BigDecimal ibsReturnAmount,
                               BigDecimal totalReturnAmount, LocalDate taxEventDate) {
    }

    /** Entrada elegível do domicílio no período, se houver. */
    Optional<EntradaLista> buscarEntrada(UUID tenantId, String domicileKeyHash, YearMonth assessmentMonth);

    /** Devolução já concedida ao domicílio no período, se houver (a garantia da devolução única). */
    Optional<DevolucaoRegistrada> buscarDevolucao(UUID tenantId, String domicileKeyHash, YearMonth assessmentMonth);

    /**
     * Registra um acesso à lista (padrão G2 do aceite: cada acesso
     * registrado, com origem/data/versão alcançáveis pela importação).
     * Chamado pelo serviço em <b>toda</b> consulta — elegível ou não.
     *
     * @param importId importação consultada; {@code null} quando o domicílio não consta de nenhuma
     */
    void registrarAcesso(UUID tenantId, UUID importId, String domicileKeyHash, UUID accessedBy,
                         String accessType, String purpose, Instant accessedAt);

    /** Grava a devolução concedida e a devolve com o id gerado. */
    DevolucaoRegistrada salvarDevolucao(DevolucaoRegistrada devolucao);
}
