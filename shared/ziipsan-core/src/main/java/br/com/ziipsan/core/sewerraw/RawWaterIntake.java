package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.watersystems.WaterSystem;

import java.util.Objects;
import java.util.UUID;

/**
 * Captacao de agua bruta (aceite ED-80). Ligada a:
 * <ul>
 *   <li>um sistema de abastecimento da ED-79 ({@code systemCode} —
 *       a ED-79 ja cadastra SAA/SES e o manancial DECLARADO; aqui
 *       nao se duplica manancial, apenas se referencia o sistema);</li>
 *   <li>uma outorga ({@link WaterGrant}) e uma licenca ambiental
 *       ({@link EnvironmentalLicense}) pelos ids — a vigencia e as
 *       condicionantes vivem nesses documentos, nao aqui.</li>
 * </ul>
 * O tipo de captacao reusa {@link WaterSystem.SourceKind} da ED-79
 * (SUPERFICIAL/SUBTERRANEA/MISTA nas chaves dela) — nenhum enum
 * paralelo e criado. O dominio do corpo hidrico vem da outorga.
 */
public record RawWaterIntake(
        UUID id,
        UUID tenantId,
        String systemCode,
        String code,
        String name,
        String waterBodyName,
        WaterSystem.SourceKind sourceKind,
        UUID grantId,
        UUID licenseId,
        boolean active) {

    public RawWaterIntake {
        Objects.requireNonNull(id, "id da captacao e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant da captacao e obrigatorio");
        if (systemCode == null || systemCode.isBlank())
            throw new IllegalArgumentException("sistema de abastecimento da ED-79 e obrigatorio na captacao");
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("codigo da captacao e obrigatorio");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("nome da captacao e obrigatorio");
        if (waterBodyName == null || waterBodyName.isBlank())
            throw new IllegalArgumentException("corpo hidrico da captacao e obrigatorio (declarado)");
        Objects.requireNonNull(sourceKind, "tipo da captacao (ED-79 SourceKind) e obrigatorio");
        Objects.requireNonNull(grantId, "outorga da captacao e obrigatoria (aceite ED-80)");
        Objects.requireNonNull(licenseId, "licenca ambiental da captacao e obrigatoria (aceite ED-80)");
    }
}
