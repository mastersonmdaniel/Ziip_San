package br.com.ziipsan.platform.implantation;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exportação e importação de parâmetros ACEITOS entre homologação e produção
 * (ED-09).
 *
 * <p>A fonte é a {@code parameter_versions} da P0.19 (core {@code V3} +
 * tenant {@code V1_5}): versão com vigência, quem propõe ≠ quem aprova e
 * aceite registrado em {@code parameter_acceptances}. O pacote carrega SÓ
 * versões aceitas — versão proposta, em aprovação ou recusada não atravessa
 * ambiente, porque o aceite é do ambiente de origem e a produção não herda
 * rascunho.
 *
 * <p>Formato do pacote: um objeto JSON montado e lido EXCLUSIVAMENTE pela
 * classe {@code Json} do core (nunca JSON na mão), com o hash SHA-256 do
 * conteúdo canônico das entradas. A importação confere, nesta ordem:
 * <ol>
 *   <li>o hash do pacote (adulteração recusa tudo);</li>
 *   <li>cada entrada: chave presente, valor JSON válido, vigência não
 *       invertida, proponente ≠ aprovador (a separação da P0.19 não se perde
 *       na travessia) e marca de aceite presente;</li>
 *   <li>vigências sobrepostas da MESMA chave dentro do pacote recusam o
 *       pacote (o {@code EXCLUDE} da {@code V1_5} recusaria na gravação; aqui
 *       a recusa é prévia e nomeada).</li>
 * </ol>
 * A importação NÃO grava: devolve as entradas validadas para o serviço do
 * console gravar como versões aceitas do ambiente de destino, com a linhagem
 * do pacote (origem e hash) registrada por quem grava.
 */
public final class ParameterPackage {

    private ParameterPackage() {
    }

    /** Uma versão aceita de parâmetro, como viaja no pacote. */
    public record Entry(String definitionKey, String valueJson, LocalDate validFrom,
                        LocalDate validTo, String proposedBy, String approvedBy,
                        boolean accepted) {
        public Entry {
            if (definitionKey == null || definitionKey.isBlank()) {
                throw new IllegalArgumentException("A chave do parâmetro é obrigatória.");
            }
            if (valueJson == null || valueJson.isBlank()) {
                throw new IllegalArgumentException(
                        "O valor do parâmetro é obrigatório: " + definitionKey);
            }
            // Valida que é JSON de verdade, pela classe do core. A Json do core
            // sinaliza JSON inválido com IllegalStateException; na borda desta
            // classe a recusa é IllegalArgumentException nomeada, como as demais.
            try {
                Json.readTree(valueJson);
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException(
                        "O valor do parâmetro não é JSON válido: " + definitionKey, e);
            }
            if (proposedBy == null || proposedBy.isBlank()
                    || approvedBy == null || approvedBy.isBlank()) {
                throw new IllegalArgumentException(
                        "Proponente e aprovador são obrigatórios: " + definitionKey);
            }
            if (proposedBy.equals(approvedBy)) {
                throw new IllegalArgumentException(
                        "Quem propõe não pode ser quem aprova (P0.19): " + definitionKey);
            }
            if (validFrom == null) {
                throw new IllegalArgumentException(
                        "O início da vigência é obrigatório: " + definitionKey);
            }
            if (validTo != null && validTo.isBefore(validFrom)) {
                throw new IllegalArgumentException(
                        "Vigência invertida do parâmetro: " + definitionKey);
            }
        }
    }

    public record Package(String sourceEnvironment, List<Entry> entries, String contentHash) {
    }

    /** Exporta: só entradas aceitas entram; o hash cobre o conteúdo canônico. */
    public static Package export(String sourceEnvironment, List<Entry> versions) {
        if (sourceEnvironment == null || sourceEnvironment.isBlank()) {
            throw new IllegalArgumentException("O ambiente de origem é obrigatório.");
        }
        if (versions == null) {
            throw new IllegalArgumentException("A lista de versões é obrigatória.");
        }
        List<Entry> accepted = new ArrayList<>();
        for (Entry e : versions) {
            if (e.accepted()) {
                accepted.add(e);
            }
        }
        // Fail-fast na origem: a mesma sobreposição que a importação recusa
        // não pode nascer na exportação em silêncio.
        requireNoOverlap(accepted);
        String hash = hashOf(accepted);
        return new Package(sourceEnvironment, List.copyOf(accepted), hash);
    }

