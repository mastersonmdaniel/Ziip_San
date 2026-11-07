package br.com.ziipsan.platform.templates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ponte P2.32 x P2.30: o conteudo legal entra como bloco SYSTEM travado,
 * identificado pela chave do campo obrigatorio.
 */
class LegalTemplateBlocksTest {

    @Test
    @DisplayName("blocos SYSTEM travados carregam a chave do campo como id")
    void systemBlocksCarryFieldKeys() {
        List<TemplateBlock> blocks = LegalTemplateBlocks.systemBlocks(
                List.of("supplier.identification_block", "water_quality.monthly_block"),
                List.of("texto do fornecedor", "texto da qualidade da agua"));
        assertThat(blocks).allMatch(b -> b.origin() == TemplateBlock.Origin.SYSTEM && b.locked());
        assertThat(LegalTemplateBlocks.fieldKeysOf(blocks))
                .isEqualTo(Set.of("supplier.identification_block", "water_quality.monthly_block"));
        assertThat(LegalTemplateBlocks.lockedFieldKeysOf(blocks))
                .isEqualTo(Set.of("supplier.identification_block", "water_quality.monthly_block"));
    }

    @Test
    @DisplayName("bloco do tenant nao entra nas chaves do conteudo legal")
    void tenantBlocksAreNotLegalFields() {
        List<TemplateBlock> blocks = List.of(
                new TemplateBlock("livre", TemplateBlock.Kind.FREE, TemplateBlock.Origin.TENANT, "x"));
        assertThat(LegalTemplateBlocks.fieldKeysOf(blocks)).isEmpty();
    }

    @Test
    @DisplayName("chaves e textos em quantidades diferentes sao recusados")
    void mismatchedListsAreRefused() {
        assertThatThrownBy(() -> LegalTemplateBlocks.systemBlocks(List.of("a"), List.of("x", "y")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
