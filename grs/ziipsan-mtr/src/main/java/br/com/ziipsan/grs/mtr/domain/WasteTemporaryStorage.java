package br.com.ziipsan.grs.mtr.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E1.
// Armazenador temporário: o quarto actor do modelo de partes da Portaria MMA 280/2020
// (arts. 3º e 13, via levantamento regulatório). Recebe o resíduo e emite MTR Complementar;
// sem ele o modelo só tinha gerador/transportador/destinador. Com armazenador temporário,
// o MTR leva 1 só resíduo (regra do Manual do Utilizador do MTR Nacional, via levantamento).
@Entity
@Table(name = "waste_temporary_storage", indexes = {
        @Index(name = "idx_waste_temp_storage_cnpj", columnList = "cnpj"),
        @Index(name = "idx_waste_temp_storage_status", columnList = "status")
})
@Getter
@Setter
public class WasteTemporaryStorage extends TenantAwareEntity {

    @Column(name = "cnpj", length = 18, nullable = false, unique = true)
    private String cnpj;

    @Column(name = "corporate_name", nullable = false, length = 200)
    private String corporateName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "address", columnDefinition = "jsonb")
    private String address;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "environmental_license", columnDefinition = "jsonb")
    private String environmentalLicense;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;
}
