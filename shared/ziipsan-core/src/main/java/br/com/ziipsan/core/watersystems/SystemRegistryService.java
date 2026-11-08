package br.com.ziipsan.core.watersystems;

/**
 * Registro de sistemas e componentes (ED-79): o sistema nasce no
 * cadastro; o componente so nasce com o tipo EXISTENTE no catalogo da
 * P1.27 e, quando referencia feicao, com a feicao EXISTENTE na ED-53.
 * Tipo desativado no catalogo NAO recusa (regra da P1.27: item
 * desativado continua valido nas feicoes existentes) — o registro
 * devolve a flag para a tela/integracao exibir.
 */
public final class SystemRegistryService {
    private SystemRegistryService() {}

    /** Resultado do registro de componente: o componente e a atividade do tipo no catalogo. */
    public record ComponentRegistration(WaterSystem.SystemComponent component, boolean catalogTypeActive) {}

    /**
     * Registra o sistema. <b>Correcao D4 (revisao da ED-79,
     * 08/11/2026):</b> as chaves de tratamento declaradas por tipo
     * ({@code TreatmentInfo.catalogTypeKeys}) sao chaves do catalogo
     * da P1.27 e passam a ser CONFERIDAS aqui, pela
     * {@link SystemsPorts.FactoryCatalogPort} — antes o registro
     * gravava a chave sem conferir (admitido em comentario no codigo
     * e nomeado na pendencia P-ED79-09, cuja parte de conferencia se
     * fecha com esta correcao; a guarda de completude de componentes
     * por tipo da P-ED79-09 segue pendencia — nenhuma fonte define
     * quais componentes cada tipo de sistema exige). Chave de
     * tratamento inexistente no catalogo recusa com
     * {@code TREATMENT_TYPE_UNKNOWN}; tipo DESATIVADO nao recusa
     * (mesma regra da P1.27 aplicada aos componentes). Os codigos de
     * unidade ({@code unitCodes}) sao codigos de estrutura da ED-53,
     * nao chaves da P1.27 — seguem referenciados, sem conferencia
     * nesta porta.
     */
    public static WaterSystem registerSystem(SystemsStore store,
                                             SystemsPorts.FactoryCatalogPort catalog,
                                             WaterSystem system) {
        if (store.system(system.tenantId(), system.code()).isPresent())
            throw new SystemsExceptions.ValidationException("SYSTEM_CODE_DUPLICATE",
                    "ja existe sistema com o codigo " + system.code() + " neste tenant");
        for (String typeKey : system.treatment().catalogTypeKeys()) {
            if (!catalog.typeExists(system.tenantId(), typeKey))
                throw new SystemsExceptions.ValidationException("TREATMENT_TYPE_UNKNOWN",
                        "tipo de tratamento fora do catalogo da P1.27 (GIS-02): " + typeKey
                                + " (sistema " + system.code() + ")");
        }
        store.saveSystem(system);
        return system;
    }

    public static ComponentRegistration registerComponent(SystemsStore store,
                                                          SystemsPorts.FactoryCatalogPort catalog,
                                                          SystemsPorts.GisFeaturePort gis,
                                                          WaterSystem.SystemComponent component) {
        if (store.system(component.tenantId(), component.systemCode()).isEmpty())
            throw new SystemsExceptions.NotFoundException("SYSTEM_NOT_FOUND",
                    "componente de sistema inexistente: " + component.systemCode());
        if (!catalog.typeExists(component.tenantId(), component.componentTypeKey()))
            throw new SystemsExceptions.ValidationException("CATALOG_TYPE_UNKNOWN",
                    "tipo de componente fora do catalogo da P1.27 (GIS-02): " + component.componentTypeKey());
        if (component.gisFeatureId() != null && !gis.featureExists(component.tenantId(), component.gisFeatureId()))
            throw new SystemsExceptions.ValidationException("GIS_FEATURE_UNKNOWN",
                    "feicao da ED-53 inexistente para o componente: " + component.gisFeatureId());
        boolean active = catalog.typeActive(component.tenantId(), component.componentTypeKey());
        store.saveComponent(component);
        return new ComponentRegistration(component, active);
    }
}
