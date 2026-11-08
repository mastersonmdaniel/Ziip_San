package br.com.ziipsan.commercial.billing.discharge;

import br.com.ziipsan.commercial.billing.content.LegalMessageEngine;
import br.com.ziipsan.commercial.billing.domain.AnnualDischargeStatementEntity;
import br.com.ziipsan.commercial.billing.parameters.StatementParameters;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Implementacao pura do {@link AnnualDischargeService} (sem banco: regra + parametros em memoria). */
@Service
public class DefaultAnnualDischargeService implements AnnualDischargeService {

    @Override
    public List<AnnualDischargeRule.DischargeDecision> dueStatements(DischargeRequest request) {
        requireRequest(request);
        return AnnualDischargeRule.evaluateAll(request.history(), request.targetReferenceMonth(),
                request.alreadyIssuedYears());
    }

    @Override
    public StatementParameters.StatementChannel channel(DischargeRequest request) {
        requireRequest(request);
        return StatementParameters.fromValues(request.parameterValues()).statementChannel();
    }

    @Override
    public LegalMessageEngine.EmissionContext contextWithDischarge(LegalMessageEngine.EmissionContext base,
                                                                   DischargeRequest request) {
        if (base == null) {
            throw new IllegalArgumentException("contexto de emissao base e obrigatorio");
        }
        requireRequest(request);
        boolean onBill = channel(request) == StatementParameters.StatementChannel.INVOICE_SPACE
                && !dueStatements(request).isEmpty();
        return new LegalMessageEngine.EmissionContext(
                base.issueDate(),
                base.socialLossEffectiveDate(),
                base.socialLossFirstNoticeDate(),
                base.socialLossNoticeMonths(),
                onBill,
                base.waterQualityAvailable(),
                base.benefitValidUntil(),
                base.benefitExpiryNoticeDays(),
                base.activatedSlotKeys());
    }

    @Override
    public AnnualDischargeStatementEntity newStatementRecord(UUID tenantId, UUID unitId,
                                                             AnnualDischargeRule.DischargeDecision decision,
                                                             StatementParameters.StatementChannel channel,
                                                             UUID billId, LocalDate issuedOn) {
        if (tenantId == null || unitId == null || decision == null || channel == null || issuedOn == null) {
            throw new IllegalArgumentException(
                    "tenant, unidade, decisao, canal e data de emissao sao obrigatorios no registro");
        }
        if (channel == StatementParameters.StatementChannel.INVOICE_SPACE && billId == null) {
            throw new IllegalArgumentException(
                    "declaracao no espaco na fatura exige a conta que a carregou (billId)");
        }
        AnnualDischargeStatementEntity entity = new AnnualDischargeStatementEntity();
        entity.setTenantId(tenantId);
        entity.setUnitId(unitId);
        entity.setReferenceYear(decision.referenceYear());
        entity.setChannel(channel.name());
        entity.setEmissionTrigger(decision.trigger().name());
        entity.setIssuedBillId(billId);
        entity.setIssuedOn(issuedOn);
        return entity;
    }

    private static void requireRequest(DischargeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("pedido de avaliacao e obrigatorio");
        }
    }
}
