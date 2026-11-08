package br.com.ziipsan.commercial.collection.dunning.installment;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.commercial.collection.dunning.campaign.RegularizationCampaign;
import br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation;
import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo de vida do parcelamento (P4.1). Orquestracao sobre portas: o
 * demonstrativo e o termo sao gerados pelo motor de modelos da P2.30
 * (porta {@link DunningPorts.TemplatePort}) e as REFERENCIAS dos
 * documentos gerados ficam gravadas no acordo; a confirmacao CONSULTA o
 * nivel minimo do tipo {@code INSTALLMENT_TERM} na politica da P3.34
 * (porta {@link DunningPorts.MinimumSignatureLevelPort} — correcao D1:
 * o nivel nunca vem do chamador), assina o TERMO GERADO daquele acordo
 * (nunca o codigo do modelo) e confere o nivel efetivo contra o minimo
 * antes de ativar. A restauracao das contas ao desfazer e escrita pelo
 * billing (porta {@link DunningPorts.BillPort}). A persistencia do
 * acordo e da integracao (precedente das P3.34/P4.5).
 *
 * <p>Regras do aceite aplicadas na criacao padrao: entrada e vencimento
 * da entrada nao inferiores ao minimo do perfil SALVO permissao
 * REGISTRADA (quem, quando, referencia e quais minimos — D5) gravada no
 * acordo; numero de parcelas dentro do maximo; cada prestacao acima da
 * prestacao minima; abatimentos limitados ao teto total e, no
 * principal, ao limite proprio do perfil.
 */
public final class InstallmentService {

    /** Codigos de modelo desta tarefa no catalogo da P2.30 (registrar na integracao). */
    public static final String STATEMENT_TEMPLATE = "INSTALLMENT_STATEMENT";
    public static final String TERM_TEMPLATE = "INSTALLMENT_TERM";

    /**
     * Codigo do tipo de documento signatario do termo na P3.34
     * ({@code SignableDocumentType.INSTALLMENT_TERM.code()}): e o sufixo
     * de {@code sign.min_level.installment_term}, a chave consultada pela
     * politica de nivel minimo na confirmacao (D1).
     */
    public static final String TERM_DOCUMENT_TYPE = "installment_term";

    private final DunningPorts.TemplatePort templates;
    private final DunningPorts.SignaturePort signatures;
    private final DunningPorts.BillPort bills;
    private final DunningPorts.MinimumSignatureLevelPort minimumLevels;

    public InstallmentService(DunningPorts.TemplatePort templates,
                              DunningPorts.SignaturePort signatures,
                              DunningPorts.BillPort bills,
                              DunningPorts.MinimumSignatureLevelPort minimumLevels) {
        this.templates = Objects.requireNonNull(templates, "porta de modelos e obrigatoria");
        this.signatures = Objects.requireNonNull(signatures, "porta de assinatura e obrigatoria");
        this.bills = Objects.requireNonNull(bills, "porta de contas e obrigatoria");
        this.minimumLevels = Objects.requireNonNull(minimumLevels,
                "porta da politica de nivel minimo da P3.34 e obrigatoria");
    }

    /** Pedido de criacao de um acordo. */
    public record CreationRequest(
            UUID tenantId, UUID unitId, UUID customerId,
            UUID protocolId, UUID serviceOrderId,
            List<InstallmentAgreement.IncludedBill> includedBills,
            BigDecimal totalCharges,
            InstallmentAgreement.Discounts discounts,
            BigDecimal downPayment, LocalDate downPaymentDueDate,
            int installmentCount, LocalDate firstInstallmentDueDate,
            InstallmentEnums.InterestMethod interestMethod, BigDecimal interestRate,
            InstallmentPolicy policy, LocalDate creationDate,
            InstallmentAgreement.MinimumWaiver downPaymentWaiver,
            UUID replacesAgreementId, String legacyReference) {
        public CreationRequest {
            includedBills = includedBills == null ? List.of() : List.copyOf(includedBills);
            discounts = discounts == null ? InstallmentAgreement.Discounts.none() : discounts;
            totalCharges = totalCharges == null ? BigDecimal.ZERO : totalCharges;
        }
    }

