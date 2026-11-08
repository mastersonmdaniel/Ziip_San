package br.com.ziipsan.core.quality;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Uma versao do catalogo normativo (Portaria GM/MS 888/2021, conforme o
 * aceite). O catalogo e DADO carregado por tenant a partir da revisao
 * do responsavel tecnico — o produto nao traz nenhuma linha de fabrica:
 * catalogo vazio ou nao revisado significa conformidade INDETERMINADA,
 * nunca conformidade presumida.
 *
 * <p><b>Correcao M-3 (revisao da ED-50) — DECISAO REGISTRADA:</b>
 * vigencias SOBREPOSTAS do mesmo parametro nao sao mais aceitas na
 * carga. Antes, duas linhas revisadas sobrepostas (ex.: limite 10 desde
 * jan sem fim + limite 5 desde jun) eram aceitas e {@code standardOn}
 * devolvia a PRIMEIRA INSERIDA — a sonda E1 da revisao avaliou o valor
 * 7 em julho contra o limite 10 e obteve CONFORMING, quando a linha
 * vigente mais recente (5) daria NON_CONFORMING. Das opcoes do aceite,
 * escolheu-se a RECUSA NA CARGA/REGISTRO (e nao a resolucao silenciosa
 * pela mais recente): sobreposicao e erro de carga do catalogo, e erro
 * de carga se corrige na carga, com recusa nomeada — nao se emenda em
 * silencio na avaliacao. A sucessao legitima segue valida: a linha
 * antiga fecha a vigencia com {@code effectiveTo} e a nova comeca
 * depois. (No SQL pendente, a mesma garantia na integracao depende de
 * constraint de exclusao por faixa — fica registrada na
 * RELATORIO-CORRECAO-ED-50 como pendencia de integracao; o nucleo ja
 * recusa.)
 */
public record NormativeCatalog(String version, List<ParameterStandard> standards) {

    public NormativeCatalog {
        if (version == null || version.isBlank())
            throw new IllegalArgumentException("versao do catalogo e obrigatoria");
        if (standards == null)
            throw new IllegalArgumentException("lista de padroes e obrigatoria (pode ser vazia)");
        standards = List.copyOf(standards);
        long distinct = standards.stream()
                .map(s -> s.parameterKey() + "|" + s.effectiveFrom())
                .distinct().count();
        if (distinct != standards.size())
            throw new IllegalArgumentException("catalogo " + version
                    + " com padrao duplicado (mesmo parametro e mesma vigencia inicial)");
        // M-3: nenhuma sobreposicao de vigencia para o mesmo parametro.
        for (int i = 0; i < standards.size(); i++) {
            for (int j = i + 1; j < standards.size(); j++) {
                ParameterStandard a = standards.get(i);
                ParameterStandard b = standards.get(j);
                if (a.parameterKey().equals(b.parameterKey()) && overlaps(a, b))
                    throw new IllegalArgumentException("catalogo " + version
                            + " com vigencias sobrepostas para " + a.parameterKey()
                            + " (" + a.effectiveFrom() + ".." + a.effectiveTo() + " x "
                            + b.effectiveFrom() + ".." + b.effectiveTo()
                            + "): a carga deve fechar a vigencia da linha antiga"
                            + " (effectiveTo) antes da nova — sobreposicao nao se"
                            + " resolve por ordem de insercao (correcao M-3)");
            }
        }
    }

    private static boolean overlaps(ParameterStandard a, ParameterStandard b) {
        LocalDate aTo = a.effectiveTo() == null ? LocalDate.MAX : a.effectiveTo();
        LocalDate bTo = b.effectiveTo() == null ? LocalDate.MAX : b.effectiveTo();
        return !a.effectiveFrom().isAfter(bTo) && !b.effectiveFrom().isAfter(aTo);
    }

    public static NormativeCatalog empty(String version) {
        return new NormativeCatalog(version, List.of());
    }

    /** Padrao vigente na data, revisado ou nao — para exibicao e auditoria. */
    public Optional<ParameterStandard> standardOn(String parameterKey, LocalDate date) {
        return standards.stream()
                .filter(s -> s.parameterKey().equals(parameterKey) && s.effectiveOn(date))
                .findFirst();
    }

    /**
     * Padrao que pode fundamentar conformidade: vigente na data E
     * revisado pelo responsavel tecnico. Ausente ou nao revisado,
     * devolve vazio — quem avalia registra INDETERMINADO com o motivo.
     */
    public Optional<ParameterStandard> reviewedStandardOn(String parameterKey, LocalDate date) {
        return standardOn(parameterKey, date)
                .filter(ParameterStandard::reviewedByTechnicalResponsible);
    }
}
