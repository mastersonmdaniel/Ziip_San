package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Fornecedor de leitura terceirizada (P2.7, V5_3): a empresa contratada pelo
 * prestador para ler uma ou mais rotas. {@code layoutCode} identifica o leiaute
 * de arquivo do fornecedor (o leiaute que o sistema sabe ler e escrever, hoje
 * {@code PILOTO_CSV_V1} de {@code VendorReadingLayout}); um fornecedor com
 * leiaute desconhecido e recusado na importacao/exportacao, nunca convertido
 * por aproximacao. Sem semente: fornecedor e dado do contrato do tenant.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "reading_vendors")
@Getter
@Setter
public class ReadingVendor extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Documento do fornecedor (CNPJ), quando o contrato o registra; sem mascara aqui. */
    @Column(name = "document", length = 20)
    private String document;

    @Column(name = "layout_code", nullable = false, length = 30)
    private String layoutCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
