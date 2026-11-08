package br.com.ziipsan.platform.tenant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P3.12 — política da sequência global de migrations de tenant (17:D1; COD-14).
 *
 * <p>Até a P3.12, cada módulo versionava na sua faixa (V1_x, V3_x, V6_x, ...)
 * e o {@code TenantMigrator} rodava com {@code outOfOrder(true)} (G10a). Desta
 * tarefa em diante:
 * <ul>
 *   <li>a sequência de tenant é <b>global e única</b>, a partir de
 *   {@code V100} (a maior versão de tenant publicada no corte é 27.1 —
 *   {@code V27_1} —, logo V100 já nasce acima de tudo o que existe);</li>
 *   <li>migration <b>nova</b> é qualquer arquivo versionado
 *   {@code V<versão>__*.sql} de {@code db/migration/tenant} que não esteja na
 *   lista de publicadas ({@code migration-policy/published-tenant-migrations.txt},
 *   gerada do repo no corte e atualizada só na integração, ver o cabeçalho
 *   dela);</li>
 *   <li>a CI <b>recusa</b> migration nova com versão menor ou igual à maior
 *   publicada, com versão abaixo de V100, com versão ilegível ou duplicando a
 *   versão de outra migration nova — ver
 *   {@code TenantMigrationGlobalSequenceCiTest}, que roda no
 *   {@code mvn verify} do {@code scripts/ci.sh}, e a réplica em script
 *   {@code scripts/migration/check-tenant-migration-versions.py}.</li>
 * </ul>
 *
 * <p>Classe pura, sem Flyway nem Spring: a mesma regra vale no teste de CI,
 * no script e em qualquer conferência manual. Versões são comparadas
 * segmento a segmento como inteiros ({@code 100.1 > 100}, {@code 6.5 < 15.1}),
 * que é a ordenação do Flyway para versões numéricas; segmentos que faltam
 * valem zero ({@code 100 == 100.0}).
 */
public final class TenantMigrationVersionPolicy {

    /** Primeiro major da sequência global (aceite da P3.12: "a partir de V100"). */
    public static final int FIRST_GLOBAL_MAJOR = 100;

    private static final Pattern VERSIONED_FILE =
            Pattern.compile("^V([0-9]+(?:_[0-9]+)*)__.*\\.sql$");
    private static final Pattern VERSIONED_FILE_LOOSE =
            Pattern.compile("^V([^_]*(?:_[^_]*)*)__.*\\.sql$");

    private TenantMigrationVersionPolicy() {
    }

    /** Versão Flyway de tenant como segmentos inteiros (ex.: "6_5" → [6, 5]). */
    public record MigrationVersion(List<Integer> segments) implements Comparable<MigrationVersion> {

        public MigrationVersion {
            segments = List.copyOf(segments);
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("versão vazia");
            }
        }

        public int major() {
            return segments.get(0);
        }

