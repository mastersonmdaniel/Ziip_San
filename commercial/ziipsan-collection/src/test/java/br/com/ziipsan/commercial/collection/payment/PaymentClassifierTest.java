package br.com.ziipsan.commercial.collection.payment;

import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.AdjustmentKind;
import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.Document;
import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.DocumentCondition;
import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.DuplicateWinner;
import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.Outcome;
import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.Payment;
import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.Policy;
import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos calculados à mão. Política base: tolerância de classificação R$ 0,03, de ajuste R$ 2,00,
 * vence o pagamento mais antigo. Documento base: R$ 100,00, normal, ciclo contábil fechado.
 *
 * <p>Regra: {@code total = soma dos pagamentos - excedente dos TO_WRITE_DOWN}; {@code dif = total -
 * documento}. {@code |dif| <= 0,03} classifica; até 2,00 classifica e gera ajuste (crédito a
 * realizar se a maior, débito a cobrar se a menor); além disso, a menor é AMOUNT_MISMATCH e a
 * maior separa o pagamento de valor igual ao documento (válido), os demais iguais (DUPLICATE) e os
 * de outro valor (AMOUNT_MISMATCH); se ninguém iguala o documento, um pagamento único é EXCESS.
 */
class PaymentClassifierTest {

    private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_03 = LocalDate.of(2026, 10, 3);
    private static final LocalDate OCT_05 = LocalDate.of(2026, 10, 5);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static Policy base() {
        return new Policy(d("0.03"), d("2.00"), DuplicateWinner.OLDEST_PAYMENT);
    }

    private static Optional<Document> doc100() {
        return Optional.of(new Document(d("100.00"), DocumentCondition.NORMAL, false));
    }

    private static Payment pay(String id, String amount, LocalDate on) {
        return new Payment(id, d(amount), on, PaymentStatus.OPEN, BigDecimal.ZERO, true);
    }

    private static Payment pay(String id, String amount) {
        return pay(id, amount, OCT_01);
    }

    private static Result run(Policy p, Optional<Document> doc, Payment... payments) {
        return new PaymentClassifier(p).classify(doc, Arrays.asList(payments));
    }

    private static Result run(Optional<Document> doc, Payment... payments) {
        return run(base(), doc, payments);
    }

    private static Outcome only(Result r) {
        assertThat(r.outcomes()).hasSize(1);
        return r.outcomes().get(0);
    }

    // ---------------------------------------------------------- documento ausente ou em outro estado

