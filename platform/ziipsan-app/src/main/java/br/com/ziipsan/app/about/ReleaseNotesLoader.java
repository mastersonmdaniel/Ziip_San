package br.com.ziipsan.app.about;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * P1.24: novidades publicadas, lidas do pacote gerado da P0.41.
 *
 * <p>Fonte: {@code content/release-notes/<version>.yaml} (um arquivo por
 * versão lançada, validado pelo CI da P0.41), embalado como
 * {@code release-notes/release-notes.json} no classpath pelo script
 * {@code scripts/release-notes/package-release-notes.py}. O loader NÃO lê
 * YAML em runtime e NÃO inventa versão: sem arquivo, sem notas; arquivo
 * ausente (imagem antiga, antes da primeira release) é lista vazia, não
 * erro — o estado "visto" é que vive no servidor, na tabela
 * {@code support.release_note_view}.
 *
 * <p>Validação espelha a da P0.41 (schema.json): versão SemVer X.Y.Z, data
 * YYYY-MM-DD, entries não vazio com título não vazio. Arquivo malformado
 * derruba o boot (como pacote de marca ausente, P0.34): nota publicada
 * errada não pode aparecer pela metade.
 */
public class ReleaseNotesLoader {

    private static final Pattern VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");
    private static final Pattern DATE = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");

    public record Entry(String type, String title, List<String> commits, String issue) {
    }

    public record ReleaseNote(String version, String date, List<Entry> entries) {
    }

    private final String resource;
    private final List<ReleaseNote> notes;

    public ReleaseNotesLoader() {
        this("release-notes/release-notes.json");
    }

    ReleaseNotesLoader(String resource) {
        this.resource = resource;
        this.notes = load();
    }

    /** Notas ordenadas da mais recente para a mais antiga (SemVer). */
    public List<ReleaseNote> notes() {
        return notes;
    }

    /** A nota da versão, se publicada. */
    public java.util.Optional<ReleaseNote> note(String version) {
        if (version == null) {
            return java.util.Optional.empty();
        }
        String wanted = version.strip();
        return notes.stream().filter(n -> n.version().equals(wanted)).findFirst();
    }

    private List<ReleaseNote> load() {
        JsonMapper mapper = JsonMapper.builder().build();
        List<ReleaseNote> out = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                // Imagem sem o pacote (antes da primeira release embalada):
                // zero notas, não erro. O CI da P0.41 continua exigindo o
                // arquivo por versão no momento da release.
                return List.of();
            }
            JsonNode root = mapper.readTree(in);
            if (root == null || !root.isArray()) {
                throw new IllegalStateException("release-notes embalado inválido (esperado array): " + resource);
            }
            for (JsonNode node : root) {
                String version = text(node, "version");
                String date = text(node, "date");
                if (!VERSION.matcher(version).matches()) {
                    throw new IllegalStateException("release-notes: versão inválida: " + version);
                }
                if (!DATE.matcher(date).matches()) {
                    throw new IllegalStateException("release-notes: data inválida em " + version + ": " + date);
                }
                JsonNode entriesNode = node.get("entries");
                if (entriesNode == null || !entriesNode.isArray() || entriesNode.isEmpty()) {
                    throw new IllegalStateException("release-notes: entries vazio em " + version);
                }
                List<Entry> entries = new ArrayList<>();
                for (JsonNode entry : entriesNode) {
                    String title = text(entry, "title");
                    if (title.isBlank()) {
                        throw new IllegalStateException("release-notes: título vazio em " + version);
                    }
                    List<String> commits = new ArrayList<>();
                    JsonNode commitsNode = entry.get("commits");
                    if (commitsNode != null && commitsNode.isArray()) {
                        commitsNode.forEach(c -> commits.add(c.asString()));
                    }
                    entries.add(new Entry(text(entry, "type"), title, List.copyOf(commits),
                            entry.hasNonNull("issue") ? entry.get("issue").asString() : null));
                }
                out.add(new ReleaseNote(version, date, List.copyOf(entries)));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Pacote de release-notes inválido: " + resource, e);
        }
        out.sort((a, b) -> compareSemver(b.version(), a.version()));
        return List.copyOf(out);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private static List<Integer> semver(String version) {
        String[] parts = version.split("\\.");
        List<Integer> out = new ArrayList<>();
        for (String part : parts) {
            out.add(Integer.parseInt(part));
        }
        return out;
    }

    /** Compara duas versões SemVer parte a parte (List não é Comparable). */
    private static int compareSemver(String left, String right) {
        List<Integer> a = semver(left);
        List<Integer> b = semver(right);
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int cmp = Integer.compare(a.get(i), b.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}
