package br.com.ziipsan.core.inspection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de fraude (aceite da ED-24: "fraude registrada com tipo, fotos e
 * boletim"). Tipo, fotos e boletim sao OBRIGATORIOS na construcao — registro
 * sem um dos tres e recusado, nao gravado parcial.
 *
 * <p>Fotos e boletim sao documentos do pipeline da P1.20: aqui vivem so os
 * ids dos documentos (o arquivo e a varredura sao da P1.20/ADR-0020, mesmo
 * desenho dos anexos da ED-36). O numero do boletim de ocorrencia e o texto
 * livre lancado pela equipe/autoridade — nenhum formato de boletim foi
 * localizado nas fontes, por isso ele e guardado como texto, sem mascara
 * inventada.
 *
 * <p>[A DEFINIR] O catalogo de tipos de fraude do tenant/ERI nao esta nas
 * fontes locais. {@link FraudType} e o catalogo PROPOSTO por esta frente a
 * partir do vocabulario do dominio (desvio, violacao de hidrometro,
 * religacao nao autorizada); a integracao deve conferir/ajustar o catalogo
 * antes de declarar os CHECKs como definitivos.
 */
public record FraudRecord(UUID id, UUID consumerUnitId, UUID inspectionOrderId,
                          FraudType type, String description,
                          List<UUID> photoDocumentIds, String policeReportNumber,
                          FraudStatus status, UUID registeredBy,
                          LocalDateTime registeredAt) {

    /** [A DEFINIR — catalogo proposto, ver javadoc da classe] Tipos de fraude. */
    public enum FraudType {
        /** Desvio/derivacao que contorna o hidrometro (bypass). */
        BYPASS,
        /** Ligacao clandestina sem cadastro/medicao. */
        CLANDESTINE_CONNECTION,
        /** Violacao, adulteracao ou inversao do hidrometro. */
        METER_TAMPERING,
        /** Religacao feita pelo usuario em ligacao cortada, sem OS de religacao. */
        UNAUTHORIZED_RECONNECTION,
        /** Outro tipo, com a descricao obrigatoria detalhando. */
        OTHER
    }

    public enum FraudStatus {
        /** Registrada em campo, aguardando confirmacao. */
        SUSPECTED,
        /** Confirmada — so fraude confirmada pode gerar auto de infracao. */
        CONFIRMED,
        /** Descartada na verificacao (nao era fraude). */
        DISMISSED
    }

    public FraudRecord {
        Objects.requireNonNull(id, "id do registro de fraude e obrigatorio");
        Objects.requireNonNull(consumerUnitId, "unidade consumidora e obrigatoria");
        Objects.requireNonNull(inspectionOrderId, "OS de fiscalizacao de origem e obrigatoria");
        Objects.requireNonNull(type, "tipo da fraude e obrigatorio (aceite ED-24)");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("descricao da fraude e obrigatoria");
        if (photoDocumentIds == null || photoDocumentIds.isEmpty())
            throw new IllegalArgumentException("fraude sem foto nao se registra (aceite ED-24: tipo, fotos e boletim)");
        photoDocumentIds = List.copyOf(photoDocumentIds);
        if (photoDocumentIds.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("foto nula no registro de fraude");
        if (policeReportNumber == null || policeReportNumber.isBlank())
            throw new IllegalArgumentException("fraude sem boletim nao se registra (aceite ED-24: tipo, fotos e boletim)");
        Objects.requireNonNull(status, "situacao da fraude e obrigatoria");
        Objects.requireNonNull(registeredBy, "quem registrou e obrigatorio");
        Objects.requireNonNull(registeredAt, "data/hora do registro e obrigatoria");
    }

    /** So fraude CONFIRMED pode sustentar auto de infracao. */
    public boolean canSupportInfractionNotice() {
        return status == FraudStatus.CONFIRMED;
    }

    public FraudRecord withStatus(FraudStatus newStatus) {
        Objects.requireNonNull(newStatus, "nova situacao e obrigatoria");
        return new FraudRecord(id, consumerUnitId, inspectionOrderId, type, description,
                photoDocumentIds, policeReportNumber, newStatus, registeredBy, registeredAt);
    }
}
