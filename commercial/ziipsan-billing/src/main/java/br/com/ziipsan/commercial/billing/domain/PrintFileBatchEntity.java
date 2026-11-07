package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Lote de arquivo para grafica gerado (P2.8): o registro persistido do lote
 * e SO o do manifesto — contagens, versoes e integridade. O CONTEUDO do
 * arquivo nao tem coluna aqui (vai ao pipeline de documentos da P1.20, com a
 * linha referenciada em {@code documentId}) e o codigo de vinculacao nao tem
 * coluna em tabela nenhuma desta tarefa: ele existe apenas dentro do
 * arquivo transmitido a grafica (ADR-0022; ver {@code LinkingCodeLeakTest}).
 *
 * <p>PENDENCIA DE MIGRATION DECLARADA: o mapa Flyway do plano v3.0 (SS3) nao
 * reserva versao para a P2.8 (a faixa {@code V4_x} do billing esta toda
 * atribuida: V4_3/V4_4 da P2.5, V4_10 da P2.32, V4_13 da P1.3, V4_14/V4_15 da
 * P2.3 etc.), portanto esta entrega NAO traz migration: a tabela
 * {@code print_file_batches} precisa de versao atribuida na integracao
 * (o DDL proposto vai no relatorio da tarefa). Ate la a entidade compila e
 * os testes puros rodam; a persistencia entra com a migration da camada.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "print_file_batches")
@Getter
@Setter
public class PrintFileBatchEntity extends TenantAwareEntity {

    @Column(name = "layout_version", nullable = false)
    private int layoutVersion;

    @Column(name = "profile_key", nullable = false, length = 60)
    private String profileKey;

    @Column(name = "content_catalog_version", nullable = false)
    private int contentCatalogVersion;

    @Column(name = "message_catalog_version", nullable = false)
    private int messageCatalogVersion;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "braille_count", nullable = false)
    private int brailleCount;

    @Column(name = "large_print_count", nullable = false)
    private int largePrintCount;

    @Column(name = "normal_count", nullable = false)
    private int normalCount;

    /** SHA-256 (hex) das linhas de registro do arquivo gerado. */
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    /** Linha de {@code documents} do pipeline (P1.20) com o arquivo guardado. */
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;
}
