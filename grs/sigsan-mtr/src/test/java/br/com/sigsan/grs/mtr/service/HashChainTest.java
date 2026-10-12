package br.com.sigsan.grs.mtr.service;

import br.com.sigsan.grs.mtr.service.HashChain.Link;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashChainTest {

    @Test
    @DisplayName("SHA-256 confere com os vetores oficiais (FIPS 180-4): \"abc\" e a entrada vazia")
    void sha256KnownVectors() {
        assertThat(HashChain.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(HashChain.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("o primeiro elo é SHA-256 de \"\\n\" + conteúdo")
    void firstLinkDefinition() {
        assertThat(HashChain.next(null, "abc"))
                .isEqualTo(HashChain.sha256Hex("\nabc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("o elo seguinte depende do hash anterior")
    void chainsOnThePreviousHash() {
        String h1 = HashChain.next(null, "emitido");
        String h2 = HashChain.next(h1, "coletado");
        String h2WithOtherParent = HashChain.next(HashChain.next(null, "outro"), "coletado");

        assertThat(h2).isEqualTo(HashChain.sha256Hex((h1 + "\ncoletado").getBytes(StandardCharsets.UTF_8)));
        assertThat(h2).isNotEqualTo(h2WithOtherParent);
    }

    @Test
    @DisplayName("usa UTF-8 e não o charset da plataforma: o hash de texto acentuado é estável")
    void usesUtf8() {
        String utf8 = HashChain.sha256Hex("\nação".getBytes(StandardCharsets.UTF_8));
        String latin1 = HashChain.sha256Hex("\nação".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(HashChain.next(null, "ação")).isEqualTo(utf8).isNotEqualTo(latin1);
    }

    @Test
    @DisplayName("hexadecimal minúsculo de 64 caracteres")
    void shape() {
        assertThat(HashChain.next(null, "x")).matches("[0-9a-f]{64}");
    }

    private static List<Link> build(String... contents) {
        List<Link> links = new ArrayList<>();
        String previous = null;
        for (String c : contents) {
            String h = HashChain.next(previous, c);
            links.add(new Link(c, h));
            previous = h;
        }
        return links;
    }

    @Test
    @DisplayName("cadeia íntegra: nenhum elo quebrado")
    void intactChain() {
        assertThat(HashChain.firstBrokenLink(build("a", "b", "c", "d"))).isEqualTo(-1);
        assertThat(HashChain.firstBrokenLink(List.of())).isEqualTo(-1);
    }

    @Test
    @DisplayName("alterar o conteúdo de um elo quebra esse elo (e os seguintes)")
    void tamperedContentIsDetected() {
        List<Link> links = new ArrayList<>(build("a", "b", "c", "d"));
        links.set(2, new Link("C ADULTERADO", links.get(2).storedHash()));
        assertThat(HashChain.firstBrokenLink(links)).isEqualTo(2);
    }

    @Test
    @DisplayName("alterar o hash guardado de um elo é detectado nele e no seguinte")
    void tamperedHashIsDetected() {
        List<Link> links = new ArrayList<>(build("a", "b", "c"));
        links.set(0, new Link("a", "0".repeat(64)));
        assertThat(HashChain.firstBrokenLink(links)).isEqualTo(0);
    }

    @Test
    @DisplayName("remover um elo do meio é detectado")
    void removedLinkIsDetected() {
        List<Link> links = new ArrayList<>(build("a", "b", "c"));
        links.remove(1);
        assertThat(HashChain.firstBrokenLink(links)).isEqualTo(1);
    }

    @Test
    @DisplayName("conteúdo nulo é recusado (nunca vira um hash qualquer)")
    void nullContentIsRejected() {
        assertThatThrownBy(() -> HashChain.next(null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
