package br.com.ziipsan.platform.central.complete;

import br.com.ziipsan.core.json.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P3.15: kit de evidências da Central com hash registrado na trilha.
 *
 * <p>O kit é um manifesto JSON canônico — montado SOMENTE pela classe
 * {@code Json} do core (regra do projeto) a partir de um mapa de
 * inserção ordenada, com os itens ordenados por nome — cujo SHA-256 é
 * a identidade do kit. O registro na trilha é o {@link TrailEntry}:
 * ação, hash, ator e instante, no formato do {@code audit_log} do core
 * (a gravação em si é a fiação da integração; o que esta classe garante
 * é que o hash registrado é o hash do manifesto exato, recomputável).
 *
 * <p>Item sem hash ou com hash que não seja SHA-256 hex de 64 dígitos
 * é recusado: um kit cuja evidência não pode ser conferida não é kit.
 */
public final class EvidenceKit {

    /** Um artefato do kit: nome e o SHA-256 (hex, 64) do seu conteúdo. */
    public record EvidenceItem(String name, String contentSha256) {
        public EvidenceItem {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Nome do item de evidência é obrigatório");
            }
            if (contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Item " + name + " sem SHA-256 válido (hex minúsculo, 64 dígitos): " + contentSha256);
            }
        }
    }

    /** Registro do kit na trilha de auditoria (hash = identidade do kit). */
    public record TrailEntry(String action, String kitSha256, String actor, Instant at, String reference) {
        public TrailEntry {
            if (!"CENTRAL_EVIDENCE_KIT_GENERATED".equals(action)) {
                throw new IllegalArgumentException("Ação de trilha do kit deve ser CENTRAL_EVIDENCE_KIT_GENERATED: " + action);
            }
            // b2: o hash na trilha passa pelo MESMO rigor do item de
            // evidência (SHA-256 hex minúsculo de 64 dígitos) — um hash
            // qualquer na trilha não identifica kit nenhum.
            if (kitSha256 == null || !kitSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Hash do kit na trilha sem SHA-256 válido (hex minúsculo, 64 dígitos): " + kitSha256);
            }
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("Ator é obrigatório no registro em trilha do kit");
            }
            Objects.requireNonNull(at, "instante é obrigatório no registro em trilha do kit");
        }
    }

    public record Kit(String reference, List<EvidenceItem> items, String manifestJson, String kitSha256) {
    }

    private EvidenceKit() {
    }

    /** SHA-256 hex de um conteúdo (usado nos itens e no manifesto). */
    public static String sha256Hex(byte[] content) {
        Objects.requireNonNull(content, "conteúdo é obrigatório para o hash");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /**
     * Monta o kit da referência (ex.: {@code 2026-11}): itens ordenados
     * por nome, manifesto via {@code Json} do core e o hash do kit.
     * Kit vazio é erro explícito — evidência vazia com hash válido
     * fingiria uma conferência que não houve.
     */
    public static Kit build(String reference, List<EvidenceItem> items) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Referência do kit é obrigatória");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Kit de evidências vazio é recusado (referência " + reference + ")");
        }
        List<EvidenceItem> sorted = items.stream().sorted(java.util.Comparator.comparing(EvidenceItem::name)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).name().equals(sorted.get(i - 1).name())) {
                throw new IllegalArgumentException("Item duplicado no kit: " + sorted.get(i).name());
            }
        }
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (EvidenceItem item : sorted) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", item.name());
            m.put("sha256", item.contentSha256());
            itemMaps.add(m);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("kit", "central-evidence");
        manifest.put("reference", reference);
        manifest.put("items", itemMaps);
        String manifestJson = Json.write(manifest);
        return new Kit(reference, sorted, manifestJson, sha256Hex(manifestJson.getBytes(StandardCharsets.UTF_8)));
    }

    /** O registro que a fiação grava na trilha ({@code audit_log}) ao gerar o kit. */
    public static TrailEntry trailEntry(Kit kit, String actor, Instant at) {
        Objects.requireNonNull(kit, "kit é obrigatório");
        return new TrailEntry("CENTRAL_EVIDENCE_KIT_GENERATED", kit.kitSha256(), actor, at, kit.reference());
    }

    /** Reconferência: o hash registrado bate com o manifesto? */
    public static boolean verify(Kit kit) {
        Objects.requireNonNull(kit, "kit é obrigatório");
        return kit.kitSha256().equals(sha256Hex(kit.manifestJson().getBytes(StandardCharsets.UTF_8)));
    }
}
