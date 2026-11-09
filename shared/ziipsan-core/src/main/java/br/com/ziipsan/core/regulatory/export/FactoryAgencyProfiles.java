package br.com.ziipsan.core.regulatory.export;

import java.util.List;
import java.util.Optional;

/**
 * Perfis de saida de FABRICA para ARIS, AGIR e ARESC (ED-97, RAS-08) e a
 * regra RAS-31 para agencias fora de SC.
 *
 * <p>FONTES REAIS usadas: os atos citados no proprio aceite da ED-97 —
 * SISARIS por modulo (ARIS IN DG 1/2025; ARIS RN 11/2017) e relatorio
 * trimestral de SAC e ouvidoria da AGIR (AGIR RN 013/2022). O TEXTO dos
 * leiautes (campos, tipos, posicoes) NAO foi localizado nas fontes do
 * projeto: os campos entram como marcadores {@code SEM_FONTE}
 * parametrizaveis e {@code layoutComplete=false} (pendencia P-ED97-01).
 * Nenhum campo e apresentado como campo oficial da norma.</p>
 *
 * <p>ARESC: o aceite exige o perfil de fabrica, mas nao cita ato de
 * leiaute; o perfil nasce generico parametrizavel, {@code SEM_FONTE}
 * (pendencia P-ED97-02).</p>
 *
 * <p>RAS-31: agencia fora de SC nao tem perfil de fabrica — so sob
 * demanda de edital ou de tenant; enquanto nao houver perfil, valem os
 * valores proprios aceitos pelo tenant ({@link #tenantDefined}).</p>
 */
public final class FactoryAgencyProfiles {

    private FactoryAgencyProfiles() { }

    public static AgencyExportProfile arisSisaris() {
        return new AgencyExportProfile("ARIS", "ARIS_SISARIS", "SISARIS", "[A DEFINIR]",
                AgencyExportProfile.Periodicity.MONTHLY, AgencyExportProfile.Channel.ASSISTED_FILE,
                true, "ARIS IN DG 1/2025; ARIS RN 11/2017",
                List.of(LayoutField.semFonte("modulo [SEM FONTE]"),
                        LayoutField.semFonte("campos do modulo [SEM FONTE]")),
                false);
    }

    public static AgencyExportProfile agirSacOuvidoria() {
        return new AgencyExportProfile("AGIR", "AGIR_SAC_OUVIDORIA_TRIMESTRAL", "SAC_OUVIDORIA", "[A DEFINIR]",
                AgencyExportProfile.Periodicity.QUARTERLY, AgencyExportProfile.Channel.ASSISTED_FILE,
                true, "AGIR RN 013/2022",
                List.of(LayoutField.semFonte("campos do relatorio trimestral de SAC [SEM FONTE]"),
                        LayoutField.semFonte("campos do relatorio trimestral de ouvidoria [SEM FONTE]")),
                false);
    }

    public static AgencyExportProfile arescGeneric() {
        return new AgencyExportProfile("ARESC", "ARESC_GENERICO", "GENERICO_REG", "[A DEFINIR]",
                AgencyExportProfile.Periodicity.MONTHLY, AgencyExportProfile.Channel.ASSISTED_FILE,
                true, "[SEM FONTE] ato de leiaute da ARESC nao citado no aceite nem localizado",
                List.of(LayoutField.semFonte("campos do leiaute ARESC [SEM FONTE]")),
                false);
    }

    public static List<AgencyExportProfile> factoryProfiles() {
        return List.of(arisSisaris(), agirSacOuvidoria(), arescGeneric());
    }

    /** Perfil de fabrica por agencia (vazio para agencia fora de SC — RAS-31). */
    public static Optional<AgencyExportProfile> factoryFor(String agencyCode) {
        if (agencyCode == null) return Optional.empty();
        return factoryProfiles().stream()
                .filter(p -> p.agencyCode().equalsIgnoreCase(agencyCode.trim())).findFirst();
    }

    /**
     * Perfil definido pelo tenant (RAS-31): agencia sem perfil de fabrica
     * usa valores proprios ACEITOS pelo tenant. O aceite e obrigatorio e
     * registrado; sem ele, nao ha perfil.
     */
    public static AgencyExportProfile tenantDefined(String agencyCode, String layoutId,
            String layoutVersion, AgencyExportProfile.Periodicity periodicity,
            List<LayoutField> fields, boolean tenantAccepted) {
        if (!tenantAccepted) {
            throw new IllegalArgumentException("perfil proprio exige aceite do tenant (RAS-31): " + agencyCode);
        }
        return new AgencyExportProfile(agencyCode, "TENANT_" + agencyCode, layoutId, layoutVersion,
                periodicity, AgencyExportProfile.Channel.ASSISTED_FILE, false,
                "valores proprios aceitos pelo tenant (RAS-31)", fields,
                // Correcao D5 (REVISAO-ED-97): lista vazia NUNCA e leiaute
                // completo — noneMatch sobre lista vazia e vacuamente
                // verdadeiro; exige-se ao menos um campo.
                fields != null && !fields.isEmpty() && fields.stream().noneMatch(
                        f -> f.sourceStatus() == LayoutField.SourceStatus.SEM_FONTE));
    }
}
