package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.stationops.StationUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de geracao e destinacao de UM residuo da propria estacao
 * (OPE-25): lodo de ETA, lodo de ETE, residuo de gradeamento ou de
 * desarenacao — o tenant e o GERADOR do proprio residuo.
 *
 * <p>Maquina de estados: {@code GENERATED -> STORED -> DISPATCHED
 * -> CLOSED}. Cada passo exige o fato do aceite:
 * <ul>
 *   <li>geracao por unidade, com quantidade por PESAGEM ou por
 *       ESTIMATIVA DE VOLUME (o metodo e parte do registro — numero
 *       sem metodo nao entra);</li>
 *   <li>armazenamento temporario com local e instante;</li>
 *   <li>saida com transportador, destino (validado contra
 *       {@code waste.sludge_destination_catalog} pelo
 *       {@link SludgeService}) e documento de transporte;</li>
 *   <li>fechamento com a confirmacao da destinacao.</li>
 * </ul>
 * Gradeamento e desarenacao sao etapas preliminares da ETE: so
 * nascem em unidade WWTP. O vinculo opcional {@code shiftLogId} e a
 * leitura do diario de operacao da ED-87 (turno em que o residuo
 * foi gerado) — ver {@link DiaryEffluentFacts}.
 */
public final class SludgeRecord {

    public enum ResidueKind {
        /** Lodo de ETA ou de ETE (a unidade diz de qual estacao). */
        SLUDGE,
        /** Residuo de gradeamento (preliminar da ETE). */
        SCREENINGS,
        /** Residuo de desarenacao (preliminar da ETE). */
        GRIT
    }

    /** Como a quantidade foi obtida — pesagem ou estimativa de volume (aceite, OPE-25). */
    public enum QuantityMethod { WEIGHED, VOLUME_ESTIMATED }

    public enum Status { GENERATED, STORED, DISPATCHED, CLOSED }

    private final UUID id;
    private final UUID tenantId;
    private final UUID unitId;
    private final StationUnit.UnitKind unitKind;
    private final ResidueKind residueKind;
    private final String originCode;
    private final BigDecimal quantity;
    private final String quantityUnit;
    private final QuantityMethod quantityMethod;
    private final Instant generatedAt;
    private final UUID shiftLogId;
    private Status status = Status.GENERATED;
    private String storageLocation;
    private Instant storedAt;
    private String transporterRef;
    private String destinationCode;
    private String transportDocumentRef;
    private Instant dispatchedAt;
    private Instant closedAt;

