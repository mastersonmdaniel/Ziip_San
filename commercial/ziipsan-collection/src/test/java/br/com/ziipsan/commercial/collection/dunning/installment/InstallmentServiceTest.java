package br.com.ziipsan.commercial.collection.dunning.installment;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation;
import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.collection.dunning.DunningTestSupport.FakeBills;
import static br.com.ziipsan.commercial.collection.dunning.DunningTestSupport.FakeMinimumLevels;
import static br.com.ziipsan.commercial.collection.dunning.DunningTestSupport.FakeSignatures;
import static br.com.ziipsan.commercial.collection.dunning.DunningTestSupport.FakeTemplates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ciclo de vida do parcelamento (DAD-21): criacao com os limites do
 * perfil, confirmacao com assinatura, desfazer restaurando as contas,
 * reparcelamento e importacao do plano homologado.
 */
class InstallmentServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID BILL1 = UUID.randomUUID();
    private static final UUID BILL2 = UUID.randomUUID();
    private static final LocalDate CREATION = LocalDate.of(2026, 11, 7);

    private final FakeTemplates templates = new FakeTemplates();
    private final FakeBills billsPort = new FakeBills();
    private final FakeSignatures signatures = new FakeSignatures("ADVANCED");
    private final FakeMinimumLevels minimumLevels = new FakeMinimumLevels("ADVANCED");
    private final InstallmentService service =
            new InstallmentService(templates, signatures, billsPort, minimumLevels);

    /** Permissao REGISTRADA completa (quem, quando, referencia, minimos) — D5. */
    private static final InstallmentAgreement.MinimumWaiver FULL_WAIVER =
            new InstallmentAgreement.MinimumWaiver(UUID.randomUUID(),
                    java.time.Instant.parse("2026-11-06T10:00:00Z"), "despacho-123/2026",
                    java.util.Set.of(InstallmentAgreement.WaivedMinimum.DOWN_PAYMENT_AMOUNT,
                            InstallmentAgreement.WaivedMinimum.DOWN_PAYMENT_DUE_DATE));

    private static InstallmentPolicy policy() {
        return new InstallmentPolicy(UUID.randomUUID(),
                new BigDecimal("20"), new BigDecimal("50.00"), 7, 24,
                new BigDecimal("30.00"), new BigDecimal("30"), BigDecimal.ZERO);
    }

    private static InstallmentService.CreationRequest baseRequest() {
        return new InstallmentService.CreationRequest(
                TENANT, UNIT, CUSTOMER, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new InstallmentAgreement.IncludedBill(BILL1, new BigDecimal("600")),
                        new InstallmentAgreement.IncludedBill(BILL2, new BigDecimal("400"))),
                new BigDecimal("100"), InstallmentAgreement.Discounts.none(),
                new BigDecimal("220"), CREATION.plusDays(7),
                10, LocalDate.of(2026, 12, 7),
                InstallmentEnums.InterestMethod.NONE, BigDecimal.ZERO,
                policy(), CREATION, null, null, null);
    }

    private static InstallmentService.CreationRequest withDown(BigDecimal down, boolean permitted) {
        var b = baseRequest();
        return new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(), b.discounts(),
                down, b.downPaymentDueDate(), b.installmentCount(), b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(),
                permitted ? FULL_WAIVER : null, b.replacesAgreementId(), b.legacyReference());
    }

    @Test
    @DisplayName("criacao padrao: PENDING_CONFIRMATION, perfil versionado, demonstrativo e termo gerados")
    void createStandard() {
        var agreement = service.create(baseRequest());
        assertThat(agreement.status()).isEqualTo(InstallmentEnums.Status.PENDING_CONFIRMATION);
        assertThat(agreement.policyVersionId()).isNotNull();
        assertThat(agreement.includedTotal()).isEqualByComparingTo("1000");
        assertThat(agreement.totalDebt()).isEqualByComparingTo("1100");
        assertThat(agreement.financedAmount()).isEqualByComparingTo("880");
        assertThat(agreement.schedule()).hasSize(10);
        assertThat(agreement.schedule().stream().map(InstallmentCalculator.Installment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("880");
        assertThat(templates.renderedCodes).containsExactly(
                InstallmentService.STATEMENT_TEMPLATE, InstallmentService.TERM_TEMPLATE);
        assertThat(agreement.termTemplateVersion()).isEqualTo("v3");
    }

    @Test
    @DisplayName("sem protocolo ou sem OS: recusado (ADR-0039)")
    void requiresProtocolAndOrder() {
        var b = baseRequest();
        var noProtocol = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                null, b.serviceOrderId(), b.includedBills(), b.totalCharges(), b.discounts(),
                b.downPayment(), b.downPaymentDueDate(), b.installmentCount(), b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(),
                null, null, null);
        assertThatThrownBy(() -> service.create(noProtocol)).isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("entrada abaixo do minimo: recusada sem permissao, aceita com permissao REGISTRADA gravada no acordo (D5)")
    void downPaymentMinimum() {
        assertThatThrownBy(() -> service.create(withDown(new BigDecimal("100"), false)))
                .isInstanceOf(ApiException.Validation.class);
        var waived = service.create(withDown(new BigDecimal("100"), true));
        assertThat(waived.downPayment()).isEqualByComparingTo("100");
        assertThat(waived.downPaymentWaiver()).isEqualTo(FULL_WAIVER);
        assertThat(waived.downPaymentWaiver().reference()).isEqualTo("despacho-123/2026");
        // fronteira: exatamente o minimo (20% de 1100 = 220) passa sem permissao
        assertThat(service.create(withDown(new BigDecimal("220"), false)).status())
                .isEqualTo(InstallmentEnums.Status.PENDING_CONFIRMATION);
    }

    @Test
    @DisplayName("parcelas acima do maximo e prestacao abaixo da minima: recusadas")
    void installmentLimits() {
        var b = baseRequest();
        var tooMany = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(), b.discounts(),
                b.downPayment(), b.downPaymentDueDate(), 25, b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(), null, null, null);
        assertThatThrownBy(() -> service.create(tooMany)).isInstanceOf(ApiException.Validation.class);

        var small = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(),
                List.of(new InstallmentAgreement.IncludedBill(BILL1, new BigDecimal("100"))),
                BigDecimal.ZERO, InstallmentAgreement.Discounts.none(),
                new BigDecimal("50"), CREATION.plusDays(7), 2, LocalDate.of(2026, 12, 7),
                InstallmentEnums.InterestMethod.NONE, BigDecimal.ZERO,
                policy(), CREATION, null, null, null);
        // financiado 50 em 2x = 25,00 < prestacao minima 30,00
        assertThatThrownBy(() -> service.create(small)).isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("abatimento acima do teto total e do principal fora do limite: recusados")
    void discountCaps() {
        var b = baseRequest();
        var overTotal = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(),
                new InstallmentAgreement.Discounts(new BigDecimal("400"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                b.downPayment(), b.downPaymentDueDate(), b.installmentCount(), b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(), null, null, null);
        // 400/1100 = 36,36% > teto de 30%
        assertThatThrownBy(() -> service.create(overTotal)).isInstanceOf(ApiException.Validation.class);

        var overPrincipal = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(),
                new InstallmentAgreement.Discounts(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("50")),
                b.downPayment(), b.downPaymentDueDate(), b.installmentCount(), b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(), null, null, null);
        // limite do principal do perfil = 0%: qualquer abatimento do principal recusa
        assertThatThrownBy(() -> service.create(overPrincipal)).isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("confirmacao assina o termo no nivel exigido e ativa; desfazer restaura as contas pelos valores originais")
    void confirmAndUndo() {
        var agreement = service.create(baseRequest());
        var active = service.confirm(agreement, UUID.randomUUID());
        assertThat(active.status()).isEqualTo(InstallmentEnums.Status.ACTIVE);
        // D1: o nivel veio da politica da P3.34 (sign.min_level.installment_term),
        // consultada pelo servico — nao do chamador — e o documento assinado
        // foi o TERMO GERADO para este acordo, nao o codigo do modelo.
        assertThat(minimumLevels.lastDocumentTypeCode).isEqualTo("installment_term");
        assertThat(signatures.lastRequiredLevelName).isEqualTo("ADVANCED");
        assertThat(signatures.lastDocumentRef).isEqualTo(agreement.termDocumentRef());
        assertThat(signatures.lastDocumentRef).isEqualTo("doc-" + InstallmentService.TERM_TEMPLATE);
        assertThat(signatures.lastDocumentRef).isNotEqualTo(agreement.termTemplateCode());
        assertThat(active.signatureLevelName()).isEqualTo("ADVANCED");
        assertThat(active.signatureReference()).startsWith("sig-");

        var undone = service.undo(active);
        assertThat(undone.status()).isEqualTo(InstallmentEnums.Status.UNDONE);
        assertThat(billsPort.restored).hasSize(2);
        assertThat(billsPort.restored.get(0).originalAmount()).isEqualByComparingTo("600");
        assertThat(billsPort.restored.get(1).originalAmount()).isEqualByComparingTo("400");
        assertThatThrownBy(() -> service.undo(undone)).isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("reparcelamento: novo acordo referencia o anterior, que vira REPLACED")
    void reparcel() {
        var first = service.confirm(service.create(baseRequest()), UUID.randomUUID());
        var b = baseRequest();
        var replacementRequest = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(), b.discounts(),
                b.downPayment(), b.downPaymentDueDate(), b.installmentCount(), b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(),
                null, first.id(), null);
        var replacement = service.create(replacementRequest);
        assertThat(service.markReplaced(first, replacement).status())
                .isEqualTo(InstallmentEnums.Status.REPLACED);
        assertThatThrownBy(() -> service.markReplaced(first, service.create(baseRequest())))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("plano homologado: importa com condicoes proprias (36x alem do maximo do perfil); sem plano, recusa")
    void homologatedPlanImport() {
        var b = baseRequest();
        var plan36 = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(), b.discounts(),
                new BigDecimal("10"), CREATION.plusDays(30), 36, LocalDate.of(2027, 1, 7),
                InstallmentEnums.InterestMethod.NONE, BigDecimal.ZERO,
                b.policy(), b.creationDate(), null, null, null);
        var withPlan = new Repactuation.Case(UNIT, CUSTOMER, "0001234-56.2026.8.24.0001",
                "Procon municipal", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 6, 1),
                LocalDate.of(2026, 11, 20), "plano-homologado-42");
        var imported = service.createFromHomologatedPlan(plan36, withPlan);
        assertThat(imported.source()).isEqualTo(InstallmentEnums.Source.REPACTUATION_PLAN);
        assertThat(imported.sourceReference()).isEqualTo("plano-homologado-42");
        assertThat(imported.schedule()).hasSize(36);

        var noPlan = new Repactuation.Case(UNIT, CUSTOMER, "0009999-00.2026.8.24.0001",
                "Procon municipal", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 6, 1), null, null);
        assertThatThrownBy(() -> service.createFromHomologatedPlan(plan36, noPlan))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("perfil a partir dos parametros inst.*: ausencia de chave e erro explicito")
    void policyFromParameters() {
        var params = DunningParameters.fromValues(Map.of(
                "inst.min_down_payment_pct", "20",
                "inst.min_down_payment", "50.00",
                "inst.entry_due_days", "7",
                "inst.max_installments", "24",
                "inst.min_installment", "30.00",
                "inst.max_discount_pct", "30",
                "inst.principal_discount_limit_pct", "0"));
        var built = InstallmentPolicy.fromParameters(UUID.randomUUID(), params);
        assertThat(built.minimumDownPayment(new BigDecimal("1100"))).isEqualByComparingTo("220.00");
        var incomplete = DunningParameters.fromValues(Map.of("inst.max_installments", "24"));
        assertThatThrownBy(() -> InstallmentPolicy.fromParameters(UUID.randomUUID(), incomplete))
                .isInstanceOf(br.com.ziipsan.core.parameters.MissingParameterException.class);
    }

    @Test
    @DisplayName("D5: permissao so dispensa o minimo NOMEADO nela (valor nao dispensa vencimento)")
    void waiverCoversOnlyNamedMinimums() {
        var amountOnly = new InstallmentAgreement.MinimumWaiver(UUID.randomUUID(),
                java.time.Instant.parse("2026-11-06T10:00:00Z"), "despacho-124/2026",
                java.util.Set.of(InstallmentAgreement.WaivedMinimum.DOWN_PAYMENT_AMOUNT));
        var b = baseRequest();
        var earlyDue = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(), b.discounts(),
                new BigDecimal("100"), CREATION.plusDays(3), b.installmentCount(), b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(),
                amountOnly, null, null);
        // valor dispensado, mas vencimento (3 dias < minimo de 7) NAO nomeado: recusa
        assertThatThrownBy(() -> service.create(earlyDue)).isInstanceOf(ApiException.Validation.class);
        var okDue = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(), b.discounts(),
                new BigDecimal("100"), CREATION.plusDays(7), b.installmentCount(), b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(),
                amountOnly, null, null);
        assertThat(service.create(okDue).downPaymentWaiver()).isEqualTo(amountOnly);
    }

    @Test
    @DisplayName("D1: sem nivel minimo definido na P3.34, a confirmacao recusa e NADA e assinado")
    void confirmRefusesWhenMinimumUndefined() {
        var noMinimum = new InstallmentService(templates, signatures, billsPort,
                new FakeMinimumLevels(null));
        var agreement = noMinimum.create(baseRequest());
        assertThatThrownBy(() -> noMinimum.confirm(agreement, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(signatures.calls).isZero();
    }

    @Test
    @DisplayName("D1: assinatura aplicada ABAIXO do minimo da politica nao ativa o acordo (fake nao ecoa o nivel)")
    void confirmRefusesWhenAppliedLevelBelowMinimum() {
        var strict = new InstallmentService(templates, new FakeSignatures("SIMPLE"), billsPort,
                new FakeMinimumLevels("QUALIFIED"));
        var agreement = strict.create(baseRequest());
        assertThatThrownBy(() -> strict.confirm(agreement, UUID.randomUUID()))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("D8: desfazer recusa PENDING_CONFIRMATION e CANCELED, sem restaurar conta alguma")
    void undoRefusesPendingAndCanceled() {
        var pending = service.create(baseRequest());
        assertThatThrownBy(() -> service.undo(pending)).isInstanceOf(ApiException.Validation.class);
        var canceled = pending.withStatus(InstallmentEnums.Status.CANCELED);
        assertThatThrownBy(() -> service.undo(canceled)).isInstanceOf(ApiException.Validation.class);
        assertThat(billsPort.restored).isEmpty();
    }

    @Test
    @DisplayName("D6: adesao a campanha GERA o acordo (origem campanha, abatimentos da campanha, adesao apontando para ele)")
    void campaignAdherenceCreatesAgreement() {
        var campaign = br.com.ziipsan.commercial.collection.dunning.campaign.RegularizationCampaign
                .createWithinPrincipalLimit(UUID.randomUUID(), "Regulariza 2026",
                        LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31),
                        "Decreto do ente 123/2026", UUID.randomUUID(), UUID.randomUUID(),
                        30, 180, java.util.Set.of("RESIDENTIAL"),
                        new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        12, "CAMPAIGN_ADHESION_TERM", true, BigDecimal.ZERO);
        var debt = new br.com.ziipsan.commercial.collection.dunning.campaign.RegularizationCampaign.DebtComponents(
                new BigDecimal("1000"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
        var outcome = service.adhereToCampaign(baseRequest(), campaign, debt, BigDecimal.ZERO, CREATION);
        assertThat(outcome.agreement().source())
                .isEqualTo(InstallmentEnums.Source.REGULARIZATION_CAMPAIGN);
        assertThat(outcome.agreement().sourceReference()).isEqualTo(campaign.id().toString());
        assertThat(outcome.agreement().discounts().fine()).isEqualByComparingTo("100.00");
        assertThat(outcome.agreement().financedAmount()).isEqualByComparingTo("780.00");
        assertThat(outcome.agreement().termTemplateCode()).isEqualTo("CAMPAIGN_ADHESION_TERM");
        assertThat(outcome.adherence().agreementId()).isEqualTo(outcome.agreement().id());
        assertThat(outcome.adherence().recoveredAmount()).isEqualByComparingTo("1000.00");
        // fora da vigencia e acima do teto de parcelas da campanha: recusado
        assertThatThrownBy(() -> service.adhereToCampaign(baseRequest(), campaign, debt,
                BigDecimal.ZERO, LocalDate.of(2027, 3, 1))).isInstanceOf(ApiException.Validation.class);
        var b = baseRequest();
        var tooMany = new InstallmentService.CreationRequest(b.tenantId(), b.unitId(), b.customerId(),
                b.protocolId(), b.serviceOrderId(), b.includedBills(), b.totalCharges(), b.discounts(),
                b.downPayment(), b.downPaymentDueDate(), 13, b.firstInstallmentDueDate(),
                b.interestMethod(), b.interestRate(), b.policy(), b.creationDate(), null, null, null);
        assertThatThrownBy(() -> service.adhereToCampaign(tooMany, campaign, debt,
                BigDecimal.ZERO, CREATION)).isInstanceOf(ApiException.Validation.class);
    }
}
