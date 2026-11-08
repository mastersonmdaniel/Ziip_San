package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.certification.governance.LegalRequirement;
import br.com.ziipsan.core.watersystems.WaterSystem;

import java.util.UUID;

/**
 * Registro de captacao de agua bruta com as ligacoes do aceite
 * conferidas nas dependencias reais:
 * <ul>
 *   <li>o sistema existe na ED-79, e do tenant, esta ativo e e de
 *       ABASTECIMENTO (captacao nao se liga a sistema de esgoto);</li>
 *   <li>outorga e licenca sao do mesmo tenant da captacao e sao os
 *       documentos referenciados por ela;</li>
 *   <li>existe requisito legal da P3.31 ancorando a ligacao
 *       (id declarado no registro) — sem ele, o registro recusa:
 *       requisito legal nao se presume (P3.31).</li>
 * </ul>
 */
public final class IntakeRegistrationService {

    private final SewerRawPorts.SystemsPort systems;
    private final SewerRawPorts.LegalRequirementsPort legal;

    public IntakeRegistrationService(SewerRawPorts.SystemsPort systems,
                                     SewerRawPorts.LegalRequirementsPort legal) {
        if (systems == null) throw new IllegalArgumentException("porta de sistemas da ED-79 e obrigatoria");
        if (legal == null) throw new IllegalArgumentException("porta de requisitos legais da P3.31 e obrigatoria");
        this.systems = systems;
        this.legal = legal;
    }

    public RawWaterIntake register(RawWaterIntake intake, WaterGrant grant, EnvironmentalLicense license,
                                   UUID legalRequirementId) {
        if (intake == null || grant == null || license == null)
            throw new IllegalArgumentException("captacao, outorga e licenca sao obrigatorios");
        if (legalRequirementId == null)
            throw new IllegalArgumentException("requisito legal da P3.31 que ancora a captacao e obrigatorio");
        WaterSystem system = systems.systemOf(intake.tenantId(), intake.systemCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "sistema " + intake.systemCode() + " nao encontrado na ED-79 para o tenant"));
        if (!system.tenantId().equals(intake.tenantId()))
            throw new IllegalArgumentException("sistema da ED-79 de outro tenant");
        if (system.kind() != WaterSystem.Kind.SUPPLY)
            throw new IllegalArgumentException("captacao de agua bruta se liga a sistema de ABASTECIMENTO (ED-79), nao a " + system.kind());
        if (!system.active())
            throw new IllegalArgumentException("sistema da ED-79 inativo: " + intake.systemCode());
        if (!grant.id().equals(intake.grantId()) || !license.id().equals(intake.licenseId()))
            throw new IllegalArgumentException("documentos nao sao os ligados a captacao");
        if (!grant.tenantId().equals(intake.tenantId()) || !license.tenantId().equals(intake.tenantId()))
            throw new IllegalArgumentException("outorga/licenca de outro tenant");
        LegalRequirement req = legal.requirementOf(intake.tenantId(), legalRequirementId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "requisito legal " + legalRequirementId + " nao encontrado na P3.31 para o tenant"));
        if (!req.tenantId().equals(intake.tenantId()))
            throw new IllegalArgumentException("requisito legal da P3.31 de outro tenant");
        return intake;
    }
}