    public SludgeRecord(UUID id, UUID tenantId, UUID unitId, StationUnit.UnitKind unitKind,
                        ResidueKind residueKind, String originCode, BigDecimal quantity,
                        String quantityUnit, QuantityMethod quantityMethod,
                        Instant generatedAt, UUID shiftLogId) {
        if (id == null) throw new IllegalArgumentException("id do registro de residuo e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (unitId == null) throw new IllegalArgumentException("a unidade geradora e obrigatoria");
        if (unitKind == null) throw new IllegalArgumentException("o tipo da unidade geradora e obrigatorio");
        if (unitKind != StationUnit.UnitKind.WTP && unitKind != StationUnit.UnitKind.WWTP)
            throw new IllegalArgumentException("residuo de estacao so nasce em ETA ou ETE, nao em " + unitKind);
        if (residueKind == null) throw new IllegalArgumentException("o tipo do residuo e obrigatorio");
        if (residueKind != ResidueKind.SLUDGE && unitKind != StationUnit.UnitKind.WWTP)
            throw new IllegalArgumentException("gradeamento e desarenacao sao preliminares da ETE:"
                    + " " + residueKind + " exige unidade WWTP");
        if (originCode == null || originCode.isBlank())
            throw new IllegalArgumentException("originCode e obrigatorio (a isencao de MTR se decide por ele)");
        if (quantity == null || quantity.signum() <= 0)
            throw new IllegalArgumentException("quantidade do residuo deve ser maior que zero");
        if (quantityUnit == null || quantityUnit.isBlank())
            throw new IllegalArgumentException("unidade da quantidade e obrigatoria (dado do registro)");
        if (quantityMethod == null)
            throw new IllegalArgumentException("o metodo da quantidade (pesagem ou estimativa) e obrigatorio");
        if (generatedAt == null) throw new IllegalArgumentException("instante da geracao e obrigatorio");
        this.id = id;
        this.tenantId = tenantId;
        this.unitId = unitId;
        this.unitKind = unitKind;
        this.residueKind = residueKind;
        this.originCode = originCode.trim();
        this.quantity = quantity;
        this.quantityUnit = quantityUnit.trim();
        this.quantityMethod = quantityMethod;
        this.generatedAt = generatedAt;
        this.shiftLogId = shiftLogId;
    }

    private void require(Status expected, String action) {
        if (status != expected) throw new IllegalStateException(
                "registro " + id + ": " + action + " exige estado " + expected
                        + ", estado atual " + status);
    }

    /** Armazenamento temporario: local e instante declarados. */
    public void store(String location, Instant at) {
        require(Status.GENERATED, "armazenamento temporario");
        if (location == null || location.isBlank())
            throw new IllegalArgumentException("local do armazenamento temporario e obrigatorio");
        if (at == null || at.isBefore(generatedAt))
            throw new IllegalArgumentException("instante do armazenamento anterior a geracao");
        this.storageLocation = location.trim();
        this.storedAt = at;
        this.status = Status.STORED;
    }

    /**
     * Saida para destinacao. O destino ja chega VALIDADO contra o
     * catalogo do tenant pelo {@link SludgeService}; aqui exigem-se
     * transportador e documento de transporte (OPE-25).
     */
    public void dispatch(String transporterRef, String destinationCode,
                         String transportDocumentRef, Instant at) {
        require(Status.STORED, "saida para destinacao");
        if (transporterRef == null || transporterRef.isBlank())
            throw new IllegalArgumentException("transportador da saida e obrigatorio");
        if (destinationCode == null || destinationCode.isBlank())
            throw new IllegalArgumentException("destino da saida e obrigatorio");
        if (transportDocumentRef == null || transportDocumentRef.isBlank())
            throw new IllegalArgumentException("documento de transporte da saida e obrigatorio");
        if (at == null || at.isBefore(storedAt))
            throw new IllegalArgumentException("instante da saida anterior ao armazenamento");
        this.transporterRef = transporterRef.trim();
        this.destinationCode = destinationCode.trim();
        this.transportDocumentRef = transportDocumentRef.trim();
        this.dispatchedAt = at;
        this.status = Status.DISPATCHED;
    }

    /** Fechamento: a destinacao foi confirmada (comprovante do destino, na fiacao). */
    public void close(Instant at) {
        require(Status.DISPATCHED, "fechamento");
        if (at == null || at.isBefore(dispatchedAt))
            throw new IllegalArgumentException("instante do fechamento anterior a saida");
        this.closedAt = at;
        this.status = Status.CLOSED;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID unitId() { return unitId; }
    public StationUnit.UnitKind unitKind() { return unitKind; }
    public ResidueKind residueKind() { return residueKind; }
    public String originCode() { return originCode; }
    public BigDecimal quantity() { return quantity; }
    public String quantityUnit() { return quantityUnit; }
    public QuantityMethod quantityMethod() { return quantityMethod; }
    public Instant generatedAt() { return generatedAt; }
    public UUID shiftLogId() { return shiftLogId; }
    public Status status() { return status; }
    public String storageLocation() { return storageLocation; }
    public Instant storedAt() { return storedAt; }
    public String transporterRef() { return transporterRef; }
    public String destinationCode() { return destinationCode; }
    public String transportDocumentRef() { return transportDocumentRef; }
    public Instant dispatchedAt() { return dispatchedAt; }
    public Instant closedAt() { return closedAt; }
}
