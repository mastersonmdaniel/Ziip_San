package br.com.ziipsan.commercial.billing.catalog;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeVersion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Registro em memoria do catalogo unico de cobrancas (proposta de
 * arquitetura tarifaria, secoes 2.1 e 2.2). No mesmo espirito do
 * {@code BillInput} da P2.5 e do catalogo {@code debit_types} (DAD-20):
 * o dominio nao le nada — quem chama monta o registro a partir do
 * banco (tabelas {@code charge_definitions}/{@code charge_versions})
 * e o catalogo so guarda, valida e resolve por vigencia.
 *
 * <p>Recusas na montagem (ambiguidade nunca e resolvida por ordem de
 * lista — precedente {@code PriceTable} da ED-34):
 * <ul>
 *   <li>versao cuja vigencia se sobrepoe a outra versao da mesma
 *       cobranca (vigencias sobrepostas);</li>
 *   <li>duplicidade de (definicao, numero da versao) — versao
 *       duplicada;</li>
 *   <li>versao de definicao inexistente no registro;</li>
 *   <li>definicao duplicada (mesmo id, ou mesmo codigo NO MESMO
 *       ESCOPO — ver paragrafo do escopo);</li>
 *   <li>versao cujo modelo de calculo diverge do modelo declarado
 *       na definicao.</li>
 * </ul>
 *
 * <p><b>Unicidade por escopo (decisao do dono, item 45; adendo de
 * desenho da Camada 0, D-01.2 e premissa PM-03):</b> a definicao e
 * unica por (codigo, {@code municipalityIbge}). O mesmo codigo PODE
 * coexistir em escopos diferentes — escopo "empresa" ({@code null})
 * e municipios distintos entre si — e DEVE ser recusado dentro do
 * mesmo escopo, incluindo dois {@code null} (dois escopos "empresa"
 * com o mesmo codigo sao duplicidade). No banco, a mesma regra e o
 * indice unico por expressao sobre
 * {@code COALESCE(municipality_ibge, '')}.
 *
 * <p><b>Resolucao por codigo sensivel a escopo:</b>
 * {@link #resolveByCode(String, String, LocalDate)} recebe o
 * municipio derivado da unidade e aplica a precedencia da INV-C1-04-v2
 * (D-02.2): a definicao municipal daquele municipio, havendo versao
 * vigente dela, vence a de empresa para o mesmo codigo; sem versao
 * municipal vigente, cai para a de empresa; definicoes de OUTROS
 * municipios sao excluidas (nem vencem, nem bloqueiam). Com municipio
 * {@code null}, so o escopo "empresa" e candidato — comportamento
 * vigente. A forma sem municipio,
 * {@link #resolveByCode(String, LocalDate)}, delega com municipio
 * nulo. A resolucao por id nao muda: o id identifica uma definicao
 * (e portanto um escopo) sem ambiguidade.
 *
 * <p><b>Atomicidade de {@code add(definition, version)}</b> (correcao
 * C1-01): o atalho valida TUDO antes de mutar — definicao duplicada,
 * versao de outra definicao, modelo divergente, versao duplicada e
 * sobreposicao — e so entao registra definicao e versao. Se qualquer
 * validacao recusar, o catalogo fica exactamente como estava (a
 * definicao NAO fica registrada). {@code addDefinition} e
 * {@code addVersion} seguem publicos, com o comportamento documentado
 * em cada um: isolados, cada um valida a sua parte antes de mutar.
 *
 * <p>As listas devolvidas sao imutaveis e as colecoes recebidas sao
 * copiadas: o registro nao pode ser alterado por fora depois da
 * montagem senao pelos metodos {@code add}.
 */
public final class ChargeCatalog {

    private final Map<UUID, ChargeDefinition> definitionsById = new HashMap<>();
    private final Map<UUID, List<ChargeVersion>> versionsByDefinition = new HashMap<>();

    /** Catalogo vazio (o catalogo nasce vazio — precedente DAD-20). */
    public ChargeCatalog() {
    }

    /**
     * Monta o registro a partir de definicoes e versoes ja lidas pelo
     * chamador, aplicando as mesmas recusas dos metodos {@code add}.
     */
    public ChargeCatalog(List<ChargeDefinition> definitions, List<ChargeVersion> versions) {
        Objects.requireNonNull(definitions, "definicoes sao obrigatorias");
        Objects.requireNonNull(versions, "versoes sao obrigatorias");
        definitions.forEach(this::addDefinition);
        versions.forEach(this::addVersion);
    }

    /** Registra uma definicao; recusa id duplicado ou codigo duplicado no mesmo escopo. */
    public void addDefinition(ChargeDefinition definition) {
        Objects.requireNonNull(definition, "definicao e obrigatoria");
        validateNewDefinition(definition);
        definitionsById.put(definition.id(), definition);
    }

    /**
     * Registra uma versao; recusa definicao inexistente, versao
     * duplicada, modelo divergente da definicao e vigencia sobreposta
     * a outra versao da mesma cobranca.
     */
    public void addVersion(ChargeVersion version) {
        Objects.requireNonNull(version, "versao e obrigatoria");
        ChargeDefinition definition = definitionsById.get(version.chargeDefinitionId());
        if (definition == null) {
            throw new IllegalArgumentException(
                    "versao de definicao inexistente no catalogo: " + version.chargeDefinitionId());
        }
        validateVersionAgainst(definition, version);
        storeVersion(definition, version);
    }

    /**
     * Registra definicao e versao juntas (a versao deve ser da
     * definicao), de forma ATOMICA: pre-valida tudo (definicao
     * duplicada, versao de outra definicao, modelo divergente, versao
     * duplicada, sobreposicao) e so muta o registro depois de todas
     * as validacoes passarem. Em caso de recusa, nada fica registrado.
     */
    public void add(ChargeDefinition definition, ChargeVersion version) {
        Objects.requireNonNull(definition, "definicao e obrigatoria");
        Objects.requireNonNull(version, "versao e obrigatoria");
        if (!version.chargeDefinitionId().equals(definition.id())) {
            throw new IllegalArgumentException(
                    "versao de definicao inexistente para esta definicao: " + version.chargeDefinitionId());
        }
        // Pre-validacao completa ANTES de qualquer mutacao (C1-01).
        validateNewDefinition(definition);
        validateVersionAgainst(definition, version);
        // So entao registra.
        definitionsById.put(definition.id(), definition);
        storeVersion(definition, version);
    }

    private void validateNewDefinition(ChargeDefinition definition) {
        if (definitionsById.containsKey(definition.id())) {
            throw new IllegalArgumentException("definicao duplicada (id): " + definition.id());
        }
        // Unicidade por (codigo, escopo): mesmo codigo em escopo
        // diferente coexiste; no mesmo escopo (incl. null x null,
        // escopo "empresa") e duplicidade.
        for (ChargeDefinition existing : definitionsById.values()) {
            if (existing.code().equals(definition.code())
                    && Objects.equals(existing.municipalityIbge(), definition.municipalityIbge())) {
                throw new IllegalArgumentException(
                        "definicao duplicada (codigo no mesmo escopo): " + definition.code()
                                + " escopo=" + scopeLabel(definition.municipalityIbge()));
            }
        }
    }

    private static String scopeLabel(String municipalityIbge) {
        return municipalityIbge == null ? "empresa" : "municipio " + municipalityIbge;
    }

    private void validateVersionAgainst(ChargeDefinition definition, ChargeVersion version) {
        if (version.calculationModel() != definition.calculationModel()) {
            throw new IllegalArgumentException(
                    "modelo da versao diverge do modelo da definicao: " + definition.code());
        }
        List<ChargeVersion> existing = versionsByDefinition.getOrDefault(definition.id(), List.of());
        for (ChargeVersion other : existing) {
            if (other.version() == version.version()) {
                throw new IllegalArgumentException(
                        "versao duplicada para a cobranca " + definition.code() + ": versao " + version.version());
            }
            if (overlaps(other.validFrom(), other.validTo(), version.validFrom(), version.validTo())) {
                throw new IllegalArgumentException(
                        "vigencias sobrepostas para a cobranca " + definition.code()
                                + ": versao " + version.version() + " x versao " + other.version());
            }
        }
    }

    private void storeVersion(ChargeDefinition definition, ChargeVersion version) {
        List<ChargeVersion> existing = versionsByDefinition.getOrDefault(definition.id(), List.of());
        List<ChargeVersion> updated = new ArrayList<>(existing);
        updated.add(version);
        versionsByDefinition.put(definition.id(), List.copyOf(updated));
    }

    /** Resolve a versao vigente da cobranca (por id) na data de referencia. */
    public Optional<ChargeVersion> resolveVersion(UUID definitionId, LocalDate referenceDate) {
        return resolve(definitionId, referenceDate).map(ChargeResolution::version);
    }

    /**
     * Resolve a versao vigente da cobranca (por codigo, so escopo
     * "empresa") na data de referencia. Delega na forma sensivel a
     * escopo com municipio nulo.
     */
    public Optional<ChargeVersion> resolveVersionByCode(String code, LocalDate referenceDate) {
        return resolveByCode(code, referenceDate).map(ChargeResolution::version);
    }

    /**
     * Resolve a versao vigente da cobranca (por codigo) para o
     * municipio dado, com precedencia municipal sobre empresa
     * (INV-C1-04-v2). Municipio {@code null} = so escopo "empresa".
     */
    public Optional<ChargeVersion> resolveVersionByCode(String code, String municipalityIbge,
                                                        LocalDate referenceDate) {
        return resolveByCode(code, municipalityIbge, referenceDate).map(ChargeResolution::version);
    }

    /** Resolve definicao + versao vigente (por id) na data de referencia. */
    public Optional<ChargeResolution> resolve(UUID definitionId, LocalDate referenceDate) {
        Objects.requireNonNull(definitionId, "id da definicao e obrigatorio");
        Objects.requireNonNull(referenceDate, "data de referencia e obrigatoria");
        ChargeDefinition definition = definitionsById.get(definitionId);
        if (definition == null) {
            return Optional.empty();
        }
        return resolveFor(definition, referenceDate);
    }

    /**
     * Resolve definicao + versao vigente (por codigo, so escopo
     * "empresa") na data de referencia. Delega na forma sensivel a
     * escopo com municipio nulo — o comportamento vigente para quem
     * nao conhece o municipio da unidade.
     */
    public Optional<ChargeResolution> resolveByCode(String code, LocalDate referenceDate) {
        return resolveByCode(code, null, referenceDate);
    }

    /**
     * Resolve definicao + versao vigente (por codigo) para o municipio
     * derivado da unidade, com precedencia (INV-C1-04-v2 / D-02.2):
     * candidatas sao a definicao de escopo "empresa" e a do municipio
     * dado; havendo versao vigente da municipal, ela vence e a de
     * empresa e ignorada para este codigo; sem versao municipal
     * vigente, resolve a de empresa; definicoes de outros municipios
     * sao excluidas. Municipio {@code null} = so escopo "empresa".
     */
    public Optional<ChargeResolution> resolveByCode(String code, String municipalityIbge,
                                                    LocalDate referenceDate) {
        Objects.requireNonNull(code, "codigo da cobranca e obrigatorio");
        Objects.requireNonNull(referenceDate, "data de referencia e obrigatoria");
        if (municipalityIbge != null) {
            ChargeDefinition municipal = findByCodeAndScope(code, municipalityIbge);
            if (municipal != null) {
                Optional<ChargeResolution> resolved = resolveFor(municipal, referenceDate);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        }
        ChargeDefinition company = findByCodeAndScope(code, null);
        if (company == null) {
            return Optional.empty();
        }
        return resolveFor(company, referenceDate);
    }

    private ChargeDefinition findByCodeAndScope(String code, String municipalityIbge) {
        for (ChargeDefinition definition : definitionsById.values()) {
            if (definition.code().equals(code)
                    && Objects.equals(definition.municipalityIbge(), municipalityIbge)) {
                return definition;
            }
        }
        return null;
    }

    private Optional<ChargeResolution> resolveFor(ChargeDefinition definition, LocalDate referenceDate) {
        return versionsByDefinition.getOrDefault(definition.id(), List.of()).stream()
                .filter(v -> v.appliesTo(referenceDate))
                .findFirst()
                .map(v -> new ChargeResolution(definition, v));
    }

    /**
     * Cobrancas ativas com versao vigente na data de referencia
     * (definicao ativa e versao que se aplica a data), em lista
     * imutavel ordenada pelo codigo da cobranca. Listagem POR
     * DEFINICAO, sem precedencia de escopo: com o mesmo codigo em
     * varios escopos, cada definicao vigente aparece — quem resolve
     * para uma unidade concreta, aplicando a precedencia municipal,
     * e o {@link ChargeResolver} (ou o {@code resolveByCode} com
     * municipio).
     */
    public List<ChargeResolution> listActive(LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "data de referencia e obrigatoria");
        List<ChargeResolution> active = new ArrayList<>();
        for (ChargeDefinition definition : definitionsById.values()) {
            if (!definition.active()) {
                continue;
            }
            resolveFor(definition, referenceDate).ifPresent(active::add);
        }
        active.sort((a, b) -> a.definition().code().compareTo(b.definition().code()));
        return List.copyOf(active);
    }

    /**
     * Todas as definicoes registradas (lista imutavel, ordenada por
     * codigo e, dentro do codigo, por escopo — empresa primeiro).
     */
    public List<ChargeDefinition> definitions() {
        List<ChargeDefinition> all = new ArrayList<>(definitionsById.values());
        all.sort(Comparator.comparing(ChargeDefinition::code)
                .thenComparing(ChargeDefinition::municipalityIbge,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        return List.copyOf(all);
    }

    /** Versoes registradas de uma definicao (lista imutavel, ordenada por vigencia). */
    public List<ChargeVersion> versionsOf(UUID definitionId) {
        Objects.requireNonNull(definitionId, "id da definicao e obrigatorio");
        List<ChargeVersion> versions = new ArrayList<>(versionsByDefinition.getOrDefault(definitionId, List.of()));
        versions.sort((a, b) -> a.validFrom().compareTo(b.validFrom()));
        return List.copyOf(versions);
    }

    private static boolean overlaps(LocalDate f1, LocalDate t1, LocalDate f2, LocalDate t2) {
        LocalDate e1 = t1 == null ? LocalDate.MAX : t1;
        LocalDate e2 = t2 == null ? LocalDate.MAX : t2;
        return !f1.isAfter(e2) && !f2.isAfter(e1);
    }
}
