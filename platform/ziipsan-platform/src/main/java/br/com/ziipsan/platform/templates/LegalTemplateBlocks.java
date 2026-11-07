package br.com.ziipsan.platform.templates;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ponte da P2.32 com o motor da P2.30: o conteudo legal obrigatorio da
 * fatura entra no modelo como blocos de origem SYSTEM e travados
 * ({@link TemplateBlock.Kind#LOCKED}), identificados pela chave do campo
 * obrigatorio (catalogo {@code RequiredBillContent} do billing).
 *
 * <p>A P2.30 reservou exatamente isto: ela nao cria blocos SYSTEM e o
 * {@code TemplateEngine} os recusa quando vindos do tenant; a insercao
 * legitima e ESTA, alimentada pelas chaves que o servico de conteudo da
 * P2.32 devolve para o perfil vigente. O texto de cada bloco continua
 * sendo o do modelo aprovado — esta classe nao escreve texto juridico.
 *
 * <p>PONTO DE INTEGRACAO (declarado): a regra 3 de
 * {@code TemplateEngine.validationProblems} precisa passar a aceitar bloco
 * SYSTEM cujo id esteja no conjunto devolvido por esta ponte para o tipo
 * FATURA; ate essa fiacao, modelos com blocos SYSTEM sao recusados pelo
 * motor — comportamento seguro (nada e aprovado sem o conteudo conferido
 * tambem pela regra de conteudo da P2.32 no billing).
 */
public final class LegalTemplateBlocks {

    private LegalTemplateBlocks() {
    }

    /** Blocos SYSTEM travados para as chaves exigidas, um por campo, na ordem dada. */
    public static List<TemplateBlock> systemBlocks(List<String> requiredFieldKeys, List<String> blockTexts) {
        if (requiredFieldKeys == null || blockTexts == null
                || requiredFieldKeys.size() != blockTexts.size()) {
            throw new IllegalArgumentException("requiredFieldKeys e blockTexts precisam ter o mesmo tamanho");
        }
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int i = 0; i < requiredFieldKeys.size(); i++) {
            blocks.add(new TemplateBlock(requiredFieldKeys.get(i), TemplateBlock.Kind.LOCKED,
                    TemplateBlock.Origin.SYSTEM, blockTexts.get(i)));
        }
        return List.copyOf(blocks);
    }

    /** Chaves dos campos presentes num modelo (blocos SYSTEM), para a regra de conteudo da P2.32. */
    public static Set<String> fieldKeysOf(List<TemplateBlock> blocks) {
        Set<String> keys = new LinkedHashSet<>();
        for (TemplateBlock block : blocks) {
            if (block.origin() == TemplateBlock.Origin.SYSTEM) {
                keys.add(block.id());
            }
        }
        return Set.copyOf(keys);
    }

    /** Chaves em bloco TRAVADO (o CDC, art. 42-A, exige o bloco travado). */
    public static Set<String> lockedFieldKeysOf(List<TemplateBlock> blocks) {
        Set<String> keys = new LinkedHashSet<>();
        for (TemplateBlock block : blocks) {
            if (block.origin() == TemplateBlock.Origin.SYSTEM && block.locked()) {
                keys.add(block.id());
            }
        }
        return Set.copyOf(keys);
    }
}
