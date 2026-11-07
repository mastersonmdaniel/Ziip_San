package br.com.ziipsan.commercial.fiscal.domain;

import br.com.ziipsan.commercial.fiscal.nfse.NfseEtapa;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Tipo de serviço avulso com a etapa de obrigatoriedade dos grupos de IBS e CBS registrada
 * (Ato Conjunto RFB/CGIBS 4/2026) — item expresso do aceite da P3.6: "cada tipo de serviço
 * avulso registra a etapa". Sem etapa registrada não há NFS-e: a coluna é NOT NULL e a
 * regra ({@code NfseEtapaRule}) recusa etapa nula.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "fiscal_avulso_service_types")
@Getter
@Setter
public class AvulsoServiceTypeEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "nfse_etapa", nullable = false, length = 40)
    private NfseEtapa nfseEtapa;
}
