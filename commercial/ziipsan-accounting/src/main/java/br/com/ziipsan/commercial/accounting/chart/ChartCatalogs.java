package br.com.ziipsan.commercial.accounting.chart;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Catalogos globais versionados (ED-32):
 * - MCRSB (NR 16, Res. ANA 302/2026): dado global versionado;
 * - PCASP versionado por exercicio: a partir de 2027 vale o PCASP 2027
 *   (Portaria STN/MF 2.520/2026); exercicios anteriores usam a versao registrada
 *   para o exercicio (nenhuma portaria anterior e inventada aqui).
 * De-para MCRSB -> plano do tenant (PCASP p/ autarquia ou societario) e
 * configuracao com vigencia no grupo acc.*.
 *
 * Correcoes pos-revisao:
 * - (M2) a resolucao FILTRA por TenantChartKind: de-para PCASP e societario
 *   para a mesma conta regulatoria nao colidem mais (antes vencia o primeiro
 *   da lista); cada Mapping registra a versao do catalogo do tenant a que
 *   pertence (catalogVersion).
 * - (M3) mais de um de-para vigente para a mesma chave (conta + tipo +
 *   exercicio + data) e ambiguidade de configuracao e e recusado, em vez de
 *   resolver silenciosamente pela ordem da lista.
 * - (L2) codigos em branco sao recusados (antes so nulo era recusado).
 */
public final class ChartCatalogs {

    public static final String MCRSB_VERSION = "MCRSB NR 16 - Res. ANA 302/2026";
    public static final String PCASP_2027_VERSION = "PCASP 2027 - Portaria STN/MF 2.520/2026";
    public static final int PCASP_2027_FIRST_EXERCISE = 2027;

    public enum TenantChartKind { PCASP, SOCIETARIO }

    public record Mapping(String regulatoryAccountCode, String tenantAccountCode, TenantChartKind tenantKind,
                          String catalogVersion,
                          int exerciseFrom, Integer exerciseTo, LocalDate validFrom, LocalDate validTo) {
        public Mapping {
            if (regulatoryAccountCode == null || regulatoryAccountCode.isBlank()) throw new IllegalArgumentException("conta regulatoria (MCRSB) e obrigatoria");
            if (tenantAccountCode == null || tenantAccountCode.isBlank()) throw new IllegalArgumentException("conta do tenant e obrigatoria");
            Objects.requireNonNull(tenantKind, "tipo do plano do tenant e obrigatorio");
            if (catalogVersion == null || catalogVersion.isBlank()) throw new IllegalArgumentException("versao do catalogo do de-para e obrigatoria");
            if (exerciseTo != null && exerciseTo < exerciseFrom) throw new IllegalArgumentException("exercicio do de-para invalido");
            if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia do de-para invalida");
        }
        boolean applies(int exercise, LocalDate date) {
            if (exercise < exerciseFrom || (exerciseTo != null && exercise > exerciseTo)) return false;
            if (validFrom != null && date.isBefore(validFrom)) return false;
            return validTo == null || !date.isAfter(validTo);
        }
    }

    private ChartCatalogs() {}

    /** Virada de exercicio: 2026 -> versao anterior registrada; 2027+ -> PCASP 2027. */
    public static String pcaspVersionForExercise(int exercise) {
        return exercise >= PCASP_2027_FIRST_EXERCISE ? PCASP_2027_VERSION : "PCASP exercicio " + exercise + " (versao registrada do exercicio)";
    }

    public static Mapping resolve(List<Mapping> mappings, String regulatoryAccountCode, TenantChartKind tenantKind, int exercise, LocalDate date) {
        Objects.requireNonNull(mappings, "de-para (acc.*) e obrigatorio");
        Objects.requireNonNull(tenantKind, "tipo do plano do tenant e obrigatorio");
        List<Mapping> matches = mappings.stream()
                .filter(m -> m.regulatoryAccountCode().equals(regulatoryAccountCode) && m.tenantKind() == tenantKind && m.applies(exercise, date))
                .toList();
        if (matches.isEmpty())
            throw new IllegalStateException("sem de-para vigente para a conta MCRSB " + regulatoryAccountCode + " (" + tenantKind + ") no exercicio " + exercise);
        if (matches.size() > 1)
            throw new IllegalStateException("de-para ambiguo (vigencias sobrepostas) para a conta MCRSB " + regulatoryAccountCode + " (" + tenantKind + ") no exercicio " + exercise + ": " + matches.size() + " mappings");
        return matches.get(0);
    }
}
