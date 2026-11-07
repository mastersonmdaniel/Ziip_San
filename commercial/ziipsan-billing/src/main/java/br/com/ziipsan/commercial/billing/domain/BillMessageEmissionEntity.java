package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Prova de emissao de mensagem legal (P2.32, V4_10): uma linha por mensagem
 * que saiu numa conta — qual mensagem, em qual versao da conta, de qual
 * versao do modelo e de qual versao do catalogo. Montada a partir de
 * {@code LegalMessageEngine.EmissionProof}; a emissao nao muda depois de
 * gravada (mudanca posterior do modelo nao altera conta ja emitida, P2.30).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "bill_message_emissions")
@Getter
@Setter
public class BillMessageEmissionEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "bill_version_no", nullable = false)
    private int billVersionNo;

    @Column(name = "bill_template_version_id")
    private UUID billTemplateVersionId;

    @Column(name = "message_key", nullable = false, length = 80)
    private String messageKey;

    @Column(name = "norm_key", nullable = false, length = 160)
    private String normKey;

    @Column(name = "message_catalog_version", nullable = false)
    private int messageCatalogVersion;

    @Column(name = "content_catalog_version", nullable = false)
    private int contentCatalogVersion;

    @Column(name = "print_position", nullable = false)
    private int printPosition;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;
}