        @Override
        public int compareTo(MigrationVersion other) {
            int size = Math.max(segments.size(), other.segments.size());
            for (int i = 0; i < size; i++) {
                int a = i < segments.size() ? segments.get(i) : 0;
                int b = i < other.segments.size() ? other.segments.get(i) : 0;
                if (a != b) {
                    return Integer.compare(a, b);
                }
            }
            return 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append(segments.get(i));
            }
            return sb.toString();
        }
    }

    /**
     * Versão de um nome de arquivo {@code V<versão>__<descrição>.sql}, ou vazio
     * se o arquivo não é versionado (ex.: {@code R__*.sql}) ou a versão não é
     * numérica (ex.: o legado {@code V15_X__*.sql}, que só pode existir como
     * arquivo já publicado, nunca como migration nova).
     */
    public static Optional<MigrationVersion> versionOfFileName(String fileName) {
        Matcher matcher = VERSIONED_FILE.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        List<Integer> segments = new ArrayList<>();
        for (String part : matcher.group(1).split("_")) {
            segments.add(Integer.valueOf(part));
        }
        return Optional.of(new MigrationVersion(segments));
    }

    /** Verdadeiro para qualquer arquivo com cara de versionado ({@code V*__*.sql}), mesmo ilegível. */
    public static boolean looksVersioned(String fileName) {
        return VERSIONED_FILE_LOOSE.matcher(fileName).matches();
    }

    /** Maior versão entre os arquivos dados; falha se nenhum tiver versão numérica. */
    public static MigrationVersion maxVersion(Collection<String> fileNames) {
        MigrationVersion max = null;
        for (String fileName : fileNames) {
            Optional<MigrationVersion> version = versionOfFileName(fileName);
            if (version.isPresent() && (max == null || version.get().compareTo(max) > 0)) {
                max = version.get();
            }
        }
        if (max == null) {
            throw new IllegalArgumentException("nenhuma migration versionada numérica na lista");
        }
        return max;
    }

    /**
     * Confere UMA migration nova contra a lista de publicadas. Lança
     * {@link IllegalArgumentException} com o motivo da recusa — é a recusa que
     * a CI materializa ao falhar o teste.
     */
    public static void requireNewMigrationAllowed(String fileName,
                                                    Set<String> publishedFileNames,
                                                    MigrationVersion maxPublished) {
        List<String> violations = checkNewMigration(fileName, publishedFileNames, maxPublished, List.of());
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", violations));
        }
    }

    /**
     * Violações de um arquivo contra a política (lista vazia = permitido).
     * Arquivo já publicado (nome na lista) nunca é violação: é história, e a
     * história inclui as faixas e até o {@code V15_X} legado.
     */
    public static List<String> checkNewMigration(String fileName,
                                                 Set<String> publishedFileNames,
                                                 MigrationVersion maxPublished,
                                                 Collection<String> otherNewFileNames) {
        List<String> violations = new ArrayList<>();
        if (!looksVersioned(fileName)) {
            return violations;
        }
        if (publishedFileNames.contains(fileName)) {
            return violations;
        }
        Optional<MigrationVersion> version = versionOfFileName(fileName);
        if (version.isEmpty()) {
            violations.add(fileName + ": versão ilegível — migration nova precisa de versão "
                    + "numérica na sequência global (a partir de V" + FIRST_GLOBAL_MAJOR + ")");
            return violations;
        }
        if (version.get().major() < FIRST_GLOBAL_MAJOR) {
            violations.add(fileName + ": versão " + version.get() + " abaixo de V" + FIRST_GLOBAL_MAJOR
                    + " — as faixas de módulo acabaram na P3.12; migration nova entra na "
                    + "sequência global a partir de V" + FIRST_GLOBAL_MAJOR);
        }
        if (version.get().compareTo(maxPublished) <= 0) {
            violations.add(fileName + ": versão " + version.get() + " menor ou igual à maior "
                    + "publicada (" + maxPublished + ") — a CI recusa reutilizar ou "
                    + "ficar abaixo da maior versão já publicada");
        }
        for (String other : otherNewFileNames) {
            if (!other.equals(fileName) && !publishedFileNames.contains(other)
                    && versionOfFileName(other).equals(version)) {
                violations.add(fileName + ": versão " + version.get() + " duplicada com a "
                        + "migration nova " + other + " — a sequência global é única");
            }
        }
        return violations;
    }

    /**
     * Violações de um conjunto inteiro de arquivos de tenant (o que a CI varre
     * no repo) contra a lista de publicadas.
     */
    public static List<String> checkAll(Collection<String> allFileNames, Set<String> publishedFileNames) {
        if (publishedFileNames.isEmpty()) {
            return List.of("lista de migrations publicadas vazia — sem ela a política da P3.12 não tem base");
        }
        MigrationVersion maxPublished = maxVersion(publishedFileNames);
        List<String> newFiles = allFileNames.stream()
                .filter(TenantMigrationVersionPolicy::looksVersioned)
                .filter(name -> !publishedFileNames.contains(name))
                .toList();
        List<String> violations = new ArrayList<>();
        for (String newFile : newFiles) {
            violations.addAll(checkNewMigration(newFile, publishedFileNames, maxPublished, newFiles));
        }
        return violations;
    }
}
