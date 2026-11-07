package br.com.ziipsan.platform.templates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guarda estrutural da marca sobre o conteúdo legal (P2.17; CAD-96,
 * RAS-04): confere que uma lista de blocos COM marca preserva, em relação
 * ao modelo sem marca, tudo o que a P2.32 protege — e que nenhum campo
 * obrigatório do perfil se perdeu.
 *
 * <p>Problemas nomeados:
 * <ol>
 *   <li>bloco SYSTEM removido, alterado (texto, tipo ou origem) ou
 *       reordenado em relação aos demais SYSTEM — o conteúdo legal não
 *       pode ser sobreposto nem deslocado pela camada de marca;</li>
 *   <li>bloco LOCKED (de qualquer origem) removido, alterado ou
 *       destravado — a trava da P2.30 vale também contra a marca;</li>
 *   <li>campo obrigatório do perfil ausente nos blocos SYSTEM com marca,
 *       ou presente fora do bloco travado quando a exigência é de bloco
 *       travado (as chaves vêm do catálogo da P2.32, lidas pela ponte
 *       {@link LegalTemplateBlocks});</li>
 *   <li>bloco de marca (ids {@code brand.header}/{@code brand.footer}) que
 *       não seja FREE/TENANT — a marca nunca se disfarça de conteúdo do
 *       sistema.</li>
 * </ol>
 *
 * <p>Blocos FREE/TENANT novos fora do namespace de marca não são problema
 * desta guarda (são edição normal do modelo, conferida pelo motor da
 * P2.30); blocos FREE do modelo alterados também não — a guarda protege o
 * legal e o travado, que é o que o aceite manda.
 */
public final class BrandedTemplateGuard {

    private BrandedTemplateGuard() {
    }

    /** Problemas de preservação entre o modelo original e o com marca. */
    public static List<String> preservationProblems(List<TemplateBlock> original,
                                                    List<TemplateBlock> branded) {
        if (original == null || branded == null) {
            throw new IllegalArgumentException("original e branded sao obrigatorios");
        }
        List<String> problems = new ArrayList<>();
        Map<String, TemplateBlock> brandedById = new LinkedHashMap<>();
        for (TemplateBlock block : branded) {
            brandedById.put(block.id(), block);
        }
        for (TemplateBlock old : original) {
            boolean protectedBlock = old.origin() == TemplateBlock.Origin.SYSTEM || old.locked();
            if (!protectedBlock) {
                continue;
            }
            TemplateBlock kept = brandedById.get(old.id());
            if (kept == null) {
                problems.add("Bloco protegido removido pela marca: " + old.id() + ".");
            } else if (!kept.text().equals(old.text()) || kept.kind() != old.kind()
                    || kept.origin() != old.origin()) {
                problems.add("Bloco protegido alterado pela marca: " + old.id() + ".");
            }
        }
        // Ordem relativa dos SYSTEM: a marca não desloca o conteúdo legal.
        List<String> originalSystemOrder = systemIds(original);
        List<String> brandedSystemOrder = systemIds(branded);
        List<String> brandedCommonOrder = new ArrayList<>();
        for (String id : brandedSystemOrder) {
            if (originalSystemOrder.contains(id)) {
                brandedCommonOrder.add(id);
            }
        }
        List<String> originalCommonOrder = new ArrayList<>();
        for (String id : originalSystemOrder) {
            if (brandedSystemOrder.contains(id)) {
                originalCommonOrder.add(id);
            }
        }
        if (!originalCommonOrder.equals(brandedCommonOrder)) {
            problems.add("Blocos de conteudo legal reordenados pela marca.");
        }
        for (TemplateBlock block : branded) {
            if ((BrandedDocumentComposer.HEADER_BLOCK_ID.equals(block.id())
                    || BrandedDocumentComposer.FOOTER_BLOCK_ID.equals(block.id()))
                    && (block.origin() != TemplateBlock.Origin.TENANT
                    || block.kind() != TemplateBlock.Kind.FREE)) {
                problems.add("Bloco de marca fora do papel FREE/TENANT: " + block.id() + ".");
            }
        }
        return List.copyOf(problems);
    }

    /**
     * Problemas de conteúdo obrigatório: cada chave exigida do perfil
     * precisa estar nos blocos SYSTEM da lista com marca; a que exige
     * bloco travado precisa estar travada. As chaves são as do catálogo
     * {@code RequiredBillContent} da P2.32 (o chamador as lê de lá — esta
     * guarda não duplica o catálogo).
     */
    public static List<String> requiredContentProblems(List<TemplateBlock> branded,
                                                       Set<String> requiredFieldKeys,
                                                       Set<String> lockedRequiredFieldKeys) {
        if (branded == null || requiredFieldKeys == null) {
            throw new IllegalArgumentException("branded e requiredFieldKeys sao obrigatorios");
        }
        Set<String> present = LegalTemplateBlocks.fieldKeysOf(branded);
        Set<String> locked = LegalTemplateBlocks.lockedFieldKeysOf(branded);
        List<String> problems = new ArrayList<>();
        for (String key : requiredFieldKeys) {
            if (!present.contains(key)) {
                problems.add("Campo obrigatorio ausente no modelo com marca: " + key + ".");
            } else if (lockedRequiredFieldKeys != null && lockedRequiredFieldKeys.contains(key)
                    && !locked.contains(key)) {
                problems.add("Campo obrigatorio fora do bloco travado no modelo com marca: " + key + ".");
            }
        }
        return List.copyOf(problems);
    }

    /** Aprova ou lança {@link IllegalArgumentException} com todos os problemas. */
    public static void assertPreserved(List<TemplateBlock> original, List<TemplateBlock> branded,
                                       Set<String> requiredFieldKeys,
                                       Set<String> lockedRequiredFieldKeys) {
        List<String> problems = new ArrayList<>(preservationProblems(original, branded));
        problems.addAll(requiredContentProblems(branded, requiredFieldKeys, lockedRequiredFieldKeys));
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Modelo com marca recusado: "
                    + String.join("; ", problems));
        }
    }

    private static List<String> systemIds(List<TemplateBlock> blocks) {
        List<String> ids = new ArrayList<>();
        for (TemplateBlock block : blocks) {
            if (block.origin() == TemplateBlock.Origin.SYSTEM) {
                ids.add(block.id());
            }
        }
        return ids;
    }
}
