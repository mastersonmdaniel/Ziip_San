package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Controle do PROTESTO de CDA (ED-31; Lei 9.492/1997, art. 1o, paragrafo
 * unico; decisao PD-10). Tres portoes, TODOS obrigatorios:
 * <ol>
 *   <li>{@code da.protest_enabled} ligado — nasce DESLIGADO e, sem o
 *       parecer da PD-10, deve PERMANECER desligado;</li>
 *   <li>parecer juridico favoravel REGISTRADO sobre o protesto de CDA de
 *       creditos de agua e esgoto (PD-10), via LegalOpinionPort com
 *       proveniencia — MARCADOR DECLARADO: esse parecer NAO existe nas
 *       fontes desta entrega, logo, no estado real do projeto, este portao
 *       esta FECHADO e todo protesto e bloqueado;</li>
 *   <li>prescricao da P4.1 nao consumada nem indeterminada, avaliada
 *       ITEM A ITEM (correcao D1 da revisao) e com a analise juridica da
 *       linha de prescricao como ENTRADA EXPLICITA do chamador
 *       (correcao D2 da revisao): {@code legalAnalysisRecorded} e o
 *       registro real da analise de prescricao (ex.: linha do Decreto
 *       20.910/1932) — assunto DISTINTO do parecer da PD-10, que trata
 *       da licitude do protesto. Nenhum atalho presume a analise: sem
 *       ela, linha que a exige devolve UNDETERMINED e bloqueia, com o
 *       protesto tao forte quanto o ajuizamento.</li>
 * </ol>
 * Qualquer portao fechado = protesto recusado com o motivo nomeado.
 */
public final class ProtestControl {

    /** Assunto do parecer exigido pela PD-10. */
    public static final String PROTEST_OPINION_SUBJECT = "DA_CDA_PROTEST_PD10";

    public record ProtestAuthorization(UUID enrollmentId, String opinionReference, LocalDate authorizedOn) {}

    private final PrescriptionGate gate;
    private final ActiveDebtParameters params;
    private final DunningPorts.LegalOpinionPort legalOpinionPort;
    private final Prescription.ProviderNature nature;
    private final Prescription.ClaimType claim;

    public ProtestControl(PrescriptionGate gate, ActiveDebtParameters params,
                          DunningPorts.LegalOpinionPort legalOpinionPort,
                          Prescription.ProviderNature nature, Prescription.ClaimType claim) {
        this.gate = Objects.requireNonNull(gate); this.params = Objects.requireNonNull(params);
        this.legalOpinionPort = Objects.requireNonNull(legalOpinionPort, "porta de parecer juridico e obrigatoria");
        this.nature = Objects.requireNonNull(nature); this.claim = Objects.requireNonNull(claim);
    }

    /**
     * Autoriza o protesto. {@code legalAnalysisRecorded} e entrada
     * EXPLICITA (correcao D2): o registro real da analise juridica de
     * prescricao da linha aplicavel (P4.1), informado por quem chama —
     * NAO decorre do parecer da PD-10 nem e presumido por esta classe.
     */
    public ProtestAuthorization authorize(ActiveDebtEnrollment.Enrollment enrollment, boolean legalAnalysisRecorded) {
        Objects.requireNonNull(enrollment, "inscricao e obrigatoria");
        if (!params.protestEnabled())
            throw new IllegalStateException("protesto de CDA bloqueado: da.protest_enabled desligado (padrao; PD-10 — so liberar apos o parecer juridico)");
        var opinion = legalOpinionPort.favorableOpinion(enrollment.tenantId(), PROTEST_OPINION_SUBJECT)
                .orElseThrow(() -> new IllegalStateException("protesto de CDA bloqueado: parecer juridico favoravel (PD-10) nao registrado para o tenant"));
        gate.assertAllowedForAll(PrescriptionGate.Operation.PROTEST, nature, claim,
                enrollment.items(), legalAnalysisRecorded);
        return new ProtestAuthorization(enrollment.id(), opinion.reference(), gate.today());
    }
}
