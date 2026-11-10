package br.com.ziipsan.core.console;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MM-01 — Perfil de municipio do tenant (entidade), ao lado do
 * {@code tenant_profile} da core V9 (schema {@code platform}).
 *
 * <p>Uma linha por (tenant, municipio IBGE): e o desdobramento do
 * perfil-empresa mono-municipio em N perfis-municipio. Guarda SO a
 * existencia do perfil e a origem da derivacao que o criou:
 * parametros por municipio sao do MM-02 e tarifa por municipio e do
 * bloco tarifario — nenhuma coluna de configuracao vive aqui.
 *
 * <p>Herda por referencia (pelo {@code municipalityIbge}, D-MM-01):
 * {@code regulatory_profiles} (P2.31), {@code reg_contracts} (ED-96) e
 * os feriados de escopo MUNICIPAL — nada dessas pecas e duplicado.
 *
 * <p>Nao herda de {@code TenantAwareEntity}: tal como o
 * {@code tenant_profile} da V9, vive no schema {@code platform} (fora do
 * schema do tenant) e referencia {@code public.tenant_catalog} por
 * {@code tenant_id} simples — o mesmo desenho das vizinhas da V9.
 * Nao usar {@code @Builder} (convencao das entidades do projeto).
 */
@Entity
@Table(name = "tenant_municipality_profiles", schema = "platform",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_municipality_profiles_ibge",
                        columnNames = {"tenant_id", "municipality_ibge"}),
                @UniqueConstraint(name = "uq_tenant_municipality_profiles_tenant_id",
                        columnNames = {"tenant_id", "id"})
        })
@Getter
@Setter
public class TenantMunicipalityProfile {

    /** Origem da derivacao que criou o perfil (backfill MM-01, §5.1 do estudo). */
    public enum DerivationOrigin {
        /** IBGE presente nas localities do tenant (cadeia territorial). */
        LOCALITY_CHAIN,
        /** IBGE do perfil-empresa (tenant_profile.municipality_ibge). */
        TENANT_PROFILE
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Codigo IBGE com 7 digitos (mesmo dominio do tenant_profile da V9). */
    @Column(name = "municipality_ibge", nullable = false, length = 7, updatable = false)
    private String municipalityIbge;

    @Column(name = "derivation_origin", nullable = false, length = 20)
    private String derivationOrigin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
