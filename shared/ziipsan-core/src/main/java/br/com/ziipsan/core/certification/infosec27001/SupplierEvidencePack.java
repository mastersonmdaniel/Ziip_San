package br.com.ziipsan.core.certification.infosec27001;

import br.com.ziipsan.core.json.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pacote de evidência DO FORNECEDOR (ED-73): a montagem, por REFERÊNCIA,
 * dos artefatos que o aceite nomeia — a linha de base de segurança da
 * P3.8 (ASVS 5.0, API Security Top 10 e LLM Top 10; PLT-08), a exportação
 * verificável da trilha de auditoria da P1.7 (PLT-19), os documentos da
 * P3.9 e os incidentes da P3.17 — para acompanhar a marcação de controle
 * implementado pelo fornecedor na matriz de responsabilidade
 * ({@link SharedResponsibilityEntry}).
 *
 * <p>Nada aqui copia conteúdo de artefato: cada item é tipo + referência +
 * resumo criptográfico (SHA-256 em hexadecimal, declarado por quem monta
 * a partir do artefato real). O manifesto exportado carrega o hash do
 * próprio conteúdo canônico, de modo que quem recebe pode VERIFICAR que o
 * manifesto não mudou ({@link #verifyManifestHash(String, String)}).
 * Artefato que não existe não entra: item sem referência ou sem hash é
 * recusado, e a ausência de um tipo exigido aparece em
 * {@link #missingKinds()} — o pacote se declara incompleto em vez de
 * fingir completude.
 *
 * <p>Os 14 valores abertos da P3.9 NÃO são copiados nem inventados: os
 * documentos da P3.9 entram só como referência ao documento (a minuta),
 * nunca como valor de cláusula.
 *
 * <p>JSON somente pela classe {@link Json} do core (regra do projeto).
 *
 * <p><b>Correção D1 da revisão cruzada da ED-73:</b> a ativação é
 * componente do registro e o construtor canônico a exige ligada, e a
 * não-vacuidade do pacote (antes só na fábrica {@link #assemble}) vale
 * também no construtor canônico: pacote vazio não nasce por caminho
 * algum deste pacote.
 */
public record SupplierEvidencePack(Iso27001Activation activation, List<PackItem> items) {

    /** Origem do artefato referenciado: exatamente as fontes nomeadas no aceite. */
    public enum ArtifactKind {
        /** Linha de base de segurança da P3.8 (ASVS 5.0, API Security Top 10, LLM Top 10; PLT-08). */
        SECURITY_BASELINE_P38,
        /** Exportação verificável da trilha de auditoria da P1.7 (PLT-19). */
        AUDIT_TRAIL_EXPORT_P17,
        /** Documento da P3.9 (minutas), por referência — sem copiar valores de cláusula. */
        CONTRACT_DOCUMENT_P39,
        /** Incidente de segurança da P3.17, por referência. */
        SECURITY_INCIDENT_P317
    }

    /** Um artefato do pacote: tipo + referência + SHA-256 declarado do artefato. */
    public record PackItem(ArtifactKind kind, String ref, String sha256Hex) {
        public PackItem {
            Objects.requireNonNull(kind, "kind do artefato é obrigatório");
            if (ref == null || ref.isBlank() || ref.trim().length() > AnnexAControlEntry.EVIDENCE_REF_MAX) {
                throw new IllegalArgumentException("ref do artefato é obrigatória (identificador "
                        + "do artefato existente, sem copiar conteúdo) e tem no máximo "
                        + AnnexAControlEntry.EVIDENCE_REF_MAX + " caracteres");
            }
            ref = ref.trim();
            if (sha256Hex == null || !sha256Hex.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("sha256Hex deve ser o SHA-256 do artefato em "
                        + "hexadecimal minúsculo (64 caracteres): pacote de evidência sem resumo "
                        + "verificável não é evidência");
            }
        }
    }

    /** Tipos exigidos para o pacote se declarar completo (os quatro do aceite). */
    public static final Set<ArtifactKind> REQUIRED_KINDS = Set.of(ArtifactKind.values());

    public SupplierEvidencePack {
        Objects.requireNonNull(activation, "activation é obrigatória: o pacote de evidência só "
                + "nasce no contexto da ativação da norma (correção D1 da revisão da ED-73)");
        activation.requireEnabled();
        items = items == null ? List.of() : List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("pacote de evidência vazio não se monta: ao menos "
                    + "um artefato referenciado é obrigatório (correção D1: a régua vale no "
                    + "construtor canônico, não só na fábrica)");
        }
        long distinctRefs = items.stream().map(i -> i.kind() + "|" + i.ref()).distinct().count();
        if (distinctRefs != items.size()) {
            throw new IllegalArgumentException("artefato duplicado no pacote (mesmo tipo e mesma "
                    + "referência): o pacote referencia cada artefato uma única vez");
        }
    }

    /** Montagem do pacote: só com a norma ligada (porta da ativação). */
    public static SupplierEvidencePack assemble(Iso27001Activation activation, List<PackItem> items) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("pacote de evidência vazio não se monta: ao menos um "
                    + "artefato referenciado é obrigatório");
        }
        return new SupplierEvidencePack(activation, items);
    }

    /** Tipos exigidos ainda ausentes: vazio quando o pacote está completo. */
    public Set<ArtifactKind> missingKinds() {
        Set<ArtifactKind> missing = new TreeSet<>(REQUIRED_KINDS);
        for (PackItem item : items) {
            missing.remove(item.kind());
        }
        return Set.copyOf(missing);
    }

    /** Verdadeiro só com os quatro tipos do aceite presentes. */
    public boolean isComplete() {
        return missingKinds().isEmpty();
    }

    /** Referências presentes de um tipo (para ligar à matriz de responsabilidade). */
    public List<String> refsOfKind(ArtifactKind kind) {
        List<String> refs = new ArrayList<>();
        for (PackItem item : items) {
            if (item.kind() == kind) {
                refs.add(item.ref());
            }
        }
        return List.copyOf(refs);
    }

    /** Conteúdo canônico do manifesto (ordenado por tipo e referência). */
    public String canonicalManifest() {
        List<PackItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> {
            int byKind = a.kind().name().compareTo(b.kind().name());
            return byKind != 0 ? byKind : a.ref().compareTo(b.ref());
        });
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pack", "supplier-evidence-iso27001");
        map.put("complete", isComplete());
        map.put("missingKinds", missingKinds().stream().map(Enum::name).sorted().toList());
        map.put("items", sorted.stream().map(i -> Map.of(
                "kind", i.kind().name(), "ref", i.ref(), "sha256", i.sha256Hex())).toList());
        return Json.write(map);
    }

    /** SHA-256 (hex) do manifesto canônico: a prova de integridade da exportação. */
    public String manifestHash() {
        return sha256Hex(canonicalManifest());
    }

    /** Exportação: manifesto + o hash dele, num único JSON verificável. */
    public String exportJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("manifest", Json.readMap(canonicalManifest()));
        map.put("manifestSha256", manifestHash());
        return Json.write(map);
    }

    /** Verificação de quem recebe: o hash confere com o manifesto recebido? */
    public static boolean verifyManifestHash(String canonicalManifest, String expectedSha256Hex) {
        return canonicalManifest != null && expectedSha256Hex != null
                && sha256Hex(canonicalManifest).equals(expectedSha256Hex);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
