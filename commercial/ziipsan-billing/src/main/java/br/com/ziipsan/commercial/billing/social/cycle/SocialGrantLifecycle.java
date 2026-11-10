package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialBenefitConference;
import br.com.ziipsan.commercial.billing.social.SocialClassification;
import br.com.ziipsan.commercial.billing.social.SocialEligibility;
import br.com.ziipsan.commercial.billing.social.SocialEnrollmentLifecycle;
import br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * II-3: ciclo de vida da concessao da tarifa social - elegivel, concedido
 * SEM PEDIDO, em revisao, em graca e cessado - com trilha de auditoria.
 *
 * <p>Nenhuma regra da P1.5/ED-57 e reimplementada aqui:
 * <ul>
 *   <li>a concessao exige a conferencia integralmente aplicavel da ED-57
 *       ({@link SocialBenefitConference#check}: elegivel pela
 *       {@code SocialEligibility} da P1.5, dentro da vigencia declarada
 *       e sob a regra legal vigente desde 11/12/2026, data da
 *       {@code SocialProgramListPolicy});</li>
 *   <li>a origem da lista e travada pela {@code SocialProgramListPolicy}
 *       (PLT-36: cessao direta do CadUnico so para ente de direito
 *       publico, Decreto 11.016/2022);</li>
 *   <li>a cessacao respeita o aviso de perda e a graca da P1.5,
 *       delegados a {@link SocialEnrollmentLifecycle} com os prazos dos
 *       parametros do ciclo.</li>
 * </ul>
 *
 * <p>Correspondencia com os estados da inscricao na V4_2:
 * GRANTED = ACTIVE, UNDER_REVIEW = LOSS_NOTICE, GRACE = GRACE,
 * ENDED = ENDED. ELIGIBLE e o estado pre-concessao do ciclo (a familia
 * casou e e elegivel; ainda nao ha inscricao).
 *
 * <p>Trilha LGPD: cada entrada referencia SO o id da concessao, os
 * estados e o codigo do motivo - NUNCA o indice cego da familia. E a
 * mesma regra da V4_2 para a trilha de auditoria ("blind indexes must
 * never appear in the audit trail"). Nenhum metodo recebe situacao de
 * pagamento: a concessao nao depende de adimplencia (Lei 14.898/2024),
 * como na P1.5.
 */
public final class SocialGrantLifecycle {

    /** Estados do ciclo de vida da concessao (ver correspondencia com a V4_2 no Javadoc da classe). */
    public enum GrantStatus {
        ELIGIBLE, GRANTED, UNDER_REVIEW, GRACE, ENDED
    }

    /** Motivos de recusa da concessao sem pedido. */
    public enum RefusalReason {
        /** Inelegivel na referencia pela regra da P1.5 (ex.: CadUnico vencido). */
        INELIGIBLE,
        /** Fora da vigencia declarada do beneficio na lista. */
        OUTSIDE_VALIDITY,
        /** Regra legal ainda nao vigente na referencia (antes de 11/12/2026). */
        LEGAL_RULE_NOT_YET_APPLICABLE,
        /** Origem da lista nao permitida para a natureza juridica (PLT-36). */
        LIST_SOURCE_NOT_ALLOWED
    }

    /** Recusa explicita da concessao, com o motivo estavel para o relatorio do ciclo. */
    public static final class SocialGrantException extends RuntimeException {
        private final RefusalReason reason;

        public SocialGrantException(RefusalReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public RefusalReason reason() {
            return reason;
        }
    }

    /**
     * Uma entrada da trilha: sequencia dentro da concessao, instante,
     * transicao e motivo. Sem indice cego, sem dado pessoal (ver classe).
     */
    public record TrailEntry(long sequence, Instant occurredAt, GrantStatus fromStatus, GrantStatus toStatus,
                             String reasonCode, String actorId) {
    }

    /** A concessao: a inscricao do ciclo, com a sua trilha completa. */
    public record Grant(UUID grantId, UUID unitId, UUID holderCustomerId, String familyCodeHash,
                        SocialEligibility.Basis basis, LocalDate cadunicoUpdatedOn,
                        LocalDate validFrom, LocalDate validTo, GrantStatus status,
                        LocalDate lossNoticeStartedOn, LocalDate graceStartedOn, String endReasonCode,
                        String legalBasis, List<TrailEntry> trail) {
        public Grant {
            trail = List.copyOf(trail);
        }

        Grant transition(GrantStatus to, LocalDate lossNotice, LocalDate grace, String endReason,
                         Instant at, String reasonCode, String actorId) {
            List<TrailEntry> next = new ArrayList<>(trail);
            next.add(new TrailEntry(next.size() + 1, at, status, to, reasonCode, actorId));
            return new Grant(grantId, unitId, holderCustomerId, familyCodeHash, basis, cadunicoUpdatedOn,
                    validFrom, validTo, to, lossNotice, grace, endReason, legalBasis, next);
        }
    }

    private SocialGrantLifecycle() {
    }

    /**
     * Concede SEM PEDIDO a uma familia cruzada: ou nasce GRANTED com a
     * primeira entrada da trilha, ou recusa com {@link SocialGrantException}
     * e motivo - familia nao elegivel NUNCA e concedida por este caminho.
     *
     * @param crossed     familia casada pelo cruzamento
     * @param list        metadados da lista (origem e base legal registrada, ED-57)
     * @param legalNature natureza juridica do prestador (PLT-36)
     */
    public static Grant grantWithoutRequest(SocialCrossingEngine.CrossedFamily crossed,
                                            SocialBenefitConference.ListMetadata list,
                                            SocialProgramListPolicy.LegalNature legalNature,
                                            LocalDate referenceDate, SocialTariffCycleParameters parameters,
                                            Instant occurredAt, String actorId) {
        if (crossed == null || list == null || legalNature == null || referenceDate == null
                || parameters == null || occurredAt == null) {
            throw new IllegalArgumentException("Familia cruzada, lista, natureza juridica, data de"
                    + " referencia, parametros e instante sao obrigatorios");
        }
        if (!SocialProgramListPolicy.isAllowed(list.listSource(), legalNature)) {
            throw new SocialGrantException(RefusalReason.LIST_SOURCE_NOT_ALLOWED,
                    "Origem da lista nao permitida para a natureza juridica do prestador (PLT-36)");
        }
        SocialClassification.BaseRecord record = new SocialClassification.BaseRecord(crossed.familyCodeHash(),
                "", "HOLDER", crossed.basis(), crossed.cadunicoUpdatedOn());
        SocialBenefitConference.BenefitValidity validity =
                new SocialBenefitConference.BenefitValidity(crossed.benefitValidFrom(), crossed.benefitValidTo());
        SocialBenefitConference.Conference conference = SocialBenefitConference.check(
                record, validity, referenceDate, parameters.cadunicoMaxAgeMonths());
        if (!conference.eligible()) {
            throw new SocialGrantException(RefusalReason.INELIGIBLE,
                    "Beneficio inelegivel na data de referencia pela regra da P1.5: nao concede");
        }
        if (!conference.withinValidity()) {
            throw new SocialGrantException(RefusalReason.OUTSIDE_VALIDITY,
                    "Beneficio fora da vigencia declarada na lista na data de referencia: nao concede");
        }
        if (!conference.legalRuleApplicable()) {
            throw new SocialGrantException(RefusalReason.LEGAL_RULE_NOT_YET_APPLICABLE,
                    "Regra legal da tarifa social ainda nao aplicavel na data de referencia"
                            + " (vigora a partir de 11/12/2026, Lei 14.898/2024; NR 13, art. 26,"
                            + " paragrafo 1o): nao concede");
        }
        TrailEntry first = new TrailEntry(1, occurredAt, GrantStatus.ELIGIBLE, GrantStatus.GRANTED,
                "GRANT_WITHOUT_REQUEST", actorId);
        return new Grant(UUID.randomUUID(), crossed.unitId(), crossed.holderCustomerId(), crossed.familyCodeHash(),
                crossed.basis(), crossed.cadunicoUpdatedOn(), crossed.benefitValidFrom(), crossed.benefitValidTo(),
                GrantStatus.GRANTED, null, null, null, list.legalBasis(), List.of(first));
    }

    /** GRANTED para UNDER_REVIEW (aviso de perda na fatura, P1.5): comeca a contar o prazo do aviso. */
    public static Grant beginLossNotice(Grant grant, LocalDate noticeStartedOn, Instant occurredAt, String actorId) {
        requireStatus(grant, GrantStatus.GRANTED, "aviso de perda");
        if (noticeStartedOn == null) {
            throw new IllegalArgumentException("Data de inicio do aviso de perda e obrigatoria");
        }
        return grant.transition(GrantStatus.UNDER_REVIEW, noticeStartedOn, grant.graceStartedOn(), null,
                occurredAt, "LOSS_NOTICE_STARTED", actorId);
    }

    /** GRANTED ou UNDER_REVIEW para GRACE (deixou de ser elegivel; a graca da P1.5 corre daqui). */
    public static Grant beginGrace(Grant grant, LocalDate graceStartedOn, Instant occurredAt, String actorId) {
        if (grant == null || (grant.status() != GrantStatus.GRANTED && grant.status() != GrantStatus.UNDER_REVIEW)) {
            throw new IllegalStateException("Graca so comeca a partir de concessao activa ou em revisao;"
                    + " estado actual: " + (grant == null ? "null" : grant.status()));
        }
        if (graceStartedOn == null) {
            throw new IllegalArgumentException("Data de inicio da graca e obrigatoria");
        }
        return grant.transition(GrantStatus.GRACE, grant.lossNoticeStartedOn(), graceStartedOn, null,
                occurredAt, "GRACE_STARTED", actorId);
    }

    /**
     * Cessa a concessao. De UNDER_REVIEW, so depois de satisfeito o aviso
     * de perda da P1.5; de GRACE, so depois de satisfeita a graca da
     * P1.5; de GRANTED, recusa - cessar sem aviso nem graca nao esta em
     * nenhuma fonte do projecto (decisao de desenho declarada no
     * relatorio do pacote). Motivo de fim obrigatorio (catalogo de
     * motivos do tenant, V4_2 - o catalogo em si e politica do tenant e
     * nao e validado aqui).
     */
    public static Grant end(Grant grant, LocalDate endDate, String endReasonCode,
                            SocialTariffCycleParameters parameters, Instant occurredAt, String actorId) {
        if (grant == null || endDate == null || parameters == null) {
            throw new IllegalArgumentException("Concessao, data de fim e parametros sao obrigatorios");
        }
        if (endReasonCode == null || endReasonCode.isBlank()) {
            throw new IllegalArgumentException("Motivo de fim da concessao e obrigatorio (catalogo da V4_2)");
        }
        switch (grant.status()) {
            case UNDER_REVIEW -> {
                if (!SocialEnrollmentLifecycle.lossNoticePeriodSatisfied(
                        grant.lossNoticeStartedOn(), endDate, parameters.lossNoticeMonths())) {
                    throw new IllegalStateException("Fim antes de decorrido o aviso de perda minimo da P1.5 ("
                            + parameters.lossNoticeMonths() + " meses do aviso): recusado");
                }
            }
            case GRACE -> {
                if (!SocialEnrollmentLifecycle.gracePeriodSatisfied(
                        grant.graceStartedOn(), endDate, parameters.graceMonths())) {
                    throw new IllegalStateException("Fim antes de decorrida a graca minima da P1.5 ("
                            + parameters.graceMonths() + " meses): recusado");
                }
            }
            default -> throw new IllegalStateException("Concessao em " + grant.status()
                    + " nao pode cessar directamente: exige aviso de perda ou graca (P1.5)");
        }
        return grant.transition(GrantStatus.ENDED, grant.lossNoticeStartedOn(), grant.graceStartedOn(),
                endReasonCode, occurredAt, "ENDED", actorId);
    }

    /**
     * Proxima recertificacao devida: inicio da vigencia + intervalo do
     * parametro. Sem intervalo definido ([A DEFINIR], ver
     * {@link SocialTariffCycleParameters}), devolve vazio - nenhum
     * intervalo e inventado.
     */
    public static Optional<LocalDate> nextRecertificationDue(Grant grant, SocialTariffCycleParameters parameters) {
        if (grant == null || parameters == null) {
            throw new IllegalArgumentException("Concessao e parametros sao obrigatorios");
        }
        if (parameters.recertificationIntervalMonths().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(grant.validFrom().plusMonths(parameters.recertificationIntervalMonths().getAsInt()));
    }

    private static void requireStatus(Grant grant, GrantStatus expected, String operation) {
        if (grant == null) {
            throw new IllegalArgumentException("Concessao e obrigatoria");
        }
        if (grant.status() != expected) {
            throw new IllegalStateException("Operacao de " + operation + " exige concessao em " + expected
                    + "; estado actual: " + grant.status());
        }
    }
}
