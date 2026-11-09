package br.com.ziipsan.core.certification.external;

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
 * Pacote de evidencia por norma ligada (ED-75): a montagem, por
 * REFERENCIA + SHA-256, dos artefatos de evidencia de QUALQUER
 * subconjunto das seis normas das ED-69..ED-74, mais a secao de
 * evidencia do Ziip como fornecedor.
 *
 * <p>Regras do aceite, todas no construtor canonico (padrao reforcado
 * das ED-70/ED-73: a regua vale por qualquer caminho de construcao):</p>
 * <ul>
 *   <li>o subconjunto de normas ligadas e obrigatorio e nao vazio —
 *       pacote sem nenhuma norma ligada nao nasce;</li>
 *   <li>todo item pertence a uma norma DO subconjunto ligado — item
 *       de norma desligada e recusado;</li>
 *   <li>ao menos um item de evidencia e obrigatorio — pacote vazio
 *       nao se monta;</li>
 *   <li>dado pessoal nao entra: cada {@link EvidenceItem} ja recusa a
 *       classe PERSONAL na propria construcao (classe a, travado);</li>
 *   <li>artefato duplicado (mesma norma, mesma referencia) e
 *       recusado;</li>
 *   <li>a secao do fornecedor e parte do pacote: ela pode estar
 *       incompleta, mas entao o pacote SE DECLARA incompleto
 *       ({@link #missingSupplierKinds()}), como na ED-73 — nunca finge
 *       completude;</li>
 *   <li>o manifesto declara as normas efetivamente cobertas por itens
 *       ({@link #coveredStandards()}), subconjunto das ligadas — norma
 *       ligada sem item aparece em {@code enabledStandards} e NAO em
 *       {@code coveredStandards} (dobra R5-A, F1-B3: declaracao, nao
 *       recusa — o pacote nasce e diz o que cobre).</li>
 * </ul>
 *
 * <p>O manifesto canonico tem hash proprio verificavel
 * ({@link #manifestHash()} / {@link #verifyManifestHash(String, String)}),
 * como o pacote do fornecedor da ED-73. JSON somente pela classe
 * {@link Json} do core (regra do projeto).</p>
 */
public record EvidencePack(Set<CertificationStandard> enabledStandards, List<EvidenceItem> items,
                           List<SupplierEvidenceItem> supplierEvidence) {

    public EvidencePack {
        enabledStandards = CertificationStandard.subsetOf(enabledStandards);
        if (enabledStandards.isEmpty()) {
            throw new IllegalArgumentException("pacote de evidencia exige ao menos uma norma "
                    + "ligada (cert.<norma>.enabled): pacote sem norma ligada nao nasce");
        }
        items = items == null ? List.of() : List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("pacote de evidencia vazio nao se monta: ao "
                    + "menos um item referenciado e obrigatorio");
        }
        for (EvidenceItem item : items) {
            if (!enabledStandards.contains(item.standard())) {
                throw new IllegalArgumentException("item de norma DESLIGADA no pacote: "
                        + item.standard() + " nao esta no subconjunto de normas ligadas "
                        + enabledStandards + " (pacote de evidencia e por norma ligada)");
            }
        }
        long distinct = items.stream().map(i -> i.standard() + "|" + i.ref()).distinct().count();
        if (distinct != items.size()) {
            throw new IllegalArgumentException("artefato duplicado no pacote (mesma norma e "
                    + "mesma referencia): cada artefato entra uma unica vez");
        }
        supplierEvidence = supplierEvidence == null ? List.of() : List.copyOf(supplierEvidence);
        long distinctSupplier = supplierEvidence.stream()
                .map(i -> i.kind() + "|" + i.ref()).distinct().count();
        if (distinctSupplier != supplierEvidence.size()) {
            throw new IllegalArgumentException("artefato do fornecedor duplicado na secao "
                    + "(mesmo tipo e mesma referencia)");
        }
    }

    /** Montagem do pacote: subconjunto ligado + itens + secao do fornecedor. */
    public static EvidencePack assemble(Set<CertificationStandard> enabledStandards,
                                        List<EvidenceItem> items,
                                        List<SupplierEvidenceItem> supplierEvidence) {
        return new EvidencePack(enabledStandards, items, supplierEvidence);
    }

    /** Normas efetivamente cobertas por itens (subconjunto das ligadas). */
    public Set<CertificationStandard> coveredStandards() {
        Set<CertificationStandard> covered = new TreeSet<>();
        for (EvidenceItem item : items) {
            covered.add(item.standard());
        }
        return Set.copyOf(covered);
    }

    /** Itens de uma norma do pacote. */
    public List<EvidenceItem> itemsOf(CertificationStandard standard) {
        List<EvidenceItem> out = new ArrayList<>();
        for (EvidenceItem item : items) {
            if (item.standard() == standard) {
                out.add(item);
            }
        }
        return List.copyOf(out);
    }

    /** Tipos da secao do fornecedor ainda ausentes: vazio quando completa. */
    public Set<SupplierEvidenceItem.ArtifactKind> missingSupplierKinds() {
        Set<SupplierEvidenceItem.ArtifactKind> missing =
                new TreeSet<>(SupplierEvidenceItem.REQUIRED_KINDS);
        for (SupplierEvidenceItem item : supplierEvidence) {
            missing.remove(item.kind());
        }
        return Set.copyOf(missing);
    }

    /** Verdadeiro so com os quatro tipos de evidencia do fornecedor presentes. */
    public boolean isSupplierEvidenceComplete() {
        return missingSupplierKinds().isEmpty();
    }

    /** Conteudo canonico do manifesto (ordenado por norma, tipo e referencia). */
    public String canonicalManifest() {
        List<EvidenceItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> {
            int byStandard = a.standard().name().compareTo(b.standard().name());
            if (byStandard != 0) {
                return byStandard;
            }
            int byKind = a.dataKind().name().compareTo(b.dataKind().name());
            return byKind != 0 ? byKind : a.ref().compareTo(b.ref());
        });
        List<SupplierEvidenceItem> sortedSupplier = new ArrayList<>(supplierEvidence);
        sortedSupplier.sort((a, b) -> {
            int byKind = a.kind().name().compareTo(b.kind().name());
            return byKind != 0 ? byKind : a.ref().compareTo(b.ref());
        });
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pack", "certification-evidence-ed75");
        map.put("personalData", false);
        map.put("enabledStandards", enabledStandards.stream().map(Enum::name).sorted().toList());
        // Dobra R5-A (F1-B3): o manifesto declara a cobertura por norma
        // — as ligadas com ao menos um item — calculada pelo metodo ja
        // existente coveredStandards(). Norma ligada sem item nao some:
        // segue em enabledStandards e fica fora de coveredStandards.
        map.put("coveredStandards", coveredStandards().stream().map(Enum::name).sorted().toList());
        map.put("items", sorted.stream().map(i -> Map.of(
                "standard", i.standard().name(), "dataKind", i.dataKind().name(),
                "ref", i.ref(), "sha256", i.sha256Hex())).toList());
        map.put("supplierEvidenceComplete", isSupplierEvidenceComplete());
        map.put("supplierMissingKinds",
                missingSupplierKinds().stream().map(Enum::name).sorted().toList());
        map.put("supplierEvidence", sortedSupplier.stream().map(i -> Map.of(
                "kind", i.kind().name(), "ref", i.ref(), "sha256", i.sha256Hex())).toList());
        return Json.write(map);
    }

    /** SHA-256 (hex) do manifesto canonico: a prova de integridade da exportacao. */
    public String manifestHash() {
        return sha256Hex(canonicalManifest());
    }

    /** Exportacao: manifesto + o hash dele, num unico JSON verificavel. */
    public String exportJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("manifest", Json.readMap(canonicalManifest()));
        map.put("manifestSha256", manifestHash());
        return Json.write(map);
    }

    /** Verificacao de quem recebe: o hash confere com o manifesto recebido? */
    public static boolean verifyManifestHash(String canonicalManifest, String expectedSha256Hex) {
        return canonicalManifest != null && expectedSha256Hex != null
                && sha256Hex(canonicalManifest).equals(expectedSha256Hex);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
