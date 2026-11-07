package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Arquivo de retorno bancario recebido (P3.1). A idempotencia do retorno e por
 * {@code (tenant_id, nsa, file_hash)}: reprocessar o mesmo arquivo (mesmo NSA e mesmo
 * hash SHA-256 do conteudo) nao cria novo arquivo, nao cria novos detalhes e nao duplica
 * baixa. Mesmo NSA com hash diferente e arquivo distinto (retorno retificado pelo banco)
 * e entra como novo arquivo.
 *
 * <p>{@code receiverDocument} e o CNPJ do recebedor/cedente do cabecalho, validado pela
 * validacao de CNPJ alfanumerico da P1.39 ({@code DocumentNumber}) antes da gravacao.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "return_files")
@Getter
@Setter
public class ReturnFileEntity extends TenantAwareEntity {

    /** Numero sequencial do arquivo atribuido pelo banco (NSA). */
    @Column(name = "nsa", nullable = false, length = 20)
    private String nsa;

    /** SHA-256 (hex, 64 posicoes) do conteudo bruto do arquivo. */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "file_name", length = 200)
    private String fileName;

    /** CNPJ do recebedor, normalizado (14 posicoes, aceita alfanumerico - P1.39). */
    @Column(name = "receiver_document", nullable = false, length = 14)
    private String receiverDocument;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    /** PROCESSADO ou REPROCESSADO_SEM_EFEITO. */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PROCESSADO";
}
