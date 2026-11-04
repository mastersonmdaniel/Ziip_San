package br.com.ziipsan.commercial.registry.legacy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma unidade do legado no staging (P1.2).
 *
 * <p>O que o staging guarda — e o que ele NÃO guarda:
 * <ul>
 *   <li>{@code connectedOn}: a data de ligação do legado (DAD-10), levada
 *       ao staging pela v0. A coluna {@code connected_on} da ligação é da
 *       P2.23, não daqui.</li>
 *   <li>Documento do titular: só espécie, máscara e validade (ver
 *       {@link LegacyTransform.DocumentValue}). Não existe campo para o
 *       documento bruto, em nenhuma camada.</li>
 *   <li>Códigos (localidade, setor, quadra, rota, categoria) como texto do
 *       legado: a resolução para os UUIDs do catálogo acontece na
 *       aplicação, e a falha de resolução barra a linha (não inventa
 *       catálogo).</li>
 *   <li>{@code status}: PENDING (encenada), APPLIED (unidade criada e
 *       confirmada pela OS de lote) ou REJECTED (fora do lote por erro;
 *       nunca aplicada).</li>
 * </ul>
 *
 * <p>A lista de issues é viva durante carga e aplicação (o serviço anexa
 * achados); depois de persistida, é lida como estava.
 */
public final class StagedUnit {

    public enum Status { PENDING, APPLIED, REJECTED }

    private final UUID id;
    private final UUID batchId;
    private final int sourceRowNo;
    private final String legacyKey;
    private final String matricula;
    private final String matriculaDv;
    private final String localityCode;
    private final String sectorCode;
    private final String blockCode;
    private final String routeCode;
    private final String categoryCode;
    private final Long lot;
    private final Long sublot;
    private final Long readingSequence;
    private final Long balanceCents;
    private final LocalDate connectedOn;
    private final String holderName;
    private final String holderDocumentKind;
    private final String holderDocumentMasked;
    private final Boolean holderDocumentValid;
    private final List<LegacyIssue> issues;
    private Status status;
    private UUID appliedUnitId;

    public StagedUnit(UUID id, UUID batchId, int sourceRowNo, String legacyKey,
                      String matricula, String matriculaDv,
                      String localityCode, String sectorCode, String blockCode,
                      String routeCode, String categoryCode,
                      Long lot, Long sublot, Long readingSequence,
                      Long balanceCents, LocalDate connectedOn,
                      String holderName, String holderDocumentKind,
                      String holderDocumentMasked, Boolean holderDocumentValid,
                      List<LegacyIssue> issues, Status status, UUID appliedUnitId) {
        this.id = id;
        this.batchId = batchId;
        this.sourceRowNo = sourceRowNo;
        this.legacyKey = legacyKey;
        this.matricula = matricula;
        this.matriculaDv = matriculaDv;
        this.localityCode = localityCode;
        this.sectorCode = sectorCode;
        this.blockCode = blockCode;
        this.routeCode = routeCode;
        this.categoryCode = categoryCode;
        this.lot = lot;
        this.sublot = sublot;
        this.readingSequence = readingSequence;
        this.balanceCents = balanceCents;
        this.connectedOn = connectedOn;
        this.holderName = holderName;
        this.holderDocumentKind = holderDocumentKind;
        this.holderDocumentMasked = holderDocumentMasked;
        this.holderDocumentValid = holderDocumentValid;
        this.issues = issues == null ? new ArrayList<>() : new ArrayList<>(issues);
        this.status = status == null ? Status.PENDING : status;
        this.appliedUnitId = appliedUnitId;
    }

    public UUID id() { return id; }
    public UUID batchId() { return batchId; }
    public int sourceRowNo() { return sourceRowNo; }
    public String legacyKey() { return legacyKey; }
    public String matricula() { return matricula; }
    public String matriculaDv() { return matriculaDv; }
    public String localityCode() { return localityCode; }
    public String sectorCode() { return sectorCode; }
    public String blockCode() { return blockCode; }
    public String routeCode() { return routeCode; }
    public String categoryCode() { return categoryCode; }
    public Long lot() { return lot; }
    public Long sublot() { return sublot; }
    public Long readingSequence() { return readingSequence; }
    public Long balanceCents() { return balanceCents; }
    public LocalDate connectedOn() { return connectedOn; }
    public String holderName() { return holderName; }
    public String holderDocumentKind() { return holderDocumentKind; }
    public String holderDocumentMasked() { return holderDocumentMasked; }
    public Boolean holderDocumentValid() { return holderDocumentValid; }
    public List<LegacyIssue> issues() { return issues; }
    public Status status() { return status; }
    public UUID appliedUnitId() { return appliedUnitId; }

    /** Tem erro: fora do lote até correção. */
    public boolean hasErrors() {
        return issues.stream().anyMatch(LegacyIssue::isError);
    }

    /** Aplicável: sem erro e ainda pendente. */
    public boolean isApplicable() {
        return status == Status.PENDING && !hasErrors();
    }

    public void addIssue(LegacyIssue issue) {
        issues.add(issue);
    }

    public void markApplied(UUID unitId) {
        this.status = Status.APPLIED;
        this.appliedUnitId = unitId;
    }

    public void markRejected() {
        this.status = Status.REJECTED;
    }
}
