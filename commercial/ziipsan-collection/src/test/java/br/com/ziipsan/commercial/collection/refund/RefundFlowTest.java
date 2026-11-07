package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.parameters.RefundParameters.DoubleRefundSetting;
import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundDestination;
import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundMode;
import br.com.ziipsan.commercial.collection.refund.RefundClosureService.ApprovalData;
import br.com.ziipsan.commercial.collection.refund.RefundClosureService.ClosureInput;
import br.com.ziipsan.commercial.collection.refund.RefundClosureService.DestinationData;
import br.com.ziipsan.commercial.collection.refund.RefundClosureService.DoubleRefundData;
import br.com.ziipsan.commercial.collection.refund.RefundClosureService.Execution;
import br.com.ziipsan.commercial.collection.refund.RefundClosureService.Outcome;
import br.com.ziipsan.commercial.collection.refund.RefundDestinationValidator.PixOrigin;
import br.com.ziipsan.commercial.collection.refund.RefundRequestRule.OpeningInput;
import br.com.ziipsan.commercial.collection.refund.RefundRequestRule.Source;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.29 - abertura (pendencias, hibrida e afastamento do dobro PELO FLUXO) e
 * encerramento fiado por opcao de pay.refund_mode, com idempotencia do
 * reenvio. Valores a mao: duplicidade de 100,00; cobranca indevida de 87,50
 * com dobro = 175,00 e simples = 87,50; alcada de teste 500,00 (acima dela,
 * 500,01+ exige aprovacao dupla).
 *
 * <p>Pos-revisao (M1/M2): o encerramento nao recebe mais um booleano
 * {@code approved}: recebe STATUS, aprovacoes registradas + alcada, dados
 * do dobro e destino (cifrado + validacao). Os casos antigos foram refeitos
 * sobre a nova assinatura SEM afrouxar nenhuma assercao - o caso
 * "encerramento sem aprovacao" virou dois casos mais fortes (sem aprovador;
 * acima da alcada com um aprovador so).
 */
class RefundFlowTest {

    private static final UUID OS = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID APPROVER_A = UUID.randomUUID();
    private static final UUID APPROVER_B = UUID.randomUUID();
    private static final BigDecimal THRESHOLD = new BigDecimal("500.00");
    private static final DoubleRefundSetting DOUBLE_ON = new DoubleRefundSetting(true, null);
    private static final String CPF = "52998224725";
    private static final String CIPHERTEXT = "CIFRADO-TESTE";

    private static OpeningInput opening(UUID origin, String originAmount, String requested,
                                        String expected, RefundType type, Source source) {
        return new OpeningInput(OS, source, type, origin,
                originAmount == null ? null : new BigDecimal(originAmount),
                requested == null ? null : new BigDecimal(requested),
                expected == null ? null : new BigDecimal(expected), DOUBLE_ON, null);
    }

    // ---------- pendencias: um teste por motivo ----------

    @Test
    void pendenciaSemPagamentoDeOrigem() {
        var d = RefundRequestRule.open(opening(null, null, "100.00", null,
                RefundType.DUPLICATE_PAYMENT, Source.MANUAL_REQUEST));
        assertThat(d.isPending()).isTrue();
        assertThat(d.pendingReason()).isEqualTo(RefundPendingReason.MISSING_ORIGIN_PAYMENT);
        assertThat(d.calculated()).isNull();
    }

    @Test
    void pendenciaComValorDivergenteDaOrigem() {
        // Origem de 100,00; pedido de 90,00 diverge -> pendente, sem calculo.
        var d = RefundRequestRule.open(opening(PAYMENT, "100.00", "90.00", null,
                RefundType.DUPLICATE_PAYMENT, Source.MANUAL_REQUEST));
        assertThat(d.isPending()).isTrue();
        assertThat(d.pendingReason()).isEqualTo(RefundPendingReason.AMOUNT_DIVERGENCE);
        assertThat(d.calculated()).isNull();
        // Pedido MAIOR que a origem (120,00) tambem diverge.
        var over = RefundRequestRule.open(opening(PAYMENT, "100.00", "120.00", null,
                RefundType.DUPLICATE_PAYMENT, Source.MANUAL_REQUEST));
        assertThat(over.pendingReason()).isEqualTo(RefundPendingReason.AMOUNT_DIVERGENCE);
    }