    /** Criacao padrao: nasce PENDING_CONFIRMATION, com demonstrativo e termo gerados. */
    public InstallmentAgreement create(CreationRequest request) {
        return build(request, InstallmentEnums.Source.STANDARD, null, true, TERM_TEMPLATE);
    }

    /**
     * Importacao do plano HOMOLOGADO de repactuacao como parcelamento com
     * condicoes proprias (CAD-75): os limites do perfil padrao NAO se
     * aplicam — as condicoes sao as do plano —, mas o acordo continua
     * nascendo de protocolo e OS, com demonstrativo, termo e assinatura.
     */
    public InstallmentAgreement createFromHomologatedPlan(CreationRequest request, Repactuation.Case repactuation) {
        if (repactuation == null || !repactuation.hasHomologatedPlan()) {
            throw new ApiException.Validation(
                    "Importacao exige situacao em repactuacao com plano homologado registrado.",
                    List.of(), List.of());
        }
        if (!repactuation.unitId().equals(request.unitId()) || !repactuation.customerId().equals(request.customerId())) {
            throw new ApiException.Validation(
                    "O plano homologado e de outra unidade/titular: importacao recusada.",
                    List.of(), List.of());
        }
        return build(request, InstallmentEnums.Source.REPACTUATION_PLAN,
                repactuation.homologatedPlanRef(), false, TERM_TEMPLATE);
    }

