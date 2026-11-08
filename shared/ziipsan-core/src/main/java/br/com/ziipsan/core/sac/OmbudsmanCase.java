package br.com.ziipsan.core.sac;

import br.com.ziipsan.core.protocol.DeadlineCalculator;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;
import br.com.ziipsan.core.protocol.ProtocolChannel;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ouvidoria como fila de SEGUNDA INSTANCIA sobre o protocolo da P1.36 (ED-35;
 * NR 11, art. 52, \u00a72\u00ba; Lei 13.460/2017, art. 16): prazo de
 * {@code sac.ombudsman_days} com UMA prorrogacao por igual periodo, sempre
 * justificada. O prazo bruto do tenant passa SEMPRE por
 * {@link SacParameters#ombudsmanDays(Integer)} na construcao \u2014 caso com
 * prazo acima do teto legal (30 dias) ou sem parametro e RECUSADO aqui, nao
 * depende de o chamador ter validado antes (correcao pos-reprovacao, G1).
 * O vinculo com a P1.36 usa os tipos reais do nucleo: {@link UUID} do
 * protocolo (convencao do repo: V8_1 {@code service_protocols.id UUID} e
 * {@code ProtocolService} operando em UUID) e o canal
 * {@link ProtocolChannel} (correcao M4/M6). A identidade do manifestante e
 * preservada conforme o modo: PRESERVED/ANONYMOUS nunca expoem o nome na
 * visao da fila; em modo ANONYMOUS o nome nem e RETIDO em memoria
 * (correcao M6): o campo fica nulo desde a construcao.
 */
public final class OmbudsmanCase {

    public enum IdentityMode { IDENTIFIED, PRESERVED, ANONYMOUS }

    private final UUID protocolId;
    private final ProtocolChannel channel;
    private final LocalDate openedDate;
    private final int ombudsmanDays;
    private final LocalDate originalDueDate;
    private LocalDate currentDueDate;
    private final IdentityMode identityMode;
    private final String requesterName;
    private boolean extended;
    private String extensionJustification;

    public OmbudsmanCase(UUID protocolId, ProtocolChannel channel, LocalDate openedDate,
                         Integer ombudsmanDaysParameter,
                         DeadlineCountingRule rule, Set<LocalDate> holidays,
                         IdentityMode identityMode, String requesterName) {
        this.protocolId = Objects.requireNonNull(protocolId, "protocolo obrigatorio");
        this.channel = Objects.requireNonNull(channel, "canal do protocolo (P1.36) obrigatorio");
        this.openedDate = Objects.requireNonNull(openedDate, "abertura obrigatoria");
        // G1: autovalidacao do parametro \u2014 teto da Lei 13.460/2017 imposto na construcao.
        this.ombudsmanDays = SacParameters.ombudsmanDays(ombudsmanDaysParameter);
        this.identityMode = Objects.requireNonNull(identityMode, "modo de identidade obrigatorio");
        // M6: manifestante anonimo nao tem o nome retido em memoria.
        this.requesterName = identityMode == IdentityMode.ANONYMOUS ? null : requesterName;
        this.originalDueDate = DeadlineCalculator.computeDueDate(openedDate, this.ombudsmanDays, rule, holidays);
        this.currentDueDate = this.originalDueDate;
    }

    /** Nome exibido na fila: identidade preservada/anonima nunca vaza o nome real. */
    public String displayName() {
        return identityMode == IdentityMode.IDENTIFIED ? requesterName : "Identidade preservada";
    }

    public boolean identityExposed() { return identityMode == IdentityMode.IDENTIFIED; }

    /**
     * A UNICA prorrogacao, por igual periodo (Lei 13.460, art. 16), com justificativa
     * obrigatoria, contada do vencimento vigente pelo motor de prazos da P1.36.
     * Segunda prorrogacao e recusada.
     */
    public void extend(String justification, DeadlineCountingRule rule, Set<LocalDate> holidays) {
        if (justification == null || justification.isBlank())
            throw new IllegalArgumentException("prorrogacao da ouvidoria exige justificativa");
        if (extended) throw new IllegalStateException(
                "ouvidoria admite uma unica prorrogacao (Lei 13.460/2017, art. 16)");
        this.currentDueDate = DeadlineCalculator.extendDueDate(currentDueDate, ombudsmanDays, rule, holidays);
        this.extended = true;
        this.extensionJustification = justification;
    }

    public UUID protocolId() { return protocolId; }
    public ProtocolChannel channel() { return channel; }
    public LocalDate openedDate() { return openedDate; }
    public LocalDate originalDueDate() { return originalDueDate; }
    public LocalDate currentDueDate() { return currentDueDate; }
    public boolean extended() { return extended; }
    public String extensionJustification() { return extensionJustification; }
    public IdentityMode identityMode() { return identityMode; }
    /** Nome retido em memoria: null em modo ANONYMOUS (nunca armazenado). */
    public String requesterName() { return requesterName; }
    public boolean overdueOn(LocalDate date) { return date.isAfter(currentDueDate); }
}
