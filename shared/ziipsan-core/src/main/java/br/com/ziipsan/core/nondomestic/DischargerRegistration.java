package br.com.ziipsan.core.nondomestic;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cadastro do usuario nao domestico gerador de efluente (industria,
 * agroindustria, hospital, posto, lavanderia — aceite da ED-83,
 * CAD-70/OPE-13), com a marca {@code non_domestic_discharger} na
 * unidade consumidora.
 *
 * <p>Ciclo de vida: {@code REGISTERED -> AUTHORIZED}, com
 * {@code SUSPENDED} por nao conformidade e {@code CLOSED}. A
 * autorizacao de lancamento so nasce com (a) caracterizacao do
 * efluente registrada ({@link EffluentCharacterization}, com
 * pre-tratamento as expensas do usuario) e (b) termo de compromisso
 * referenciado — a referencia e do documento real do processo; o
 * TEXTO do termo nao e redigido nem presumido aqui [SEM FONTE].
 *
 * <p>Rota de atendimento (aceite): usuario COM cadastro comercial e
 * atendido pela sua unidade consumidora ({@link Route#COMMERCIAL_UNIT});
 * usuario SEM cadastro comercial e atendido pela ED-22
 * ({@link Route#CASUAL_VIA_ED22} — unidade avulsa de tipo proprio
 * {@code CASUAL} do catalogo, ADR-0039), declarado na criacao pelo
 * servico a partir da porta comercial (ver {@link NonDomesticService}).
 *
 * <p>A marca na unidade consumidora e o fato
 * {@link #consumerUnitFlag()} = {@code "non_domestic_discharger"}:
 * este nucleo a declara; a gravacao da marca na tabela da unidade e
 * da integracao (pendencia nomeada).
 */
public final class DischargerRegistration {

    /** Tipos de gerador nomeados no aceite; OTHER exige a descricao declarada do ramo. */
    public enum Kind { INDUSTRY, AGROINDUSTRY, HOSPITAL, FUEL_STATION, LAUNDRY, OTHER }

    /** Rota de atendimento do gerador: cadastro comercial proprio ou avulso da ED-22. */
    public enum Route { COMMERCIAL_UNIT, CASUAL_VIA_ED22 }

    public enum Status { REGISTERED, AUTHORIZED, SUSPENDED, CLOSED }

    /** Nome da marca gravada na unidade consumidora (literal do aceite). */
    public static final String CONSUMER_UNIT_FLAG = "non_domestic_discharger";

    private final UUID id;
    private final UUID tenantId;
    private final UUID consumerUnitId;
    private final Route route;
    private final Kind kind;
    private final String kindDescription;
    private final String dischargePointCode;
    private Status status = Status.REGISTERED;
    private EffluentCharacterization characterization;
    private String commitmentTermRef;
    private String authorizationRef;
    private LocalDate authorizationValidFrom;
    private LocalDate authorizationValidTo;
    private String suspensionReason;
    private Instant closedAt;

    public DischargerRegistration(UUID id, UUID tenantId, UUID consumerUnitId, Route route,
                                  Kind kind, String kindDescription, String dischargePointCode) {
        if (id == null) throw new IllegalArgumentException("id do cadastro e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId do cadastro e obrigatorio");
        if (consumerUnitId == null) throw new IllegalArgumentException(
                "unidade consumidora e obrigatoria: com cadastro comercial e a unidade do usuario;"
                        + " sem cadastro, e a unidade avulsa da ED-22 (CASUAL)");
        if (route == null) throw new IllegalArgumentException("rota de atendimento e obrigatoria");
        if (kind == null) throw new IllegalArgumentException("tipo do gerador e obrigatorio");
        if (kind == Kind.OTHER && (kindDescription == null || kindDescription.isBlank()))
            throw new IllegalArgumentException("gerador OTHER exige a descricao declarada do ramo");
        if (kind != Kind.OTHER && kindDescription != null && !kindDescription.isBlank()
                && !kindDescription.trim().equals(kind.name()))
            throw new IllegalArgumentException("descricao do ramo so se declara para OTHER");
        if (dischargePointCode == null || dischargePointCode.isBlank())
            throw new IllegalArgumentException("ponto de lancamento do usuario e obrigatorio:"
                    + " e nele que o plano de amostragem do prestador atua");
        this.id = id;
        this.tenantId = tenantId;
        this.consumerUnitId = consumerUnitId;
        this.route = route;
        this.kind = kind;
        this.kindDescription = kindDescription;
        this.dischargePointCode = dischargePointCode;
    }

    /** A marca que a integracao grava na unidade consumidora deste cadastro. */
    public String consumerUnitFlag() { return CONSUMER_UNIT_FLAG; }

    /** Classe da cobranca por taxa fixa: o tipo do gerador (chave da tabela do prestador). */
    public String chargeClassCode() {
        return kind == Kind.OTHER ? "OTHER:" + kindDescription.trim() : kind.name();
    }

    public void recordCharacterization(EffluentCharacterization value) {
        requireNotClosed("caracterizacao");
        if (value == null) throw new IllegalArgumentException("caracterizacao e obrigatoria");
        if (status == Status.AUTHORIZED)
            throw new IllegalStateException("cadastro ja autorizado: nova caracterizacao exige"
                    + " suspensao previa e nova autorizacao (o efluente autorizado e o caracterizado)");
        this.characterization = value;
    }

    /** Registra a referencia do termo de compromisso assinado (o texto e do processo, nao daqui). */
    public void recordCommitmentTerm(String termRef) {
        requireNotClosed("termo de compromisso");
        if (termRef == null || termRef.isBlank())
            throw new IllegalArgumentException("termo de compromisso exige a referencia do documento");
        if (status == Status.AUTHORIZED)
            throw new IllegalStateException("cadastro ja autorizado: termo ja fundamenta a autorizacao vigente");
        this.commitmentTermRef = termRef;
    }

    /**
     * Emite a autorizacao de lancamento. Portoes (aceite): caracterizacao
     * registrada E termo de compromisso referenciado. Sem os dois, recusa
     * nomeada — autorizacao nao se presume.
     */
    public void authorize(String authorizationRef, LocalDate validFrom, LocalDate validTo) {
        requireNotClosed("autorizacao");
        if (status == Status.AUTHORIZED)
            throw new IllegalStateException("cadastro ja esta autorizado (" + this.authorizationRef + ")");
        if (characterization == null)
            throw new IllegalStateException("autorizacao de lancamento exige a caracterizacao"
                    + " do efluente registrada (com pre-tratamento as expensas do usuario)");
        if (commitmentTermRef == null)
            throw new IllegalStateException("autorizacao de lancamento exige o termo de compromisso"
                    + " do usuario referenciado");
        if (authorizationRef == null || authorizationRef.isBlank())
            throw new IllegalArgumentException("autorizacao exige a referencia do ato (authorizationRef)");
        if (validFrom == null) throw new IllegalArgumentException("autorizacao exige o inicio da vigencia");
        if (validTo != null && validTo.isBefore(validFrom))
            throw new IllegalArgumentException("fim da vigencia da autorizacao anterior ao inicio");
        this.authorizationRef = authorizationRef;
        this.authorizationValidFrom = validFrom;
        this.authorizationValidTo = validTo;
        this.suspensionReason = null;
        this.status = Status.AUTHORIZED;
    }

    /** A autorizacao esta vigente na data? Cadastro suspenso/fechado nunca esta autorizado. */
    public boolean authorizedOn(LocalDate date) {
        return status == Status.AUTHORIZED && date != null
                && !date.isBefore(authorizationValidFrom)
                && (authorizationValidTo == null || !date.isAfter(authorizationValidTo));
    }

    /** Suspende o lancamento autorizado (ex.: nao conformidade) — o motivo e obrigatorio e fica. */
    public void suspend(String reason) {
        if (status != Status.AUTHORIZED)
            throw new IllegalStateException("suspensao exige cadastro AUTHORIZED, estado atual " + status);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("suspensao do lancamento exige o motivo registrado");
        this.suspensionReason = reason;
        this.status = Status.SUSPENDED;
    }

    /** Reautoriza apos a suspensao, dentro da vigencia original da autorizacao. */
    public void reinstate() {
        if (status != Status.SUSPENDED)
            throw new IllegalStateException("reautorizacao exige cadastro SUSPENDED, estado atual " + status);
        this.suspensionReason = null;
        this.status = Status.AUTHORIZED;
    }

    public void close(Instant at) {
        requireNotClosed("encerramento");
        if (at == null) throw new IllegalArgumentException("encerramento exige o instante");
        this.closedAt = at;
        this.status = Status.CLOSED;
    }

    private void requireNotClosed(String action) {
        if (status == Status.CLOSED)
            throw new IllegalStateException("cadastro encerrado nao admite " + action);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID consumerUnitId() { return consumerUnitId; }
    public Route route() { return route; }
    public Kind kind() { return kind; }
    public String kindDescription() { return kindDescription; }
    public String dischargePointCode() { return dischargePointCode; }
    public Status status() { return status; }
    public EffluentCharacterization characterization() { return characterization; }
    public String commitmentTermRef() { return commitmentTermRef; }
    public String authorizationRef() { return authorizationRef; }
    public LocalDate authorizationValidFrom() { return authorizationValidFrom; }
    public LocalDate authorizationValidTo() { return authorizationValidTo; }
    public String suspensionReason() { return suspensionReason; }
    public Instant closedAt() { return closedAt; }
}