    // ---------- origem hibrida (P3.2 / RAS-19) ----------

    @Test
    void segundoPagamentoDaHibridaChegaComoDuplicidade() {
        var d = RefundRequestRule.open(opening(PAYMENT, "100.00", "100.00", "100.00",
                RefundType.DUPLICATE_PAYMENT, Source.P3_2_HYBRID_SECOND_PAYMENT));
        assertThat(d.isPending()).isFalse();
        assertThat(d.calculated().refundAmount()).isEqualByComparingTo("100.00");
        assertThat(d.calculated().doubleApplied()).isFalse();
        // A hibrida NUNCA chega como cobranca indevida.
        assertThatThrownBy(() -> RefundRequestRule.open(opening(PAYMENT, "100.00", "100.00", "100.00",
                RefundType.UNDUE_CHARGE, Source.P3_2_HYBRID_SECOND_PAYMENT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- afastamento do dobro PELO FLUXO de abertura (M1) ----------

    @Test
    void aberturaAfastandoODobroSemJustificativaERecusadaPeloFluxo() {
        // Cobranca indevida de 87,50 com o dobro LIGADO; o operador pede o
        // afastamento (doubleWaived) SEM justificativa -> recusa na abertura.
        var in = new OpeningInput(OS, Source.MANUAL_REQUEST, RefundType.UNDUE_CHARGE, PAYMENT,
                new BigDecimal("87.50"), new BigDecimal("87.50"), null, DOUBLE_ON, null, true);
        assertThatThrownBy(() -> RefundRequestRule.open(in))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa obrigatoria");
    }

    @Test
    void aberturaAfastandoODobroComJustificativaDevolveOValorSimples() {
        // Mesma abertura, COM justificativa -> READY com o valor simples 87,50.
        var in = new OpeningInput(OS, Source.MANUAL_REQUEST, RefundType.UNDUE_CHARGE, PAYMENT,
                new BigDecimal("87.50"), new BigDecimal("87.50"), null, DOUBLE_ON,
                "Acordo documentado com o consumidor", true);
        var d = RefundRequestRule.open(in);
        assertThat(d.isPending()).isFalse();
        assertThat(d.calculated().refundAmount()).isEqualByComparingTo("87.50");
        assertThat(d.calculated().doubleApplied()).isFalse();
        // Sem pedido de afastamento, a mesma cobranca devolve o dobro: 175,00.
        var semAfastamento = RefundRequestRule.open(opening(PAYMENT, "87.50", "87.50", null,
                RefundType.UNDUE_CHARGE, Source.MANUAL_REQUEST));
        assertThat(semAfastamento.calculated().refundAmount()).isEqualByComparingTo("175.00");
        assertThat(semAfastamento.calculated().doubleApplied()).isTrue();
    }

    // ---------- encerramento: fabricas sobre a assinatura fiada (M2) ----------

    private static final class FakeStore implements RefundClosureService.RefundExecutionStore {
        final Map<UUID, Execution> byOs = new HashMap<>();
        final AtomicInteger saves = new AtomicInteger();

        @Override
        public Optional<Execution> findByServiceOrderId(UUID tenantId, UUID serviceOrderId) {
            return Optional.ofNullable(byOs.get(serviceOrderId));
        }

        @Override
        public Execution save(Execution execution) {
            saves.incrementAndGet();
            var saved = new Execution(UUID.randomUUID(), execution.tenantId(), execution.refundRequestId(),
                    execution.serviceOrderId(), execution.effect(), execution.amount(),
                    execution.creditedBillId(), execution.bankReference());
            byOs.put(saved.serviceOrderId(), saved);
            return saved;
        }
    }

    /** Titularidade bancaria de teste: a conta pertence ao CPF do titular. */
    private static final RefundDestinationValidator.BankAccountHolderPort HOLDER_OK =
            (bank, branch, account, doc) -> doc.equals(CPF);

    private static RefundClosureService service(FakeStore store,
                                                RefundClosureService.BankRefundPort bankPort) {
        return new RefundClosureService(store, bankPort, HOLDER_OK);
    }

    /** Aprovacao simples registrada (abaixo da alcada): um aprovador != solicitante. */
    private static ApprovalData simpleApproval() {
        return new ApprovalData(REQUESTER, APPROVER_A, null, THRESHOLD);
    }

    /** Sem dobro em jogo: duplicidade, dobro ligado, nada afastado. */
    private static DoubleRefundData noDouble() {
        return new DoubleRefundData(RefundType.DUPLICATE_PAYMENT, DOUBLE_ON, false, null);
    }

    /** Destino PIX_KEY valido: chave CPF igual ao documento do titular. */
    private static DestinationData pixKeyDestination(String ciphertext) {
        return new DestinationData(RefundDestination.PIX_KEY, ciphertext, null, CPF, CPF,
                null, null, null);
    }

    private static ClosureInput creditClosure(UUID refundId, String amount, ApprovalData approval) {
        return new ClosureInput(TENANT, refundId, OS, "APROVADA",
                RefundMode.CREDIT_NEXT_BILL, null, new BigDecimal(amount), UUID.randomUUID(),
                approval, noDouble(), null);
    }

    private static ClosureInput bankClosure(UUID refundId, String amount, ApprovalData approval,
                                            DestinationData destination) {
        return new ClosureInput(TENANT, refundId, OS, "APROVADA",
                RefundMode.BANK_TRANSFER, null, new BigDecimal(amount), null,
                approval, noDouble(), destination);
    }

    // ---------- encerramento: um teste por opcao de pay.refund_mode ----------

    @Test
    void modoCreditNextBillCreditaNaContaSeguinte() {
        var store = new FakeStore();
        var service = service(store, (t, r, a, d) -> "NUNCA");
        var result = service.close(creditClosure(UUID.randomUUID(), "100.00", simpleApproval()));
        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(result.execution().effect()).isEqualTo("NEXT_BILL_CREDIT");
        assertThat(result.execution().creditedBillId()).isNotNull();
        assertThat(result.execution().bankReference()).isNull();
    }

    @Test
    void modoBankTransferDevolvePeloBancoComReferenciaEDestinoCifrado() {
        var store = new FakeStore();
        var destinoRecebido = new AtomicReference<String>();
        var service = service(store, (t, r, a, d) -> {
            destinoRecebido.set(d);
            return "PSP-REF-123";
        });
        var result = service.close(bankClosure(UUID.randomUUID(), "175.00", simpleApproval(),
                pixKeyDestination(CIPHERTEXT)));
        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(result.execution().effect()).isEqualTo("BANK_REFUND");
        assertThat(result.execution().bankReference()).isEqualTo("PSP-REF-123");
        assertThat(result.execution().amount()).isEqualByComparingTo("175.00");
        // A porta bancaria recebeu o destino CIFRADO gravado (M2), nao texto claro.
        assertThat(destinoRecebido.get()).isEqualTo(CIPHERTEXT);
    }

    @Test
    void modoUserChoiceExigeAEscolhaDoUsuario() {
        var store = new FakeStore();
        var service = service(store, (t, r, a, d) -> "PSP-REF-9");
        // Sem escolha: erro explicito, nada e executado.
        assertThatThrownBy(() -> service.close(new ClosureInput(TENANT, UUID.randomUUID(), OS, "APROVADA",
                RefundMode.USER_CHOICE, null, new BigDecimal("100.00"), UUID.randomUUID(),
                simpleApproval(), noDouble(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USER_CHOICE");
        assertThat(store.saves.get()).isZero();
        // Com a escolha por credito: encerra como credito.
        var result = service.close(new ClosureInput(TENANT, UUID.randomUUID(), OS, "APROVADA",
                RefundMode.USER_CHOICE, RefundMode.CREDIT_NEXT_BILL,
                new BigDecimal("100.00"), UUID.randomUUID(), simpleApproval(), noDouble(), null));
        assertThat(result.execution().effect()).isEqualTo("NEXT_BILL_CREDIT");
    }

    @Test
    void reenvioDaOsNaoDuplicaCreditoNemDevolucao() {
        var store = new FakeStore();
        var bankCalls = new AtomicInteger();
        var service = service(store, (t, r, a, d) -> {
            bankCalls.incrementAndGet();
            return "PSP-REF-1";
        });
        UUID refund = UUID.randomUUID();
        var input = bankClosure(refund, "100.00", simpleApproval(), pixKeyDestination(CIPHERTEXT));
        var first = service.close(input);
        var resent = service.close(input);
        assertThat(first.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(resent.outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(resent.execution().id()).isEqualTo(first.execution().id());
        assertThat(store.saves.get()).isEqualTo(1);
        assertThat(bankCalls.get()).isEqualTo(1);
    }

    @Test
    void devolucaoBancariaExigeDestinoCifradoGravado() {
        assertThatThrownBy(() -> RefundRequestRule.requireDestinationForBankRefund(
                RefundMode.BANK_TRANSFER, RefundDestination.PIX_KEY, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefundRequestRule.requireDestinationForBankRefund(
                RefundMode.BANK_TRANSFER, null, "cifrado"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- encerramento fiado (M2): status, aprovacao, destino, dobro ----------

    @Test
    void encerramentoSemDestinoBancarioERecusadoPeloServico() {
        var store = new FakeStore();
        var bankCalls = new AtomicInteger();
        var service = service(store, (t, r, a, d) -> {
            bankCalls.incrementAndGet();
            return "X";
        });
        // Sem DestinationData nenhuma.
        assertThatThrownBy(() -> service.close(bankClosure(UUID.randomUUID(), "100.00",
                simpleApproval(), null)))
                .isInstanceOf(IllegalArgumentException.class);
        // Com destino, mas SEM o texto cifrado gravado.
        assertThatThrownBy(() -> service.close(bankClosure(UUID.randomUUID(), "100.00",
                simpleApproval(), pixKeyDestination(" "))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destino cifrado");
        // Com cifrado, mas destino INVALIDO: chave Pix e-mail nao e CPF/CNPJ.
        assertThatThrownBy(() -> service.close(bankClosure(UUID.randomUUID(), "100.00",
                simpleApproval(), new DestinationData(RefundDestination.PIX_KEY, CIPHERTEXT,
                        null, "titular@exemplo.com", CPF, null, null, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF ou CNPJ");
        assertThat(store.saves.get()).isZero();
        assertThat(bankCalls.get()).isZero();
    }

    @Test
    void encerramentoSemAprovacaoERecusado() {
        var service = service(new FakeStore(), (t, r, a, d) -> "X");
        // Nenhum aprovador registrado.
        assertThatThrownBy(() -> service.close(creditClosure(UUID.randomUUID(), "100.00",
                new ApprovalData(REQUESTER, null, null, THRESHOLD))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem nenhuma aprovacao");
        // Solicitante aprovando a propria devolucao (guarda da P3.1 reutilizada).
        assertThatThrownBy(() -> service.close(creditClosure(UUID.randomUUID(), "100.00",
                new ApprovalData(REQUESTER, REQUESTER, null, THRESHOLD))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aprovacaoInsuficienteAcimaDaAlcadaERecusadaECaminhoFelizExecutaUmaVez() {
        var store = new FakeStore();
        var bankCalls = new AtomicInteger();
        var service = service(store, (t, r, a, d) -> {
            bankCalls.incrementAndGet();
            return "PSP-REF-600";
        });
        UUID refund = UUID.randomUUID();
        // 600,00 > alcada 500,00: um aprovador so NAO basta.
        assertThatThrownBy(() -> service.close(bankClosure(refund, "600.00",
                new ApprovalData(REQUESTER, APPROVER_A, null, THRESHOLD),
                pixKeyDestination(CIPHERTEXT))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aprovacao dupla");
        assertThat(store.saves.get()).isZero();
        assertThat(bankCalls.get()).isZero();
        // Dois aprovadores distintos: caminho feliz executa UMA vez.
        var input = bankClosure(refund, "600.00",
                new ApprovalData(REQUESTER, APPROVER_A, APPROVER_B, THRESHOLD),
                pixKeyDestination(CIPHERTEXT));
        var result = service.close(input);
        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(result.execution().amount()).isEqualByComparingTo("600.00");
        assertThat(service.close(input).outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(store.saves.get()).isEqualTo(1);
        assertThat(bankCalls.get()).isEqualTo(1);
    }

    @Test
    void encerramentoDeDevolucaoPendenteERecusadoPeloStatus() {
        var store = new FakeStore();
        var service = service(store, (t, r, a, d) -> "X");
        var pendente = new ClosureInput(TENANT, UUID.randomUUID(), OS, "PENDENTE",
                RefundMode.CREDIT_NEXT_BILL, null, new BigDecimal("100.00"), UUID.randomUUID(),
                simpleApproval(), noDouble(), null);
        assertThatThrownBy(() -> service.close(pendente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APROVADA");
        assertThat(store.saves.get()).isZero();
    }

    @Test
    void encerramentoAfastandoODobroSemJustificativaERecusadoEComJustificativaExecutaSimples() {
        var store = new FakeStore();
        var service = service(store, (t, r, a, d) -> "X");
        // Cobranca indevida, dobro ligado, NAO aplicado e SEM justificativa (M1 no encerramento).
        var semJustificativa = new ClosureInput(TENANT, UUID.randomUUID(), OS, "APROVADA",
                RefundMode.CREDIT_NEXT_BILL, null, new BigDecimal("87.50"), UUID.randomUUID(),
                simpleApproval(),
                new DoubleRefundData(RefundType.UNDUE_CHARGE, DOUBLE_ON, false, " "), null);
        assertThatThrownBy(() -> service.close(semJustificativa))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa obrigatoria");
        assertThat(store.saves.get()).isZero();
        // COM justificativa: encerra no valor simples 87,50.
        var comJustificativa = new ClosureInput(TENANT, UUID.randomUUID(), OS, "APROVADA",
                RefundMode.CREDIT_NEXT_BILL, null, new BigDecimal("87.50"), UUID.randomUUID(),
                simpleApproval(),
                new DoubleRefundData(RefundType.UNDUE_CHARGE, DOUBLE_ON, false,
                        "Acordo documentado com o consumidor"), null);
        var result = service.close(comJustificativa);
        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(result.execution().amount()).isEqualByComparingTo("87.50");
    }

    @Test
    void pixReturnToPayerNoEncerramentoExigePspQueOferece() {
        var store = new FakeStore();
        var service = service(store, (t, r, a, d) -> "PSP-REF-RET");
        // PSP NAO oferece a devolucao ao pagador: o validador recusa no encerramento.
        var naoOferece = new DestinationData(RefundDestination.PIX_RETURN_TO_PAYER, CIPHERTEXT,
                new PixOrigin("PIX", "E12345678202601011234567890ABCDE", false),
                null, CPF, null, null, null);
        assertThatThrownBy(() -> service.close(bankClosure(UUID.randomUUID(), "100.00",
                simpleApproval(), naoOferece)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao oferece");
        // PSP oferece + pagamento original Pix: encerra.
        var oferece = new DestinationData(RefundDestination.PIX_RETURN_TO_PAYER, CIPHERTEXT,
                new PixOrigin("PIX", "E12345678202601011234567890ABCDE", true),
                null, CPF, null, null, null);
        var result = service.close(bankClosure(UUID.randomUUID(), "100.00",
                simpleApproval(), oferece));
        assertThat(result.execution().bankReference()).isEqualTo("PSP-REF-RET");
    }
}
