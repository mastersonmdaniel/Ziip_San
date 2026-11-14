package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Seleccao do programa de desoneracao aplicavel a uma emissao (DESENHO-PA02
 * secao 5.2, ponto 5 — correccao RR-02 da RE-REVISAO-PA-02, lote de
 * correccao 2).
 *
 * <p>Regra que governa — a letra do desenho aprovado (secao 5.2 ponto 5),
 * com o precedente de precedencia de escopo da secao 3.3 (aliquotas: o
 * escopo mais especifico vence) aplicado apenas onde o desenho o admite:
 * <ol>
 *   <li>entre os programas do tenant/escopo que accendem a guarda
 *       ({@code PUBLISHED}/{@code ACTIVE}) e cujo {@code effective_from}
 *       cubra a data de emissao, vence o <b>mais recente</b> (maior
 *       {@code effective_from}) — <b>sempre</b>, tambem entre escopos
 *       diferentes: um programa municipal antigo NAO vence um programa de
 *       empresa mais recente;</li>
 *   <li>empate no mesmo {@code effective_from} entre escopos
 *       <b>diferentes</b> (ex.: municipal e empresa): vence o <b>mais
 *       especifico</b> (municipal exacto vence empresa) — sem erro;</li>
 *   <li>empate no mesmo {@code effective_from} e no <b>mesmo</b> escopo:
 *       <b>erro de cadastro</b> fechado. A verificacao corre sobre todos os
 *       programas aplicaveis <b>antes</b> de qualquer seleccao, por isso o
 *       erro NAO e mascarado pela existencia de programas de outro escopo
 *       (nem mais especificos, nem mais recentes).</li>
 * </ol>
 *
 * <p>Regra rejeitada neste lote (decisao de coordenacao que fecha a RR-02):
 * a ordenacao anterior — especificidade de escopo ANTES da recencia, em
 * qualquer data — era invencao do lote de correccao 1, sem base no desenho;
 * fica substituida pela conformidade literal com a secao 5.2 ponto 5.
 */
public final class ReliefProgramSelector {

    private ReliefProgramSelector() {
    }

    /**
     * O programa aplicavel a uma emissao de uma unidade do municipio dado
     * ({@code null} = unidade sem municipio derivado, so escopo empresa).
     *
     * @return o programa seleccionado pela regra da classe, ou vazio se
     *         nenhum cobrir a emissao
     * @throws TariffConfigurationException se existirem dois programas
     *         aplicaveis com o mesmo {@code effective_from} e o mesmo escopo
     */
    public static Optional<ReliefProgram> selectApplicable(List<ReliefProgram> programs,
                                                           String municipalityIbge,
                                                           LocalDate issuedOn) {
        Objects.requireNonNull(programs, "programas sao obrigatorios");
        Objects.requireNonNull(issuedOn, "data de emissao e obrigatoria");
        List<ReliefProgram> covering = programs.stream()
                .filter(p -> p.status().engagesEmissionGuard())
                .filter(p -> !issuedOn.isBefore(p.effectiveFrom()))
                .filter(p -> p.municipalityIbge() == null
                        || p.municipalityIbge().equals(municipalityIbge))
                .toList();
        if (covering.isEmpty()) {
            return Optional.empty();
        }
        // Ramo 3 (erro de cadastro): verificado sobre TODOS os aplicaveis,
        // antes da recencia e da especificidade — nenhum programa de outro
        // escopo mascara a ambiguidade do mesmo escopo na mesma data.
        Map<String, List<ReliefProgram>> byDateAndScope = new LinkedHashMap<>();
        for (ReliefProgram p : covering) {
            String scopeKey = p.effectiveFrom() + "|"
                    + (p.municipalityIbge() == null ? "<empresa>" : p.municipalityIbge());
            byDateAndScope.computeIfAbsent(scopeKey, k -> new java.util.ArrayList<>()).add(p);
        }
        for (List<ReliefProgram> group : byDateAndScope.values()) {
            if (group.size() > 1) {
                ReliefProgram first = group.get(0);
                throw new TariffConfigurationException(
                        "Erro de cadastro: " + group.size() + " programas de desoneracao activos com o "
                                + "mesmo effective_from (" + first.effectiveFrom() + ") e o mesmo escopo ("
                                + group.get(0).code() + ", " + group.get(1).code() + ") — "
                                + "a ambiguidade nunca se resolve por ordem (DESENHO-PA02 secao 5.2.5)");
            }
        }
        // Ramo 1 (recencia primeiro): o maior effective_from entre TODOS os
        // escopos aplicaveis — sem pre-filtro de especificidade.
        LocalDate mostRecent = covering.stream().map(ReliefProgram::effectiveFrom)
                .max(LocalDate::compareTo).orElseThrow();
        List<ReliefProgram> atMostRecent = covering.stream()
                .filter(p -> p.effectiveFrom().equals(mostRecent)).toList();
        // Ramo 2 (desempate na mesma data, escopos diferentes): o mais
        // especifico vence — municipal exacto antes de empresa. Sem erro:
        // duplicados do mesmo escopo ja foram recusados acima, logo aqui ha
        // no maximo um programa por escopo.
        return atMostRecent.stream()
                .filter(p -> p.municipalityIbge() != null)
                .findFirst()
                .or(() -> Optional.of(atMostRecent.get(0)));
    }
}
