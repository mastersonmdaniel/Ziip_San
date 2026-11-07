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
 * Enquadramento de uma unidade em categoria opcional da NR 13, art. 6o (RAS-13, P2.5;
 * tabela {@code optional_category_enrollments} da V4_4 fundida, secao 6; correcao B5
 * da revisao cruzada: a tabela nao entra orfa, entra com entidade e repositorio).
 *
 * <p>{@code categoryCode} e o codigo do catalogo de categorias opcionais do motor
 * ({@code OptionalCategoryRule}: VULNERABLE_RESIDENTIAL, MICRO_SMALL_BUSINESS,
 * COLLECTIVE_SINGLE_METER, ASSISTANCE_ENTITY), com CHECK no banco. {@code criterion} e
 * {@code proof} sao a COPIA do criterio e da prova que o tenant declarou em
 * {@code tariff.optional_categories} no ato do enquadramento (auditoria do porque):
 * sao dado potencialmente sensivel, classificados (SOC/SENS) e REDACT na trilha de
 * auditoria pela secao 8 da V4_4 (correcao B8) -- nunca aparecem em claro no
 * audit_log. Sem sobreposicao de enquadramento da mesma categoria na mesma unidade
 * (restricao de exclusao no banco, padrao de tariff_versions).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "optional_category_enrollments")
@Getter
@Setter
public class OptionalCategoryEnrollmentEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "category_code", nullable = false, length = 40)
    private String categoryCode;

    @Column(name = "criterion", nullable = false)
    private String criterion;

    @Column(name = "proof", nullable = false)
    private String proof;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;
}
