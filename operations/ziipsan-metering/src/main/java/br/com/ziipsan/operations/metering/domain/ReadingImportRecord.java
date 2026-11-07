package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Uma linha do arquivo de volta, por tentativa de importacao (P2.7, V5_3).
 * {@code recordKey} e a chave de idempotencia do registro (fornecedor + rota
 * + referencia + unidade + tipo, de {@code VendorReadingLayout.recordKey}),
 * a mesma gravada em {@code meter_readings.idempotency_key}. So uma linha
 * {@code IMPORTED} pode existir por chave e tenant (indice unico parcial da
 * V5_3): o reenvio do mesmo registro vira linha {@code DUPLICATE}, de
 * auditoria, sem segunda leitura. Linha {@code ERROR} guarda o motivo em
 * {@code errorCode}/{@code errorMessage} e nao vira leitura -- a linha
 * recusada vai para o relatorio de erros, como no retorno de leitura interno.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "reading_import_records")
@Getter
@Setter
public class ReadingImportRecord extends TenantAwareEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    /** Numero da linha no arquivo (a linha 1 e o cabecalho). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "record_key", nullable = false, length = 120)
    private String recordKey;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "reading_id")
    private UUID readingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** IMPORTED gerou leitura; DUPLICATE e reenvio de registro ja importado; ERROR foi recusado com motivo. */
    public enum Status { IMPORTED, DUPLICATE, ERROR }
}