    private InstallmentAgreement build(CreationRequest r, InstallmentEnums.Source source,
                                       String sourceRef, boolean enforcePolicy, String termTemplateCode) {
        if (r.protocolId() == null || r.serviceOrderId() == null) {
            throw new ApiException.Validation(
                    "Parcelamento nasce de protocolo e OS (ADR-0039): protocol_id e service_order_id obrigatorios.",
                    List.of(), List.of());
        }
        if (r.includedBills().isEmpty()) {
            throw new ApiException.Validation("Parcelamento exige ao menos uma conta incluida.",
                    List.of(), List.of());
        }
        BigDecimal billsTotal = r.includedBills().stream()
                .map(InstallmentAgreement.IncludedBill::originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebt = billsTotal.add(r.totalCharges());
        if (r.discounts().total().compareTo(totalDebt) > 0) {
            throw new ApiException.Validation("Abatimentos maiores que o debito total: recusado.",
                    List.of(), List.of());
        }
        if (enforcePolicy) {
            validateAgainstPolicy(r, totalDebt);
        }
        BigDecimal financed = totalDebt.subtract(r.discounts().total()).subtract(r.downPayment());
        if (financed.signum() <= 0) {
            throw new ApiException.Validation(
                    "Entrada e abatimentos consomem o debito: nao ha valor a parcelar.", List.of(), List.of());
        }
        var schedule = InstallmentCalculator.schedule(financed, r.interestMethod(), r.interestRate(),
                r.installmentCount(), r.firstInstallmentDueDate());
        if (enforcePolicy) {
            BigDecimal smallest = schedule.installments().stream()
                    .map(InstallmentCalculator.Installment::amount)
                    .min(BigDecimal::compareTo).orElseThrow();
            if (smallest.compareTo(r.policy().minInstallment()) < 0) {
                throw new ApiException.Validation(
                        "Prestacao de " + smallest + " abaixo da prestacao minima do perfil ("
                                + r.policy().minInstallment() + "): recusado.", List.of(), List.of());
            }
        }
        UUID id = UUID.randomUUID();
        var statement = templates.render(r.tenantId(), STATEMENT_TEMPLATE,
                Map.of("agreementId", id.toString(), "totalDebt", totalDebt.toPlainString()));
        var term = templates.render(r.tenantId(), termTemplateCode,
                Map.of("agreementId", id.toString(), "installments", String.valueOf(r.installmentCount())));
        return new InstallmentAgreement(id, r.tenantId(), r.unitId(), r.customerId(),
                r.policy().policyVersionId(), r.protocolId(), r.serviceOrderId(), r.includedBills(),
                totalDebt, r.discounts(), r.downPayment(), r.downPaymentDueDate(), schedule.installments(),
                r.interestMethod(), r.interestRate(), InstallmentEnums.Status.PENDING_CONFIRMATION,
                r.replacesAgreementId(), r.legacyReference(), source, sourceRef,
                statement.templateCode(), statement.templateVersion(), statement.documentRef(),
                term.templateCode(), term.templateVersion(), term.documentRef(),
                r.downPaymentWaiver(), null, null);
    }

    private void validateAgainstPolicy(CreationRequest r, BigDecimal totalDebt) {
        InstallmentPolicy policy = r.policy();
        if (policy == null) {
            throw new IllegalArgumentException("perfil de parcelamento e obrigatorio na criacao padrao");
        }
        BigDecimal minimumDown = policy.minimumDownPayment(totalDebt);
        if (r.downPayment().compareTo(minimumDown) < 0
                && !waives(r.downPaymentWaiver(), InstallmentAgreement.WaivedMinimum.DOWN_PAYMENT_AMOUNT)) {
            throw new ApiException.Validation(
                    "Entrada " + r.downPayment() + " abaixo do minimo do perfil (" + minimumDown
                            + ") sem permissao registrada para o VALOR da entrada: recusado.",
                    List.of(), List.of());
        }
        LocalDate earliestEntryDue = r.creationDate().plusDays(policy.entryDueDays());
        if (r.downPaymentDueDate().isBefore(earliestEntryDue)
                && !waives(r.downPaymentWaiver(), InstallmentAgreement.WaivedMinimum.DOWN_PAYMENT_DUE_DATE)) {
            throw new ApiException.Validation(
                    "Vencimento da entrada " + r.downPaymentDueDate() + " antes do minimo do perfil ("
                            + earliestEntryDue + ") sem permissao registrada para o VENCIMENTO "
                            + "da entrada: recusado.", List.of(), List.of());
        }
        if (r.installmentCount() > policy.maxInstallments()) {
            throw new ApiException.Validation(
                    "Numero de parcelas " + r.installmentCount() + " acima do maximo do perfil ("
                            + policy.maxInstallments() + "): recusado.", List.of(), List.of());
        }
        BigDecimal discountPct = r.discounts().total().multiply(new BigDecimal("100"))
                .divide(totalDebt, 2, RoundingMode.HALF_UP);
        if (discountPct.compareTo(policy.maxDiscountPct()) > 0) {
            throw new ApiException.Validation(
                    "Abatimento total de " + discountPct + "% acima do teto do perfil ("
                            + policy.maxDiscountPct() + "%): recusado.", List.of(), List.of());
        }
        BigDecimal billsTotal = r.includedBills().stream()
                .map(InstallmentAgreement.IncludedBill::originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (billsTotal.signum() > 0) {
            BigDecimal principalPct = r.discounts().principal().multiply(new BigDecimal("100"))
                    .divide(billsTotal, 2, RoundingMode.HALF_UP);
            if (principalPct.compareTo(policy.principalDiscountLimitPct()) > 0) {
                throw new ApiException.Validation(
                        "Abatimento do principal de " + principalPct + "% acima do limite do perfil ("
                                + policy.principalDiscountLimitPct() + "%): recusado.", List.of(), List.of());
            }
        }
    }

    private static boolean waives(InstallmentAgreement.MinimumWaiver waiver,
                                  InstallmentAgreement.WaivedMinimum minimum) {
        return waiver != null && waiver.waives(minimum);
    }

    /** Forca dos niveis da Lei 14.063/2020 (a mesma ordem da P3.34: SIMPLE < ADVANCED < QUALIFIED). */
    private static int signatureLevelRank(String levelName) {
        if (levelName == null) {
            throw new ApiException.Validation("Nivel de assinatura nao informado.", List.of(), List.of());
        }
        return switch (levelName.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "SIMPLE" -> 1;
            case "ADVANCED" -> 2;
            case "QUALIFIED" -> 3;
            default -> throw new ApiException.Validation(
                    "Nivel de assinatura fora de SIMPLE, ADVANCED, QUALIFIED: recusado.", List.of(), List.of());
        };
    }

    /**
     * Confirmacao (correcao D1): o nivel exigido e CONSULTADO na politica
     * da P3.34 para o tipo {@code INSTALLMENT_TERM}
     * ({@code sign.min_level.installment_term} do tenant) — nunca recebido
     * do chamador; sem nivel definido, a porta recusa e nada e assinado.
     * Assina-se o TERMO GERADO pelo motor de modelos para ESTE acordo
     * ({@code termDocumentRef} gravado na criacao), e o nivel EFETIVO
     * devolvido pela assinatura e conferido contra o minimo antes de
     * ativar: abaixo do minimo, o acordo NAO ativa.
     */
    public InstallmentAgreement confirm(InstallmentAgreement agreement, UUID signerUserId) {
        if (agreement.status() != InstallmentEnums.Status.PENDING_CONFIRMATION) {
            throw new ApiException.Validation("So acordo pendente de confirmacao pode ser confirmado (estado: "
                    + agreement.status() + ").", List.of(), List.of());
        }
        String requiredLevelName = minimumLevels.requiredLevelName(agreement.tenantId(), TERM_DOCUMENT_TYPE);
        var signature = signatures.signInstallmentTerm(agreement.tenantId(), agreement.id(),
                agreement.termDocumentRef(), requiredLevelName, signerUserId);
        if (signatureLevelRank(signature.levelName()) < signatureLevelRank(requiredLevelName)) {
            throw new ApiException.Validation("Assinatura aplicada em nivel " + signature.levelName()
                    + " abaixo do minimo " + requiredLevelName + " do tipo " + TERM_DOCUMENT_TYPE
                    + " (sign.min_level." + TERM_DOCUMENT_TYPE + ", P3.34): acordo nao ativado.",
                    List.of(), List.of());
        }
        return agreement.confirmedWith(signature.levelName(), signature.signatureRef());
    }

    /**
     * Desfazer o parcelamento: o acordo vira UNDONE e as contas incluidas
     * sao RESTAURADAS pelo billing, cada uma pelo valor original gravado
     * em {@code included_bills} (teste do aceite). So se desfaz acordo
     * CONFIRMADO/ATIVO (correcao D8): um acordo PENDING_CONFIRMATION
     * nunca foi assinado nem baixou conta alguma, e um CANCELED ja teve
     * o seu proprio desfazimento — desfazer qualquer um dos dois
     * restauraria contas indevidamente (no cancelado, pela segunda vez).
     */
    public InstallmentAgreement undo(InstallmentAgreement agreement) {
        if (agreement.status() != InstallmentEnums.Status.ACTIVE) {
            throw new ApiException.Validation("So acordo ACTIVE (confirmado) pode ser desfeito (estado: "
                    + agreement.status() + ").", List.of(), List.of());
        }
        bills.restoreBills(agreement.tenantId(), agreement.id(), agreement.includedBills().stream()
                .map(b -> new DunningPorts.BillPort.BillRestore(b.billId(), b.originalAmount()))
                .toList());
        return agreement.withStatus(InstallmentEnums.Status.UNDONE);
    }

    /** Resultado da adesao a campanha: o acordo criado e a adesao que o referencia (D6). */
    public record CampaignAdherenceOutcome(InstallmentAgreement agreement,
                                           RegularizationCampaign.Adherence adherence) {
    }

    /**
     * Adesao a campanha de regularizacao (CAD-73; correcao D6): a adesao
     * GERA o acordo de parcelamento correspondente, com os abatimentos
     * calculados pela campanha (o teto do principal e revalidado aqui e
     * ja o foi na criacao da campanha), a origem
     * {@code REGULARIZATION_CAMPAIGN} e o termo de adesao como modelo do
     * termo do acordo — e devolve a {@code Adherence} apontando para o
     * acordo criado. As condicoes sao as da campanha (vigencia, teto de
     * parcelas dela), nao as do perfil padrao; o acordo continua nascendo
     * de protocolo e OS, com demonstrativo e termo gerados, e so ativa
     * pela confirmacao assinada de {@link #confirm}.
     */
    public CampaignAdherenceOutcome adhereToCampaign(CreationRequest request, RegularizationCampaign campaign,
                                                     RegularizationCampaign.DebtComponents debt,
                                                     BigDecimal principalDiscountLimitPct, LocalDate adheredAt) {
        if (campaign == null || debt == null || adheredAt == null) {
            throw new IllegalArgumentException("adesao exige campanha, componentes do debito e data");
        }
        campaign.requireWithinPrincipalLimit(principalDiscountLimitPct);
        if (!campaign.activeAt(adheredAt)) {
            throw new ApiException.Validation("Adesao fora da vigencia da campanha (" + campaign.validFrom()
                    + " a " + campaign.validTo() + "): recusada.", List.of(), List.of());
        }
        if (request.installmentCount() > campaign.maxInstallments()) {
            throw new ApiException.Validation("Adesao com " + request.installmentCount()
                    + " parcelas acima do maximo da campanha (" + campaign.maxInstallments()
                    + "): recusada.", List.of(), List.of());
        }
        BigDecimal billsTotal = request.includedBills().stream()
                .map(InstallmentAgreement.IncludedBill::originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal charges = debt.fine().add(debt.interest()).add(debt.correction());
        if (billsTotal.compareTo(debt.principal()) != 0 || request.totalCharges().compareTo(charges) != 0) {
            throw new ApiException.Validation(
                    "Componentes da campanha nao conferem com as contas incluidas/encargos do pedido: recusado.",
                    List.of(), List.of());
        }
        var result = campaign.discountsFor(debt, principalDiscountLimitPct);
        var discounts = new InstallmentAgreement.Discounts(result.fineDiscount(), result.interestDiscount(),
                result.correctionDiscount(), result.principalDiscount());
        var effective = new CreationRequest(request.tenantId(), request.unitId(), request.customerId(),
                request.protocolId(), request.serviceOrderId(), request.includedBills(), request.totalCharges(),
                discounts, request.downPayment(), request.downPaymentDueDate(), request.installmentCount(),
                request.firstInstallmentDueDate(), request.interestMethod(), request.interestRate(),
                request.policy(), request.creationDate(), request.downPaymentWaiver(),
                request.replacesAgreementId(), request.legacyReference());
        InstallmentAgreement agreement = build(effective, InstallmentEnums.Source.REGULARIZATION_CAMPAIGN,
                campaign.id().toString(), false, campaign.adhesionTermTemplateCode());
        var adherence = new RegularizationCampaign.Adherence(request.unitId(), request.customerId(),
                adheredAt, agreement.id(), result.discountedTotal(), false);
        return new CampaignAdherenceOutcome(agreement, adherence);
    }

    /** Reparcelamento: o acordo anterior e marcado REPLACED pelo novo que o referencia. */
    public InstallmentAgreement markReplaced(InstallmentAgreement previous, InstallmentAgreement replacement) {
        if (replacement.replacesAgreementId() == null
                || !replacement.replacesAgreementId().equals(previous.id())) {
            throw new ApiException.Validation(
                    "O novo acordo nao referencia o anterior em replaces_agreement_id.", List.of(), List.of());
        }
        if (previous.status() != InstallmentEnums.Status.ACTIVE) {
            throw new ApiException.Validation("So acordo ACTIVE pode ser reparcelado (estado: "
                    + previous.status() + ").", List.of(), List.of());
        }
        return previous.withStatus(InstallmentEnums.Status.REPLACED);
    }
}
