package br.com.ziipsan.commercial.billing.catalog;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeVersion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolvedor de cobrancas por unidade/competencia sobre o
 * {@link ChargeCatalog} (desenho da Camada 0, secao 1; adendo de
 * desenho do municipio, D-02): devolve, por codigo de cobranca, a
 * definicao vencedora com a sua versao vigente na competencia,
 * deterministicamente ordenadas pelo codigo — e o retrato que a
 * camada seguinte (billing maestro) consome.
 *
 * <p>O resolvedor NAO carrega tenant: como nos records vizinhos
 * vigentes, o tenant e a fronteira do repositorio/RLS e o catalogo
 * embrulhado ja foi montado dentro de um tenant pelo chamador.
 *
 * <p>{@code unitRef} continua uma referencia OPACA a unidade
 * consumidora: esta camada nao a interpreta nem a valida contra
 * nenhum registro. O que a acompanha agora e o
 * {@code municipalityIbge} — o municipio da unidade, DERIVADO pelo
 * chamador da cadeia territorial (unidade &rarr; bloco &rarr; setor
 * comercial &rarr; localidade, {@code localities.municipality_ibge};
 * premissa PM-05 do adendo): o billing nunca o digita, recebe-o
 * derivado. {@code municipalityIbge} nulo significa "sem municipio
 * derivado" (cadeia territorial incompleta) e a resolucao usa so o
 * escopo "empresa" — o comportamento vigente, nao um erro.
 *
 * <p><b>Precedencia municipal sobre empresa (INV-C1-04-v2, D-02.2):</b>
 * para cada codigo, as candidatas sao a definicao de escopo "empresa"
 * ({@code municipalityIbge} nulo na definicao) e a definicao do
 * municipio da unidade. Havendo versao vigente da definicao municipal,
 * ELA vence e a de empresa e ignorada para esse codigo (coexistencia
 * dos dois escopos NAO e ambiguidade); sem versao municipal vigente,
 * resolve a de empresa (fallback). Definicoes de OUTROS municipios
 * sao excluidas: nem vencem, nem bloqueiam, nem entram no resultado.
 * A versao resolve-se por vigencia DENTRO da definicao vencedora.
 *
 * <p><b>Falha fechada em ambiguidade no mesmo escopo
 * (INV-C1-04-v2):</b> se a definicao vencedora de um codigo tiver DUAS
 * versoes vigentes na mesma competencia, o resolvedor lanca excecao —
 * nunca escolhe silenciosamente uma delas. Pelas recusas do
 * {@link ChargeCatalog} (sobreposicao recusada na montagem), essa
 * situacao e impossivel para dados que entraram pelos metodos
 * {@code add}; ela so pode surgir se os dados vierem de fora dessa
 * montagem (ex.: leitura directa do banco sem passar pelo catalogo) —
 * e e exactamente para esse caso que a guarda existe. Por isso o
 * resolvedor conta as versoes vigentes via
 * {@code definitions()}/{@code versionsOf} do catalogo em vez de
 * reutilizar o {@code findFirst} interno da resolucao por cobranca.
 *
 * <p><b>Substituicao de assinatura (reconciliacao pos-item 45):</b> a
 * forma anterior {@code resolve(String, LocalDate)} foi SUBSTITUIDA
 * por {@code resolve(String, String, LocalDate)} — nao mantida. A
 * camada ainda nao tem consumidor (o maestro e da Camada 2), logo a
 * substituicao nao quebra nenhum chamador vigente; fica registada no
 * relatorio da correcao do municipio.
 */
public final class ChargeResolver {

    private final ChargeCatalog catalog;

    public ChargeResolver(ChargeCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalogo e obrigatorio");
    }

    /**
     * Resolve as cobrancas vigentes para a unidade na competencia,
     * aplicando a precedencia municipal sobre empresa: lista imutavel
     * de (definicao vencedora, versao vigente), ordenada pelo codigo
     * da cobranca; vazia quando nenhuma cobranca esta vigente na
     * competencia para os escopos candidatos.
     *
     * @param unitRef          referencia opaca da unidade (nao
     *                         interpretada nesta camada)
     * @param municipalityIbge municipio da unidade, derivado pelo
     *                         chamador (IBGE de 7 digitos); {@code null}
     *                         = sem municipio derivado, so escopo
     *                         "empresa"
     * @param competence       competencia/data de referencia da resolucao
     * @throws IllegalStateException se a definicao vencedora de algum
     *         codigo tiver duas versoes vigentes na competencia
     *         (ambiguidade de vigencia no mesmo escopo, INV-C1-04-v2)
     */
    public List<ChargeResolution> resolve(String unitRef, String municipalityIbge, LocalDate competence) {
        Objects.requireNonNull(unitRef, "referencia da unidade e obrigatoria (opaca)");
        Objects.requireNonNull(competence, "competencia e obrigatoria");

        // Agrupa as definicoes ativas por codigo (definitions() ja vem
        // ordenada por codigo e escopo: a ordem final e deterministica).
        Map<String, List<ChargeDefinition>> byCode = new LinkedHashMap<>();
        for (ChargeDefinition definition : catalog.definitions()) {
            if (!definition.active()) {
                continue;
            }
            byCode.computeIfAbsent(definition.code(), k -> new ArrayList<>()).add(definition);
        }

        List<ChargeResolution> resolved = new ArrayList<>();
        for (List<ChargeDefinition> sameCode : byCode.values()) {
            ChargeDefinition company = null;
            ChargeDefinition municipal = null;
            for (ChargeDefinition definition : sameCode) {
                if (definition.municipalityIbge() == null) {
                    company = definition;
                } else if (definition.municipalityIbge().equals(municipalityIbge)) {
                    municipal = definition;
                }
                // Definicoes de outros municipios: excluidas (D-02.2).
            }
            // Precedencia: a municipal do municipio da unidade, com
            // versao vigente, vence; a de empresa e ignorada.
            ChargeResolution winner = null;
            if (municipal != null) {
                winner = resolveWinner(municipal, competence);
            }
            if (winner == null && company != null) {
                winner = resolveWinner(company, competence);
            }
            if (winner != null) {
                resolved.add(winner);
            }
        }
        return List.copyOf(resolved);
    }

    /**
     * Resolve a versao vigente DENTRO de uma definicao, falhando
     * fechado se houver duas vigentes (ambiguidade no mesmo escopo).
     * Devolve {@code null} quando nenhuma versao esta vigente — e o
     * sinal para o fallback ao escopo "empresa".
     */
    private ChargeResolution resolveWinner(ChargeDefinition definition, LocalDate competence) {
        List<ChargeVersion> applying = new ArrayList<>();
        for (ChargeVersion version : catalog.versionsOf(definition.id())) {
            if (version.appliesTo(competence)) {
                applying.add(version);
            }
        }
        if (applying.size() > 1) {
            throw new IllegalStateException(
                    "ambiguidade de vigencia: duas versoes vigentes para a cobranca "
                            + definition.code() + " na competencia " + competence);
        }
        return applying.size() == 1 ? new ChargeResolution(definition, applying.get(0)) : null;
    }
}
