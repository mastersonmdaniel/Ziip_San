package br.com.ziipsan.commercial.billing.social.cycle;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * II-3: loja do ciclo da tarifa social - o contrato de persistencia do
 * nucleo, com MINIMIZACAO LGPD POR DESENHO declarada:
 * <ul>
 *   <li>persistem SO as chaves (indices cegos da familia) e o RESULTADO
 *       do cruzamento (casou com qual unidade, ou nao casou, ou
 *       inelegivel), a concessao com a sua trilha e a lista de busca
 *       activa derivada;</li>
 *   <li>a lista importada NAO e persistida como base paralela: as linhas
 *       da importacao (indices cegos das pessoas incluidos) vivem so no
 *       resultado transitorio do ciclo; o resumo por familia abaixo nem
 *       campo para indice cego de pessoa tem;</li>
 *   <li>documento, nome e renda em claro nao existem em nenhum tipo
 *       deste pacote, logo nao podem ser persistidos por esta loja.</li>
 * </ul>
 * A implementacao de prova e em memoria
 * ({@link InMemorySocialCycleStore}); a persistencia real liga a loja
 * as tabelas do SQL pendente do pacote (db/pendente/II-3) na integracao,
 * reusando {@code social_tariff_enrollments} e {@code social_base_*}
 * da P1.5/V4_2 para o que ja la vive.
 */
public interface SocialCycleStore {

    /** Resumo persistido de uma familia cruzada: chave + resultado, nada mais. */
    record FamilyCrossingSummary(String familyCodeHash, String basis, Outcome outcome, UUID matchedUnitId,
                                 String municipalityCode, String routeCode, int personCount) {
        public enum Outcome {
            MATCHED, UNMATCHED, INELIGIBLE
        }
    }

    /** Grava o resultado do cruzamento, familia a familia (chaves + resultado). */
    void saveCrossing(SocialCrossingEngine.CrossingResult crossing);

    /** Os resumos de cruzamento persistidos, na ordem de gravacao. */
    List<FamilyCrossingSummary> crossingSummaries();

    /** Grava ou actualiza uma concessao (e a sua trilha). */
    void saveGrant(SocialGrantLifecycle.Grant grant);

    /** A concessao pelo id, se existir. */
    Optional<SocialGrantLifecycle.Grant> findGrant(UUID grantId);

    /** Familias com concessao nao cessada (o conjunto dos "ja inscritos" da busca activa). */
    Set<String> enrolledFamilyHashes();

    /** Substitui a lista de busca activa vigente pela calculada neste ciclo. */
    void saveActiveSearch(List<SocialActiveSearch.ActiveSearchEntry> entries);

    /** A lista de busca activa vigente. */
    List<SocialActiveSearch.ActiveSearchEntry> activeSearch();

    /** A trilha de uma concessao (entradas sem indice cego, ver SocialGrantLifecycle). */
    List<SocialGrantLifecycle.TrailEntry> trailOf(UUID grantId);
}
