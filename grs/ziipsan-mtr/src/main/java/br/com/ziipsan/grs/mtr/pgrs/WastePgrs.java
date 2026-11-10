package br.com.ziipsan.grs.mtr.pgrs;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E4.
// PGRS electrónico do gerador: plano registado com linhas de resíduo codificadas (E5),
// responsável técnico como referência DECLARADA (nome + registro profissional, sem presumir
// habilitação), vigência e revisão. O enquadramento jurídico do tenant — quem é obrigado a ter
// PGRS — NÃO se presume: é decisão jurídica pendente (P-ED104-04, aviso expresso do
// levantamento). O sistema regista o plano e o seu estado; não declara obrigatoriedade.
@Entity
@Table(name = "waste_pgrs", indexes = {
        @Index(name = "idx_waste_pgrs_generator", columnList = "generator_id"),
        @Index(name = "idx_waste_pgrs_status", columnList = "status")
})
@Getter
@Setter
public class WastePgrs extends TenantAwareEntity {

    @Column(name = "generator_id", nullable = false)
    private UUID generatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PgrsStatus status = PgrsStatus.DRAFT;

    @Column(name = "technician_name", length = 200)
    private String technicianName;

    @Column(name = "technician_registry", length = 50)
    private String technicianRegistry; // registro profissional declarado (ex.: CREA), sem validação de habilitação

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "revision_due")
    private LocalDate revisionDue;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "lines", columnDefinition = "jsonb")
    private String lines; // linhas do plano serializadas com Json (PgrsService.PgrsLine)

    public enum PgrsStatus { DRAFT, ACTIVE, UNDER_REVISION, EXPIRED }
}
