package br.com.ziipsan.commercial.registry.legacy;

import java.time.LocalDate;
import java.util.UUID;

/**
 * P2.10: porta de criação dos débitos a cobrar da migração, nas colunas de
 * origem da P2.5 (DAD-20). Dois comandos, e só dois:
 *
 * <ul>
 *   <li>{@link InstallmentDebit}: UMA prestação a vencer de parcelamento
 *       ativo vira UM débito a cobrar de UMA parcela ({@code installments
 *       = 1}) — é o "cobradas uma a uma na conta" do aceite: cada
 *       prestação entra na conta do seu mês de vencimento como um débito
 *       próprio, com {@code source_type = INSTALLMENT_AGREEMENT},
 *       {@code source_id} = acordo migrado e {@code legacy_reference} =
 *       referência do acordo no legado;</li>
 *   <li>{@link ResidualDebit}: o resíduo do saldo por matrícula (saldo do
 *       legado menos as prestações), como UM débito de
 *       {@code source_type = LEGACY_IMPORT} com {@code legacy_reference}
 *       construída por {@link LegacyReferences#residualReference} a partir
 *       da chave da unidade no lote — é ele que faz a soma dos débitos
 *       criados ser igual ao saldo do legado na conferência
 *       ({@link V1BalanceConference}).</li>
 * </ul>
 *
 * <p>Toda {@code legacy_reference} gravada respeita o teto de 100
 * caracteres de {@code debit_receivables.legacy_reference} (V4_4, P2.5):
 * ver {@link LegacyReferences} (correção A4 da revisão cruzada).
 *
 * <p>Os dois comandos carregam os campos de proveniência que o domínio da
 * P2.5 ({@code DebitReceivable}/{@code ReceivableChecks}) e os CHECKs da
 * V4_4 exigem; a implementação JDBC grava exatamente estes valores.
 * {@code debitTypeCode} é o código do item do catálogo {@code debit_types}
 * do tenant (catálogo é dado do tenant, classe a — a P2.5 não semeia
 * códigos): ele é configuração da chamada, resolvido para
 * {@code debit_type_id} pela implementação; código ausente no catálogo é
 * erro explícito, nunca um tipo inventado.
 */
public interface InstallmentDebitGateway {

    /** {@code source_type} dos débitos de prestação (DAD-20, P2.5). */
    String SOURCE_INSTALLMENT_AGREEMENT = "INSTALLMENT_AGREEMENT";

    /** {@code source_type} do débito de resíduo de saldo (DAD-20, P2.5). */
    String SOURCE_LEGACY_IMPORT = "LEGACY_IMPORT";

    record InstallmentDebit(
            UUID unitId,
            String debitTypeCode,
            long amountCents,
            int installments,
            UUID agreementId,
            String legacyAgreementRef,
            int installmentNo,
            LocalDate dueDate,
            LocalDate accountingMonth,
            LocalDate collectionMonth,
            int financingTerm) {

        public InstallmentDebit {
            // Uma a uma: cada prestação vira um débito de UMA parcela.
            // Outro valor aqui seria um débito agrupado disfarçado — o
            // aceite manda cobrar prestação por prestação na conta.
            if (installments != 1) {
                throw new IllegalArgumentException("Débito de prestação é sempre de 1 parcela"
                        + " (installments=1): cada prestação a vencer vira um débito próprio.");
            }
        }
    }

    record ResidualDebit(
            UUID unitId,
            String debitTypeCode,
            long amountCents,
            String legacyReference,
            LocalDate accountingMonth,
            LocalDate collectionMonth) {
    }

    /** Cria o débito de uma prestação e devolve o id dele em {@code debit_receivables}. */
    UUID createInstallmentDebit(UUID tenantId, UUID actorId, InstallmentDebit debit);

    /** Cria o débito de resíduo de saldo de uma matrícula e devolve o id dele. */
    UUID createResidualDebit(UUID tenantId, UUID actorId, ResidualDebit debit);
}
