package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialEligibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * II-3: BUSCA ACTIVA - a lista dos elegiveis ainda nao inscritos, por
 * municipio e rota.
 *
 * <p>Definicao exacta (a do teste de aceite do pacote): busca activa =
 * familias elegiveis do cruzamento MENOS as familias ja inscritas
 * (com concessao activa). Entram as duas situacoes que pedem accao do
 * prestador:
 * <ul>
 *   <li>{@code MATCHED_NOT_ENROLLED}: a familia casou com uma unidade
 *       mas nao tem concessao (a concessao sem pedido falhou neste
 *       ciclo, ex.: vigencia ou data legal, ou o ciclo correu so em
 *       modo de cruzamento) - municipio e rota sao os da unidade;</li>
 *   <li>{@code NO_UNIT_MATCH}: a familia elegivel da lista nao tem
 *       unidade correspondente na base (o prestador procura a ligacao:
 *       morador sem cadastro, ligacao em nome de terceiro, etc.) -
 *       municipio e o declarado na lista e a rota e o balde
 *       {@code SEM_ROTA}, porque sem unidade nao ha rota.</li>
 * </ul>
 * Familias inelegiveis NAO entram (nao sao elegiveis) e familias ja
 * inscritas NAO entram (ja recebem): o conjunto e exacto por construcao.
 *
 * <p>Rotulos de balde {@code SEM_MUNICIPIO}/{@code SEM_ROTA} sao
 * rotulos de agrupamento deste relatorio, nao dados: nascem quando o
 * valor nao existe, para nenhuma familia elegivel desaparecer da lista
 * por falta de municipio ou rota.
 */
public final class SocialActiveSearch {

    public static final String SEM_MUNICIPIO = "SEM_MUNICIPIO";
    public static final String SEM_ROTA = "SEM_ROTA";

    public enum Reason {
        MATCHED_NOT_ENROLLED, NO_UNIT_MATCH
    }

    /** Uma familia a procurar activamente, com o seu contexto de municipio/rota. */
    public record ActiveSearchEntry(String familyCodeHash, SocialEligibility.Basis basis,
                                    String municipalityCode, String routeCode, Reason reason) {
    }

    /** Chave de agrupamento do relatorio de busca activa. */
    public record GroupKey(String municipalityCode, String routeCode) {
    }

    private SocialActiveSearch() {
    }

    /**
     * Calcula a busca activa de um cruzamento: elegiveis menos inscritos.
     *
     * @param enrolledFamilyHashes familias com concessao activa (deste
     *                             ciclo e/ou ja persistidas na loja)
     */
    public static List<ActiveSearchEntry> compute(SocialCrossingEngine.CrossingResult crossing,
                                                  Set<String> enrolledFamilyHashes) {
        if (crossing == null || enrolledFamilyHashes == null) {
            throw new IllegalArgumentException("Cruzamento e conjunto de inscritos sao obrigatorios");
        }
        List<ActiveSearchEntry> entries = new ArrayList<>();
        for (SocialCrossingEngine.CrossedFamily family : crossing.matched()) {
            if (!enrolledFamilyHashes.contains(family.familyCodeHash())) {
                entries.add(new ActiveSearchEntry(family.familyCodeHash(), family.basis(),
                        bucket(family.municipalityCode(), SEM_MUNICIPIO), bucket(family.routeCode(), SEM_ROTA),
                        Reason.MATCHED_NOT_ENROLLED));
            }
        }
        for (SocialCrossingEngine.UnmatchedFamily family : crossing.unmatched()) {
            if (!enrolledFamilyHashes.contains(family.familyCodeHash())) {
                entries.add(new ActiveSearchEntry(family.familyCodeHash(), family.basis(),
                        bucket(family.municipalityCode(), SEM_MUNICIPIO), SEM_ROTA,
                        Reason.NO_UNIT_MATCH));
            }
        }
        entries.sort(Comparator.comparing(ActiveSearchEntry::familyCodeHash));
        return List.copyOf(entries);
    }

    /** Agrupa por municipio e rota, chaves ordenadas, entradas ordenadas por familia. */
    public static Map<GroupKey, List<ActiveSearchEntry>> groupByMunicipalityAndRoute(
            List<ActiveSearchEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("Lista de busca activa e obrigatoria");
        }
        Map<GroupKey, List<ActiveSearchEntry>> grouped = new LinkedHashMap<>();
        List<ActiveSearchEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(ActiveSearchEntry::municipalityCode)
                .thenComparing(ActiveSearchEntry::routeCode)
                .thenComparing(ActiveSearchEntry::familyCodeHash));
        for (ActiveSearchEntry entry : sorted) {
            grouped.computeIfAbsent(new GroupKey(entry.municipalityCode(), entry.routeCode()),
                    k -> new ArrayList<>()).add(entry);
        }
        Map<GroupKey, List<ActiveSearchEntry>> result = new LinkedHashMap<>();
        grouped.forEach((k, v) -> result.put(k, List.copyOf(v)));
        return result;
    }

    private static String bucket(String value, String whenMissing) {
        return value == null || value.isBlank() ? whenMissing : value;
    }
}