    @Test
    @DisplayName("1. sem documento e sem unidade: DOC_NOT_FOUND, não identificado")
    void noDocumentUnidentified() {
        Result r = run(Optional.empty(), new Payment("p1", d("100.00"), OCT_01, PaymentStatus.OPEN, BigDecimal.ZERO, false));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.DOC_NOT_FOUND);
        assertThat(only(r).identified()).isFalse();
        assertThat(r.documentSettled()).isFalse();
    }

    @Test
    @DisplayName("2. sem documento mas com unidade identificada: DOC_NOT_FOUND, identificado")
    void noDocumentButUnitKnown() {
        Result r = run(Optional.empty(), pay("p1", "100.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.DOC_NOT_FOUND);
        assertThat(only(r).identified()).isTrue();
    }

    @Test
    @DisplayName("3. documento prescrito")
    void prescribed() {
        Result r = run(Optional.of(new Document(d("100.00"), DocumentCondition.PRESCRIBED, false)), pay("p1", "100.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.DOC_PRESCRIBED);
    }

    @Test
    @DisplayName("4. documento parcelado")
    void parceled() {
        Result r = run(Optional.of(new Document(d("100.00"), DocumentCondition.PARCELED, false)), pay("p1", "100.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.DOC_PARCELED);
    }

    @Test
    @DisplayName("5. documento cancelado")
    void canceled() {
        Result r = run(Optional.of(new Document(d("100.00"), DocumentCondition.CANCELED, false)), pay("p1", "100.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.DOC_CANCELED);
    }

    @Test
    @DisplayName("6. documento com erro de processamento")
    void processingError() {
        Result r = run(Optional.of(new Document(d("100.00"), DocumentCondition.PROCESSING_ERROR, false)), pay("p1", "100.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.DOC_PROCESSING_ERROR);
    }

    @Test
    @DisplayName("7. ciclo contábil aberto: TO_ACCOUNT, sem olhar o valor")
    void accountingCycleOpen() {
        Result r = run(Optional.of(new Document(d("100.00"), DocumentCondition.NORMAL, true)), pay("p1", "37.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.TO_ACCOUNT);
    }

    // ---------------------------------------------------------- um pagamento, valor próximo

    @Test
    @DisplayName("8. valor exato: CLASSIFIED e documento quitado")
    void exact() {
        Result r = run(doc100(), pay("p1", "100.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.documentSettled()).isTrue();
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.NONE);
        assertThat(only(r).excessAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("9. 100,03 (dif +0,03, limite inclusivo): CLASSIFIED sem ajuste")
    void plusThreeCentsBoundary() {
        Result r = run(doc100(), pay("p1", "100.03"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.NONE);
    }

    @Test
    @DisplayName("10. 99,97 (dif -0,03, limite inclusivo): CLASSIFIED sem ajuste")
    void minusThreeCentsBoundary() {
        Result r = run(doc100(), pay("p1", "99.97"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.NONE);
    }

    @Test
    @DisplayName("11. 100,04 (dif +0,04): CLASSIFIED com crédito a realizar de 0,04")
    void plusFourCents() {
        Result r = run(doc100(), pay("p1", "100.04"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.CREDIT_TO_REALIZE);
        assertThat(r.adjustmentAmount()).isEqualByComparingTo("0.04");
        assertThat(r.documentSettled()).isTrue();
    }

    @Test
    @DisplayName("12. 99,96 (dif -0,04): CLASSIFIED com débito a cobrar de 0,04")
    void minusFourCents() {
        Result r = run(doc100(), pay("p1", "99.96"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.DEBIT_TO_CHARGE);
        assertThat(r.adjustmentAmount()).isEqualByComparingTo("0.04");
    }

    @Test
    @DisplayName("13. 102,00 (dif +2,00, limite de ajuste inclusivo): crédito de 2,00")
    void plusTwoReaisBoundary() {
        Result r = run(doc100(), pay("p1", "102.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.CREDIT_TO_REALIZE);
        assertThat(r.adjustmentAmount()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("14. 98,00 (dif -2,00, limite de ajuste inclusivo): débito de 2,00")
    void minusTwoReaisBoundary() {
        Result r = run(doc100(), pay("p1", "98.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.DEBIT_TO_CHARGE);
        assertThat(r.adjustmentAmount()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("15. 102,01 (a maior além do ajuste, pagamento único): EXCESS de 2,01")
    void singleOverpaymentBeyondAdjust() {
        Result r = run(doc100(), pay("p1", "102.01"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.EXCESS);
        assertThat(only(r).excessAmount()).isEqualByComparingTo("2.01");
        assertThat(r.documentSettled()).isFalse();
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.NONE);
    }

    @Test
    @DisplayName("16. 97,99 (a menor além do ajuste): AMOUNT_MISMATCH")
    void singleUnderpaymentBeyondAdjust() {
        Result r = run(doc100(), pay("p1", "97.99"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
        assertThat(r.documentSettled()).isFalse();
    }

    @Test
    @DisplayName("17. 150,00 em documento de 100,00: EXCESS de 50,00")
    void bigOverpayment() {
        Result r = run(doc100(), pay("p1", "150.00"));
        assertThat(only(r).status()).isEqualTo(PaymentStatus.EXCESS);
        assertThat(only(r).excessAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("18. 50,00 em documento de 100,00: AMOUNT_MISMATCH")
    void bigUnderpayment() {
        assertThat(only(run(doc100(), pay("p1", "50.00"))).status()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
    }

    // ---------------------------------------------------------- vários pagamentos somados

    @Test
    @DisplayName("19. 60,00 + 40,00 = 100,00: os dois CLASSIFIED")
    void splitPaymentsSumToDocument() {
        Result r = run(doc100(), pay("p1", "60.00"), pay("p2", "40.00"));
        assertThat(r.outcomes()).extracting(Outcome::status).containsExactly(PaymentStatus.CLASSIFIED, PaymentStatus.CLASSIFIED);
        assertThat(r.documentSettled()).isTrue();
    }

    @Test
    @DisplayName("20. 60,00 + 40,02 = 100,02: dentro da tolerância, os dois CLASSIFIED sem ajuste")
    void splitPaymentsWithinTolerance() {
        Result r = run(doc100(), pay("p1", "60.00"), pay("p2", "40.02"));
        assertThat(r.outcomes()).extracting(Outcome::status).containsOnly(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.NONE);
    }

    @Test
    @DisplayName("21. 60,00 + 39,00 = 99,00: a menor dentro do ajuste, débito de 1,00")
    void splitPaymentsShortWithinAdjust() {
        Result r = run(doc100(), pay("p1", "60.00"), pay("p2", "39.00"));
        assertThat(r.outcomes()).extracting(Outcome::status).containsOnly(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.DEBIT_TO_CHARGE);
        assertThat(r.adjustmentAmount()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("22. 60,00 + 30,00 = 90,00: os dois AMOUNT_MISMATCH")
    void splitPaymentsShort() {
        Result r = run(doc100(), pay("p1", "60.00"), pay("p2", "30.00"));
        assertThat(r.outcomes()).extracting(Outcome::status).containsOnly(PaymentStatus.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("23. 60,00 + 42,00 = 102,00: a maior dentro do ajuste, crédito de 2,00")
    void splitPaymentsOverWithinAdjust() {
        Result r = run(doc100(), pay("p1", "60.00"), pay("p2", "42.00"));
        assertThat(r.outcomes()).extracting(Outcome::status).containsOnly(PaymentStatus.CLASSIFIED);
        assertThat(r.adjustment()).isEqualTo(AdjustmentKind.CREDIT_TO_REALIZE);
        assertThat(r.adjustmentAmount()).isEqualByComparingTo("2.00");
    }

    // ---------------------------------------------------------- duplicidade

    @Test
    @DisplayName("24. dois pagamentos de 100,00 (01/10 e 03/10): o mais antigo é válido, o outro DUPLICATE com excedente 100,00")
    void duplicateOldestWins() {
        Result r = run(doc100(), pay("p1", "100.00", OCT_01), pay("p2", "100.00", OCT_03));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.DUPLICATE);
        assertThat(r.outcomes().get(1).excessAmount()).isEqualByComparingTo("100.00");
        assertThat(r.documentSettled()).isTrue();
    }

    @Test
    @DisplayName("25. mesma duplicidade com política 'o mais recente vence': inverte")
    void duplicateNewestWins() {
        Policy newest = new Policy(d("0.03"), d("2.00"), DuplicateWinner.NEWEST_PAYMENT);
        Result r = run(newest, doc100(), pay("p1", "100.00", OCT_01), pay("p2", "100.00", OCT_03));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.DUPLICATE);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.CLASSIFIED);
    }

    @Test
    @DisplayName("26. a ordem de chegada não decide: o mais antigo vence mesmo vindo depois na lista")
    void duplicateOrderOfArrivalDoesNotMatter() {
        Result r = run(doc100(), pay("late", "100.00", OCT_05), pay("early", "100.00", OCT_01));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.DUPLICATE);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.CLASSIFIED);
    }

    @Test
    @DisplayName("27. empate de data: vence o primeiro da lista (desempate estável)")
    void duplicateTieBreakIsStable() {
        Result r = run(doc100(), pay("p1", "100.00", OCT_01), pay("p2", "100.00", OCT_01));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.DUPLICATE);
    }

    @Test
    @DisplayName("28. três pagamentos de 100,00: um CLASSIFIED e dois DUPLICATE de 100,00 cada")
    void tripleDuplicate() {
        Result r = run(doc100(), pay("p1", "100.00", OCT_01), pay("p2", "100.00", OCT_03), pay("p3", "100.00", OCT_05));
        assertThat(r.outcomes()).extracting(Outcome::status)
                .containsExactly(PaymentStatus.CLASSIFIED, PaymentStatus.DUPLICATE, PaymentStatus.DUPLICATE);
        assertThat(r.outcomes().get(2).excessAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("29. 100,00 e 100,02: o segundo iguala o documento pela tolerância; duplicidade com excedente 100,02")
    void duplicateWithinToleranceOfDocument() {
        Result r = run(doc100(), pay("p1", "100.00", OCT_01), pay("p2", "100.02", OCT_03));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.DUPLICATE);
        assertThat(r.outcomes().get(1).excessAmount()).isEqualByComparingTo("100.02");
    }

    @Test
    @DisplayName("30. 100,00 + 40,00: o exato é válido; o de 40,00 fica AMOUNT_MISMATCH")
    void exactPlusStrayPayment() {
        Result r = run(doc100(), pay("p1", "100.00", OCT_01), pay("p2", "40.00", OCT_03));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
        assertThat(r.documentSettled()).isTrue();
    }

    @Test
    @DisplayName("31. 100,00 + 101,00: o exato é válido; o de 101,00 é AMOUNT_MISMATCH, não duplicidade")
    void exactPlusLargerPayment() {
        Result r = run(doc100(), pay("p1", "100.00", OCT_01), pay("p2", "101.00", OCT_03));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("32. 150,00 + 60,00 (total 210,00, nenhum igual ao documento): todos AMOUNT_MISMATCH")
    void noPaymentMatchesTheDocument() {
        Result r = run(doc100(), pay("p1", "150.00"), pay("p2", "60.00"));
        assertThat(r.outcomes()).extracting(Outcome::status).containsOnly(PaymentStatus.AMOUNT_MISMATCH);
        assertThat(r.documentSettled()).isFalse();
    }

    // ---------------------------------------------------------- estados que não entram no total

    @Test
    @DisplayName("33. duplicidade já devolvida fica como está e não entra no total")
    void alreadyRefundedIsKept() {
        Payment refunded = new Payment("old", d("100.00"), OCT_01, PaymentStatus.DUPLICATE_REFUNDED, BigDecimal.ZERO, true);
        Result r = run(doc100(), refunded, pay("p2", "100.00", OCT_03));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.DUPLICATE_REFUNDED);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.documentSettled()).isTrue();
    }

    @Test
    @DisplayName("34. recuperação de crédito (cancelado e duplicado) também fica como está")
    void creditRecoveryIsKept() {
        Payment rc = new Payment("rc", d("100.00"), OCT_01, PaymentStatus.CREDIT_RECOVERY_CANCELED, BigDecimal.ZERO, true);
        Payment rd = new Payment("rd", d("100.00"), OCT_01, PaymentStatus.CREDIT_RECOVERY_DUPLICATE, BigDecimal.ZERO, true);
        Result r = run(doc100(), rc, pay("p3", "100.00"), rd);
        assertThat(r.outcomes()).extracting(Outcome::status).containsExactly(
                PaymentStatus.CREDIT_RECOVERY_CANCELED, PaymentStatus.CLASSIFIED, PaymentStatus.CREDIT_RECOVERY_DUPLICATE);
    }

    @Test
    @DisplayName("35. excedente aguardando baixa (TO_WRITE_DOWN de 140,00 com 40,00 de excedente) vale 100,00 no total")
    void writeDownExcessIsDeducted() {
        Payment p = new Payment("p1", d("140.00"), OCT_01, PaymentStatus.TO_WRITE_DOWN, d("40.00"), true);
        Result r = run(doc100(), p);
        assertThat(only(r).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.documentSettled()).isTrue();
    }

    @Test
    @DisplayName("36. TO_WRITE_DOWN de 140,00/40,00 mais um novo de 100,00: os dois valem 100,00, o mais antigo é válido")
    void writeDownPlusNewPayment() {
        Payment p1 = new Payment("p1", d("140.00"), OCT_01, PaymentStatus.TO_WRITE_DOWN, d("40.00"), true);
        Result r = run(doc100(), p1, pay("p2", "100.00", OCT_03));
        assertThat(r.outcomes().get(0).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(r.outcomes().get(1).status()).isEqualTo(PaymentStatus.DUPLICATE);
        assertThat(r.outcomes().get(1).excessAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("37. reclassificar o que já estava CLASSIFIED ou DUPLICATE dá o mesmo resultado (idempotente)")
    void reclassificationIsIdempotent() {
        Payment p1 = new Payment("p1", d("100.00"), OCT_01, PaymentStatus.CLASSIFIED, BigDecimal.ZERO, true);
        Payment p2 = new Payment("p2", d("100.00"), OCT_03, PaymentStatus.DUPLICATE, BigDecimal.ZERO, true);
        Result r = run(doc100(), p1, p2);
        assertThat(r.outcomes()).extracting(Outcome::status)
                .containsExactly(PaymentStatus.CLASSIFIED, PaymentStatus.DUPLICATE);
    }

    @Test
    @DisplayName("38. sem pagamentos: resultado vazio e documento não quitado")
    void noPayments() {
        Result r = run(doc100());
        assertThat(r.outcomes()).isEmpty();
        assertThat(r.documentSettled()).isFalse();
    }

    @Test
    @DisplayName("39. a ordem dos resultados segue a ordem de entrada, com o excluído no meio")
    void outcomesKeepInputOrder() {
        Payment refunded = new Payment("mid", d("100.00"), OCT_01, PaymentStatus.DUPLICATE_REFUNDED, BigDecimal.ZERO, true);
        Result r = run(doc100(), pay("a", "60.00"), refunded, pay("b", "40.00"));
        assertThat(r.outcomes()).extracting(Outcome::paymentId).containsExactly("a", "mid", "b");
        assertThat(r.outcomes()).extracting(Outcome::status).containsExactly(
                PaymentStatus.CLASSIFIED, PaymentStatus.DUPLICATE_REFUNDED, PaymentStatus.CLASSIFIED);
    }

    // ---------------------------------------------------------- tolerâncias do tenant

    @Test
    @DisplayName("40. tolerâncias zero: 100,01 vira EXCESS de 0,01 e 99,99 vira AMOUNT_MISMATCH")
    void zeroTolerances() {
        Policy strict = new Policy(d("0.00"), d("0.00"), DuplicateWinner.OLDEST_PAYMENT);
        Result over = run(strict, doc100(), pay("p1", "100.01"));
        assertThat(only(over).status()).isEqualTo(PaymentStatus.EXCESS);
        assertThat(only(over).excessAmount()).isEqualByComparingTo("0.01");
        assertThat(only(run(strict, doc100(), pay("p1", "99.99"))).status()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
        assertThat(only(run(strict, doc100(), pay("p1", "100.00"))).status()).isEqualTo(PaymentStatus.CLASSIFIED);
    }

    @Test
    @DisplayName("41. tolerâncias 0,50 e 5,00: 100,50 classifica; 100,51 gera crédito de 0,51; 105,00 gera crédito; 105,01 é EXCESS")
    void customTolerances() {
        Policy custom = new Policy(d("0.50"), d("5.00"), DuplicateWinner.OLDEST_PAYMENT);
        assertThat(run(custom, doc100(), pay("p", "100.50")).adjustment()).isEqualTo(AdjustmentKind.NONE);
        Result credit = run(custom, doc100(), pay("p", "100.51"));
        assertThat(credit.adjustment()).isEqualTo(AdjustmentKind.CREDIT_TO_REALIZE);
        assertThat(credit.adjustmentAmount()).isEqualByComparingTo("0.51");
        assertThat(run(custom, doc100(), pay("p", "105.00")).adjustmentAmount()).isEqualByComparingTo("5.00");
        Result excess = run(custom, doc100(), pay("p", "105.01"));
        assertThat(only(excess).status()).isEqualTo(PaymentStatus.EXCESS);
        assertThat(only(excess).excessAmount()).isEqualByComparingTo("5.01");
    }

    @Test
    @DisplayName("42. as mesmas contas com as tolerâncias do GSAN (0,03 e 2,00) não valem para quem configurou outras")
    void toleranceIsPerTenantNotHardcoded() {
        Policy tenantA = base();
        Policy tenantB = new Policy(d("0.10"), d("0.10"), DuplicateWinner.OLDEST_PAYMENT);
        // 100,08: A gera crédito de 0,08; B classifica sem ajuste
        assertThat(run(tenantA, doc100(), pay("p", "100.08")).adjustment()).isEqualTo(AdjustmentKind.CREDIT_TO_REALIZE);
        assertThat(run(tenantB, doc100(), pay("p", "100.08")).adjustment()).isEqualTo(AdjustmentKind.NONE);
    }

    @Test
    @DisplayName("43. documento de valor zero: 0,02 classifica; 1,00 é crédito de 1,00; 3,00 é EXCESS de 3,00")
    void zeroValueDocument() {
        Optional<Document> zero = Optional.of(new Document(d("0.00"), DocumentCondition.NORMAL, false));
        assertThat(only(run(zero, pay("p", "0.02"))).status()).isEqualTo(PaymentStatus.CLASSIFIED);
        assertThat(run(zero, pay("p", "1.00")).adjustment()).isEqualTo(AdjustmentKind.CREDIT_TO_REALIZE);
        assertThat(only(run(zero, pay("p", "3.00"))).status()).isEqualTo(PaymentStatus.EXCESS);
    }

    // ---------------------------------------------------------- validação e catálogo

    @Test
    @DisplayName("44. política exige tolerâncias válidas e regra de duplicidade")
    void policyValidation() {
        assertThatThrownBy(() -> new Policy(d("-0.01"), d("2.00"), DuplicateWinner.OLDEST_PAYMENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Policy(d("0.50"), d("0.10"), DuplicateWinner.OLDEST_PAYMENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Policy(null, d("2.00"), DuplicateWinner.OLDEST_PAYMENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Policy(d("0.03"), null, DuplicateWinner.OLDEST_PAYMENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Policy(d("0.03"), d("2.00"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentClassifier(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("45. pagamento sem id, valor zero ou negativo, ou sem data é recusado")
    void paymentValidation() {
        assertThatThrownBy(() -> pay(" ", "10.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pay("p", "0.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pay("p", "-5.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Payment("p", d("1"), null, PaymentStatus.OPEN, BigDecimal.ZERO, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Document(null, DocumentCondition.NORMAL, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("46. o catálogo tem 15 situações; 10 saem do classificador e 5 vêm de outros fluxos")
    void statusCatalog() {
        assertThat(PaymentStatus.values()).hasSize(15);
        long byClassifier = Arrays.stream(PaymentStatus.values()).filter(PaymentStatus::assignedByClassifier).count();
        assertThat(byClassifier).isEqualTo(10);
        assertThat(Arrays.stream(PaymentStatus.values()).filter(s -> !s.assignedByClassifier()))
                .containsExactlyInAnyOrder(PaymentStatus.TO_WRITE_DOWN, PaymentStatus.OPEN, PaymentStatus.DUPLICATE_REFUNDED,
                        PaymentStatus.CREDIT_RECOVERY_CANCELED, PaymentStatus.CREDIT_RECOVERY_DUPLICATE);
        assertThat(List.of(PaymentStatus.DUPLICATE_REFUNDED, PaymentStatus.CREDIT_RECOVERY_CANCELED,
                PaymentStatus.CREDIT_RECOVERY_DUPLICATE)).allMatch(PaymentStatus::isSettledElsewhere);
        assertThat(PaymentStatus.OPEN.isSettledElsewhere()).isFalse();
    }

    @Test
    @DisplayName("47. tudo o que o classificador devolve está no conjunto de situações que ele pode atribuir")
    void classifierOnlyProducesItsOwnStatuses() {
        List<Result> results = List.of(
                run(Optional.empty(), pay("p", "100.00")),
                run(doc100(), pay("p", "100.00")),
                run(doc100(), pay("p", "100.04")),
                run(doc100(), pay("p", "150.00")),
                run(doc100(), pay("p", "50.00")),
                run(doc100(), pay("p", "100.00", OCT_01), pay("q", "100.00", OCT_03)),
                run(Optional.of(new Document(d("100.00"), DocumentCondition.NORMAL, true)), pay("p", "100.00")));
        for (Result r : results) {
            for (Outcome o : r.outcomes()) {
                assertThat(o.status().assignedByClassifier()).as(o.status().name()).isTrue();
            }
        }
    }
}