    /** Serializa o pacote (JSON só pela classe {@code Json} do core). */
    public static String toJson(Package pkg) {
        if (pkg == null) {
            throw new IllegalArgumentException("O pacote é obrigatório.");
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sourceEnvironment", pkg.sourceEnvironment());
        root.put("contentHash", pkg.contentHash());
        List<Object> entries = new ArrayList<>();
        for (Entry e : pkg.entries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("definitionKey", e.definitionKey());
            // O valor viaja como árvore JSON, não como texto escapado à mão.
            m.put("value", Json.readTree(e.valueJson()));
            m.put("validFrom", e.validFrom().toString());
            m.put("validTo", e.validTo() == null ? null : e.validTo().toString());
            m.put("proposedBy", e.proposedBy());
            m.put("approvedBy", e.approvedBy());
            m.put("accepted", e.accepted());
            entries.add(m);
        }
        root.put("entries", entries);
        return Json.write(root);
    }

    /**
     * Importa e valida. Qualquer recusa é {@link IllegalArgumentException}
     * nomeada; pacote válido devolve as entradas na ordem do arquivo.
     */
    public static List<Entry> importPackage(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("O pacote de parâmetros está vazio.");
        }
        JsonNode root = Json.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("O pacote de parâmetros precisa ser um objeto JSON.");
        }
        JsonNode entriesNode = root.path("entries");
        if (!entriesNode.isArray()) {
            throw new IllegalArgumentException("O pacote de parâmetros não tem a lista entries.");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode n : entriesNode) {
            String key = text(n, "definitionKey");
            JsonNode value = n.path("value");
            if (value.isMissingNode()) {
                throw new IllegalArgumentException("Entrada sem valor no pacote: " + key);
            }
            String valueJson = Json.write(value);
            LocalDate from = LocalDate.parse(text(n, "validFrom"));
            String toText = optionalText(n, "validTo");
            LocalDate to = toText == null ? null : LocalDate.parse(toText);
            String proposedBy = text(n, "proposedBy");
            String approvedBy = text(n, "approvedBy");
            boolean accepted = n.path("accepted").asBoolean(false);
            if (!accepted) {
                throw new IllegalArgumentException(
                        "O pacote só pode conter parâmetros aceitos; entrada não aceita: " + key);
            }
            if (proposedBy.equals(approvedBy)) {
                throw new IllegalArgumentException(
                        "Quem propõe não pode ser quem aprova (P0.19): " + key);
            }
            entries.add(new Entry(key, valueJson, from, to, proposedBy, approvedBy, true));
        }
        String declaredHash = optionalText(root, "contentHash");
        if (declaredHash == null || !declaredHash.equals(hashOf(entries))) {
            throw new IllegalArgumentException(
                    "O hash do pacote não confere: o conteúdo foi alterado depois da exportação.");
        }
        requireNoOverlap(entries);
        return List.copyOf(entries);
    }

    private static void requireNoOverlap(List<Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Entry a = entries.get(i);
                Entry b = entries.get(j);
                if (!a.definitionKey().equals(b.definitionKey())) {
                    continue;
                }
                LocalDate aTo = a.validTo() == null ? LocalDate.MAX : a.validTo();
                LocalDate bTo = b.validTo() == null ? LocalDate.MAX : b.validTo();
                if (!a.validFrom().isAfter(bTo) && !b.validFrom().isAfter(aTo)) {
                    throw new IllegalArgumentException(
                            "Vigências sobrepostas da mesma chave dentro do pacote: "
                                    + a.definitionKey());
                }
            }
        }
    }

    /** Hash canônico: chaves ordenadas, uma linha por entrada, SHA-256 hex. */
    static String hashOf(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> {
            int c = a.definitionKey().compareTo(b.definitionKey());
            return c != 0 ? c : a.validFrom().compareTo(b.validFrom());
        });
        StringBuilder canonical = new StringBuilder();
        for (Entry e : sorted) {
            canonical.append(e.definitionKey()).append('|')
                    .append(Json.write(Json.readTree(e.valueJson()))).append('|')
                    .append(e.validFrom()).append('|')
                    .append(e.validTo() == null ? "" : e.validTo().toString()).append('|')
                    .append(e.proposedBy()).append('|').append(e.approvedBy()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM.", e);
        }
    }

    private static String text(JsonNode node, String field) {
        String v = optionalText(node, field);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no pacote: " + field);
        }
        return v;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        return v.asText();
    }
}
