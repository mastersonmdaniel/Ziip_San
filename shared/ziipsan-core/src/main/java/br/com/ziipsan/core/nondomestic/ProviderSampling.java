package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.effluent.EffluentPorts;
import br.com.ziipsan.core.effluent.EffluentStandard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Amostragem do prestador no ponto de lancamento do usuario: a
 * analise de uma coleta so e registrada sobre amostra cuja cadeia de
 * custodia da ED-50 prova o RECEBIMENTO pelo laboratorio — o mesmo
 * portao da ED-105 ({@link EffluentPorts.EffluentLabPort}, JOR-37) —
 * e o veredito e o do catalogo de padrao de lancamento do prestador
 * ({@link UserStandardCatalog} sobre a ED-105).
 *
 * <p>Sem recebimento pelo laboratorio, recusa nomeada: resultado sem
 * custodia nao fundamenta conformidade, notificacao nem penalidade.
 */
public final class ProviderSampling {

    /** Coleta do prestador identificada pela amostra da ED-50 que a materializa. */
    public record CollectedSample(UUID sampleId, String parameterKey) {
        public CollectedSample {
            if (sampleId == null) throw new IllegalArgumentException("sampleId (ED-50) e obrigatorio");
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey da coleta e obrigatorio");
        }
    }

    private final EffluentPorts.EffluentLabPort lab;

    public ProviderSampling(EffluentPorts.EffluentLabPort lab) {
        if (lab == null) throw new IllegalArgumentException(
                "porta do laboratorio/custodia da ED-50 (via ED-105) e obrigatoria");
        this.lab = lab;
    }

    public UserStandardCatalog.Assessment registerAnalysis(
            DischargerRegistration discharger, CollectedSample sample,
            BigDecimal numericValue, String qualitativeValue,
            UserStandardCatalog catalog, EffluentStandard.Jurisdiction jurisdiction,
            String stateCode, LocalDate collectionDate) {
        if (discharger == null || sample == null || catalog == null)
            throw new IllegalArgumentException("gerador, coleta e catalogo sao obrigatorios");
        if (collectionDate == null) throw new IllegalArgumentException("data da coleta e obrigatoria");
        if (!discharger.authorizedOn(collectionDate))
            throw new IllegalStateException("amostragem do prestador exige lancamento autorizado"
                    + " vigente na data da coleta (cadastro " + discharger.id()
                    + ", estado " + discharger.status() + ")");
        EffluentPorts.LabReceipt receipt = lab.receiptOf(discharger.tenantId(), sample.sampleId());
        if (receipt == null || !receipt.sampleId().equals(sample.sampleId()) || !receipt.receivedByLab())
            throw new IllegalStateException("amostra " + sample.sampleId()
                    + " sem recebimento registrado pelo laboratorio na cadeia de custodia"
                    + " da ED-50: analise do prestador nao se registra sem custodia");
        return catalog.evaluate(sample.parameterKey(), numericValue, qualitativeValue,
                jurisdiction, stateCode, collectionDate);
    }
}
