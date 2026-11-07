package br.com.ziipsan.platform.central.complete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.15: kit de evidências — hash calculado à mão fora da JVM:
 * SHA-256 de "" (string vazia) = e3b0c442...b855 (valor público conhecido);
 * o manifesto canônico de um item é
 * {"kit":"central-evidence","reference":"2026-11","items":[{"name":"relatorio.json","sha256":"e3b0...b855"}]}
 * e o SHA-256 dele, calculado com sha256sum sobre essa string exata, é
 * a5a242eb...f85d — se o Json do core mudar a forma canônica, este teste
 * quebra alto, que é o comportamento desejado (o hash é a identidade).
 */
class EvidenceKitTest {

    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String EXPECTED_KIT_SHA256 = "a5a242eb480bf8106c7dcbf8585d0c3cfbeb49fda9d10684872d45ac7cd2f85d";

    @Test
    @DisplayName("hash do kit calculado à mão: manifesto canônico via Json do core e SHA-256 conferido")
    void kitHashHandCalculated() {
        EvidenceKit.Kit kit = EvidenceKit.build("2026-11",
                List.of(new EvidenceKit.EvidenceItem("relatorio.json", EMPTY_SHA256)));
        assertThat(kit.manifestJson()).isEqualTo(
                "{\"kit\":\"central-evidence\",\"reference\":\"2026-11\",\"items\":[{\"name\":\"relatorio.json\",\"sha256\":\""
                        + EMPTY_SHA256 + "\"}]}");
        assertThat(kit.kitSha256()).isEqualTo(EXPECTED_KIT_SHA256);
        assertThat(EvidenceKit.verify(kit)).isTrue();
    }

    @Test
    @DisplayName("itens são ordenados por nome e duplicado é recusado: a ordem de entrada não muda o hash")
    void itemsSortedDeterministic() {
        String other = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"; // SHA-256 de "abc"
        EvidenceKit.Kit a = EvidenceKit.build("2026-11", List.of(
                new EvidenceKit.EvidenceItem("b.json", other), new EvidenceKit.EvidenceItem("a.json", EMPTY_SHA256)));
        EvidenceKit.Kit b = EvidenceKit.build("2026-11", List.of(
                new EvidenceKit.EvidenceItem("a.json", EMPTY_SHA256), new EvidenceKit.EvidenceItem("b.json", other)));
        assertThat(a.kitSha256()).isEqualTo(b.kitSha256());
        assertThat(a.items()).extracting(EvidenceKit.EvidenceItem::name).containsExactly("a.json", "b.json");
        assertThatThrownBy(() -> EvidenceKit.build("2026-11", List.of(
                new EvidenceKit.EvidenceItem("a.json", EMPTY_SHA256), new EvidenceKit.EvidenceItem("a.json", other))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicado");
    }

    @Test
    @DisplayName("registro na trilha carrega exatamente o hash do kit, a ação e o ator")
    void trailEntryCarriesKitHash() {
        EvidenceKit.Kit kit = EvidenceKit.build("2026-11",
                List.of(new EvidenceKit.EvidenceItem("relatorio.json", EMPTY_SHA256)));
        EvidenceKit.TrailEntry entry = EvidenceKit.trailEntry(kit, "operador-central",
                Instant.parse("2026-11-30T23:00:00Z"));
        assertThat(entry.action()).isEqualTo("CENTRAL_EVIDENCE_KIT_GENERATED");
        assertThat(entry.kitSha256()).isEqualTo(kit.kitSha256()).isEqualTo(EXPECTED_KIT_SHA256);
        assertThat(entry.reference()).isEqualTo("2026-11");
    }

    @Test
    @DisplayName("kit vazio e item sem SHA-256 válido são recusados")
    void refusesEmptyKitAndBadHash() {
        assertThatThrownBy(() -> EvidenceKit.build("2026-11", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vazio");
        assertThatThrownBy(() -> new EvidenceKit.EvidenceItem("x.json", "abc"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-256");
    }

    @Test
    @DisplayName("b2 — TrailEntry valida o hash do kit com o MESMO rigor do item: curto, maiúsculo ou nulo é recusado")
    void trailEntryValidatesKitHashStrictly() {
        Instant at = Instant.parse("2026-11-30T23:00:00Z");
        assertThatThrownBy(() -> new EvidenceKit.TrailEntry(
                "CENTRAL_EVIDENCE_KIT_GENERATED", "abc", "operador-central", at, "2026-11"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-256");
        assertThatThrownBy(() -> new EvidenceKit.TrailEntry(
                "CENTRAL_EVIDENCE_KIT_GENERATED", EXPECTED_KIT_SHA256.toUpperCase(), "operador-central", at, "2026-11"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-256");
        assertThatThrownBy(() -> new EvidenceKit.TrailEntry(
                "CENTRAL_EVIDENCE_KIT_GENERATED", null, "operador-central", at, "2026-11"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-256");
        // E o hash válido (64 hex minúsculos) é aceito:
        EvidenceKit.TrailEntry ok = new EvidenceKit.TrailEntry(
                "CENTRAL_EVIDENCE_KIT_GENERATED", EXPECTED_KIT_SHA256, "operador-central", at, "2026-11");
        assertThat(ok.kitSha256()).isEqualTo(EXPECTED_KIT_SHA256);
    }
}
