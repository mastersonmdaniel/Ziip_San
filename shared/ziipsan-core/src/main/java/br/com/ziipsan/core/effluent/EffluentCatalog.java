package br.com.ziipsan.core.effluent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Uma versao do catalogo de padrao de efluente (federal + estadual,
 * do catalogo de normas). Versionado e com vigencia futura: a
 * sucessao legitima fecha a vigencia da linha antiga
 * ({@code effectiveTo}) e a nova comeca na data de efeito — vigencias
 * SOBREPOSTAS do mesmo parametro na mesma esfera sao recusadas na
 * carga, como na correcao M-3 da ED-50 (sobreposicao e erro de carga
 * e se corrige na carga, nao se resolve por ordem de insercao).
 *
 * <p>Este catalogo e o contrato consumido pela ED-83
 * ({@code effluent.user_standard_catalog} se apoia nele) e pela
 * avaliacao de conformidade desta tarefa. Catalogo vazio ou sem
 * linha revisada significa conformidade INDETERMINADA, nunca
 * conformidade presumida.
 */
public record EffluentCatalog(String version, List<EffluentStandard> standards) {

    public EffluentCatalog {
        if (version == null || version.isBlank())
            throw new IllegalArgumentException("versao do catalogo de efluente e obrigatoria");
        if (standards == null)
            throw new IllegalArgumentException("lista de padroes e obrigatoria (pode ser vazia)");
        standards = List.copyOf(standards);
        long distinct = standards.stream()
                .map(s -> s.parameterKey() + "|" + s.jurisdictionKey() + "|" + s.effectiveFrom())
                .distinct().count();
        if (distinct != standards.size())
            throw new IllegalArgumentException("catalogo " + version
                    + " com padrao duplicado (mesmo parametro, esfera e vigencia inicial)");
        for (int i = 0; i < standards.size(); i++) {
            for (int j = i + 1; j < standards.size(); j++) {
                EffluentStandard a = standards.get(i);
                EffluentStandard b = standards.get(j);
                if (a.parameterKey().equals(b.parameterKey())
                        && a.jurisdictionKey().equals(b.jurisdictionKey()) && overlaps(a, b))
                    throw new IllegalArgumentException("catalogo " + version
                            + " com vigencias sobrepostas para " + a.parameterKey()
                            + " (" + a.jurisdictionKey() + "): " + a.effectiveFrom() + ".."
                            + a.effectiveTo() + " x " + b.effectiveFrom() + ".." + b.effectiveTo()
                            + ": a carga deve fechar a vigencia da linha antiga (effectiveTo)"
                            + " antes da nova — sobreposicao nao se resolve por ordem de insercao");
            }
        }
    }

    private static boolean overlaps(EffluentStandard a, EffluentStandard b) {
        LocalDate aTo = a.effectiveTo() == null ? LocalDate.MAX : a.effectiveTo();
        LocalDate bTo = b.effectiveTo() == null ? LocalDate.MAX : b.effectiveTo();
        return !a.effectiveFrom().isAfter(bTo) && !b.effectiveFrom().isAfter(aTo);
    }

    public static EffluentCatalog empty(String version) {
        return new EffluentCatalog(version, List.of());
    }

    /** Padrao da esfera vigente na data, revisado ou nao — para exibicao e auditoria. */
    public Optional<EffluentStandard> standardOn(String parameterKey,
                                                 EffluentStandard.Jurisdiction jurisdiction,
                                                 String stateCode, LocalDate date) {
        String key = jurisdiction == EffluentStandard.Jurisdiction.FEDERAL
                ? "FEDERAL" : "STATE:" + stateCode;
        return standards.stream()
                .filter(s -> s.parameterKey().equals(parameterKey)
                        && s.jurisdictionKey().equals(key) && s.effectiveOn(date))
                .findFirst();
    }

    /**
     * Padrao que pode fundamentar conformidade: vigente na data E
     * revisado pelo responsavel tecnico (toda linha do catalogo ja e
     * de norma publicada por construcao — ver {@link EffluentStandard}).
     */
    public Optional<EffluentStandard> reviewedStandardOn(String parameterKey,
                                                         EffluentStandard.Jurisdiction jurisdiction,
                                                         String stateCode, LocalDate date) {
        return standardOn(parameterKey, jurisdiction, stateCode, date)
                .filter(EffluentStandard::reviewedByTechnicalResponsible);
    }

    /** Linhas com vigencia futura na data (carregadas, ainda sem efeito). */
    public List<EffluentStandard> futureStandards(LocalDate date) {
        return standards.stream().filter(s -> s.effectiveFrom().isAfter(date)).toList();
    }
}
