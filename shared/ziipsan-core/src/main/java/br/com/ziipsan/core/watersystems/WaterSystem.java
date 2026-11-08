package br.com.ziipsan.core.watersystems;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sistema de abastecimento (SAA) e sistema de esgoto (SES) — ED-79.
 *
 * <p>A entidade carrega o que o aceite nomeia: componentes (ver
 * {@link SystemComponent}), populacao atendida (com a DATA de referencia
 * — populacao sem data seria numero solto), manancial e tratamento.
 * Os TIPOS de componente e de unidade de tratamento sao chaves do
 * catalogo de fabrica da P1.27 (GIS-02): esta tarefa nao cria tipos,
 * so referencia as chaves; a existencia/atividade da chave e conferida
 * pela porta {@link SystemsPorts.FactoryCatalogPort} no registro.
 * As unidades de tratamento sao as estruturas de planta cadastradas
 * com geometria na ED-53 (ETA/ETE/elevatorias/reservatorio, SG-148 a
 * SG-152), referenciadas pelo codigo.
 *
 * <p>Regras de forma (sem fonte externa alem do aceite):
 * <ul>
 *   <li>SUPPLY exige manancial (tipo + nome) e tratamento;</li>
 *   <li>SEWAGE nao tem manancial de captacao nesta entidade (o aceite
 *       nao nomeia corpo receptor — nada inventado) e exige
 *       tratamento;</li>
 *   <li>a referencia de outorga do manancial e OPCIONAL e declarada
 *       pelo tenant quando existir: {@code [SEM FONTE]} — nenhuma
 *       regra de outorga consta das fontes desta tarefa.</li>
 * </ul>
 */
public record WaterSystem(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        Kind kind,
        int servedPopulation,
        LocalDate populationAsOf,
        SourceInfo source,
        TreatmentInfo treatment,
        boolean active) {

    public enum Kind { SUPPLY, SEWAGE }

    /** Tipo de manancial declarado pelo tenant (captacao). */
    public enum SourceKind { SURFACE, GROUNDWATER, MIXED }

    /** Manancial: tipo e nome sao do cadastro do tenant; a referencia de outorga e opcional [SEM FONTE]. */
    public record SourceInfo(SourceKind kind, String name, String grantRef) {
        public SourceInfo {
            Objects.requireNonNull(kind, "tipo do manancial e obrigatorio");
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("nome do manancial e obrigatorio");
        }
    }

    /**
     * Tratamento: unidades (codigos das estruturas da ED-53) e/ou tipos
     * do catalogo da P1.27, com a descricao declarada pelo tenant.
     * Pelo menos um dos tres elementos tem de existir — "tratamento"
     * vazio nao e tratamento declarado.
     */
    public record TreatmentInfo(List<String> unitCodes, List<String> catalogTypeKeys, String description) {
        public TreatmentInfo {
            unitCodes = unitCodes == null ? List.of() : List.copyOf(unitCodes);
            catalogTypeKeys = catalogTypeKeys == null ? List.of() : List.copyOf(catalogTypeKeys);
            boolean hasUnits = !unitCodes.isEmpty();
            boolean hasTypes = !catalogTypeKeys.isEmpty();
            boolean hasDescription = description != null && !description.isBlank();
            if (!hasUnits && !hasTypes && !hasDescription)
                throw new IllegalArgumentException(
                        "tratamento exige unidade (ED-53), tipo do catalogo (P1.27) ou descricao declarada");
        }
    }

    public WaterSystem {
        Objects.requireNonNull(id, "id do sistema e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do sistema e obrigatorio");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("nome do sistema e obrigatorio: " + code);
        Objects.requireNonNull(kind, "tipo do sistema (SUPPLY/SEWAGE) e obrigatorio");
        if (servedPopulation < 0) throw new IllegalArgumentException("populacao atendida nao pode ser negativa");
        Objects.requireNonNull(populationAsOf, "data de referencia da populacao atendida e obrigatoria");
        Objects.requireNonNull(treatment, "tratamento e obrigatorio (aceite ED-79): " + code);
        if (kind == Kind.SUPPLY && source == null)
            throw new IllegalArgumentException("sistema de abastecimento exige manancial (aceite ED-79): " + code);
        if (kind == Kind.SEWAGE && source != null)
            throw new IllegalArgumentException("sistema de esgoto nao declara manancial de captacao nesta entidade: " + code);
    }

    /**
     * Componente de um sistema: o TIPO e chave do catalogo da P1.27;
     * a feicao GIS (ED-53) e opcional no cadastro — quando presente, a
     * existencia dela e conferida pela porta da ED-53 no registro.
     * Componente de tipo DESATIVADO no catalogo continua valido aqui
     * (regra da P1.27: item desativado continua valido nas feicoes
     * existentes) — o registro guarda a flag, nao recusa.
     */
    public record SystemComponent(
            UUID id,
            UUID tenantId,
            String systemCode,
            String componentTypeKey,
            UUID gisFeatureId,
            String name,
            boolean active) {
        public SystemComponent {
            Objects.requireNonNull(id, "id do componente e obrigatorio");
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (systemCode == null || systemCode.isBlank())
                throw new IllegalArgumentException("sistema do componente e obrigatorio");
            if (componentTypeKey == null || componentTypeKey.isBlank())
                throw new IllegalArgumentException("tipo do componente (catalogo P1.27) e obrigatorio");
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("nome do componente e obrigatorio: " + componentTypeKey);
        }
    }
}
