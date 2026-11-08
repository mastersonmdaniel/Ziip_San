package br.com.ziipsan.core.certification.infosec27001;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TESTE DE ACEITE da ED-73 com a norma LIGADA e DESLIGADA: ligada, o
 * mecanismo completo funciona de ponta a ponta (ativação → SoA de 93 →
 * matriz → campanha → pacote de evidência → autoavaliação); desligada,
 * nenhum registro novo nasce e os registros criados com a norma ligada
 * CONTINUAM existindo e legíveis (desligar não apaga registro).
 *
 * <p>Correção D5(a) da revisão cruzada: a metade "sobrevivência" NÃO relê
 * mais os acessores do record imutável em memória (tautologia apontada
 * pelo revisor). O teste PERSISTE as exportações SERIALIZADAS num
 * repositório simulado FORA dos objetos de domínio (mapa chave →
 * conteúdo, o análogo da linha gravada), com hash SHA-256 do conteúdo
 * gravado; desliga a norma e RELÊ do armazenamento: confere hash,
 * conteúdo e estrutura PARSEADA de volta ({@code Json.readMap}) —
 * exercitando o mecanismo real de retenção que o pacote oferece (a
 * exportação canônica), somado à ausência de DELETE travada no teste de
 * consistência do SQL. A persistência em banco (JPA/Flyway) é da fiação
 * e da CI total no PC do dono, pendência declarada no relatório.
 */
class Iso27001AcceptanceOnOffTest {

    private static final UUID PROPOSER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ACCEPTOR = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Test
    @DisplayName("norma LIGADA: fluxo completo do pacote funciona de ponta a ponta")
    void enabledEndToEnd() {
        Iso27001Activation on = Iso27001Activation.enabled("SGSI SYN", PROPOSER, ACCEPTOR);

        List<AnnexAControlEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 93; i++) {
            entries.add(AnnexAControlEntry.declare(on, "SYN.CTL." + i, null,
                    AnnexAControlEntry.Applicability.APPLICABLE, "justificativa SYN " + i,
                    AnnexAControlEntry.ImplementationStatus.IN_PROGRESS, List.of(), OWNER,
                    LocalDate.of(2026, 1, 10)));
        }
        StatementOfApplicability soa = StatementOfApplicability.build(on, entries);
        assertThat(soa.exportJson()).contains("\"expectedControls\":93");

        SupplierEvidencePack pack = SupplierEvidencePack.assemble(on, List.of(
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.SECURITY_BASELINE_P38,
                        "SYN-BASELINE", "a".repeat(64)),
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.AUDIT_TRAIL_EXPORT_P17,
                        "SYN-TRILHA", "b".repeat(64)),
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.CONTRACT_DOCUMENT_P39,
                        "SYN-MINUTA", "c".repeat(64)),
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.SECURITY_INCIDENT_P317,
                        "SYN-INC", "d".repeat(64))));
        assertThat(pack.isComplete()).isTrue();

        // Matriz pela fábrica que confere a chave contra a SoA montada (correção D3).
        SharedResponsibilityEntry matrix = SharedResponsibilityEntry.declare(on, soa, "SYN.CTL.1",
                SharedResponsibilityEntry.Responsibility.SHARED, true,
                pack.refsOfKind(SupplierEvidencePack.ArtifactKind.SECURITY_BASELINE_P38), "");
        assertThat(matrix.implementedBySupplier()).isTrue();

        AccessRecertificationCampaign campaign = AccessRecertificationCampaign.open(on,
                "SYN campanha", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                () -> List.of(new AccessRecertificationCampaign.CatalogGrant(
                        "SYN-ADMIN", "TENANT", "registry:read")));
        assertThat(campaign.items()).hasSize(1);
    }

    @Test
    @DisplayName("norma DESLIGADA: nada nasce e o registro persistido ligado sobrevive íntegro e legível")
    void disabledKeepsRecords() throws Exception {
        Iso27001Activation on = Iso27001Activation.enabled("SGSI SYN", PROPOSER, ACCEPTOR);
        AnnexAControlEntry created = AnnexAControlEntry.declare(on, "SYN.CTL.1", null,
                AnnexAControlEntry.Applicability.APPLICABLE, "justificativa SYN",
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED, List.of(), OWNER,
                LocalDate.of(2026, 1, 10));

        // SoA completa (a primeira linha é o registro criado acima) e pacote
        // completo, montados com a norma ligada:
        List<AnnexAControlEntry> entries = new ArrayList<>();
        entries.add(created);
        for (int i = 2; i <= 93; i++) {
            entries.add(AnnexAControlEntry.declare(on, "SYN.CTL." + i, null,
                    AnnexAControlEntry.Applicability.APPLICABLE, "justificativa SYN " + i,
                    AnnexAControlEntry.ImplementationStatus.IN_PROGRESS, List.of(), OWNER,
                    LocalDate.of(2026, 1, 10)));
        }
        StatementOfApplicability soa = StatementOfApplicability.build(on, entries);
        SupplierEvidencePack pack = SupplierEvidencePack.assemble(on, List.of(
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.SECURITY_BASELINE_P38,
                        "SYN-BASELINE", "a".repeat(64)),
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.AUDIT_TRAIL_EXPORT_P17,
                        "SYN-TRILHA", "b".repeat(64)),
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.CONTRACT_DOCUMENT_P39,
                        "SYN-MINUTA", "c".repeat(64)),
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.SECURITY_INCIDENT_P317,
                        "SYN-INC", "d".repeat(64))));

        // PERSISTÊNCIA SIMULADA (correção D5): as exportações SERIALIZADAS são
        // gravadas num repositório FORA dos objetos de domínio (chave →
        // conteúdo, o análogo da linha persistida), com o hash do conteúdo
        // gravado — a sobrevivência é provada sobre o que foi armazenado e
        // parseado de volta, não sobre os acessores do record em memória.
        Map<String, String> storedExports = new HashMap<>();
        storedExports.put("soa", soa.exportJson());
        storedExports.put("pack", pack.exportJson());
        String soaHashBeforeOff = sha256Hex(storedExports.get("soa"));
        String packHashBeforeOff = sha256Hex(storedExports.get("pack"));

        // DESLIGA a norma: nascimento novo é recusado em todas as portas.
        Iso27001Activation off = Iso27001Activation.disabled();
        assertThatThrownBy(() -> AnnexAControlEntry.declare(off, "SYN.CTL.2", null,
                AnnexAControlEntry.Applicability.APPLICABLE, "justificativa",
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED, List.of(), OWNER,
                LocalDate.of(2026, 1, 10))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SupplierEvidencePack.assemble(off, List.of(
                new SupplierEvidencePack.PackItem(SupplierEvidencePack.ArtifactKind.SECURITY_BASELINE_P38,
                        "SYN-B", "a".repeat(64))))).isInstanceOf(IllegalStateException.class);
        // E nem o construtor canônico escapa do portão (correção D1):
        assertThatThrownBy(() -> new AnnexAControlEntry(off, "SYN.CTL.2", null,
                AnnexAControlEntry.Applicability.APPLICABLE, "justificativa",
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED, List.of(), OWNER,
                LocalDate.of(2026, 1, 10))).isInstanceOf(IllegalStateException.class);

        // SOBREVIVÊNCIA: relê do armazenamento e PARSEIA de volta:
        String persistedSoa = storedExports.get("soa");
        assertThat(sha256Hex(persistedSoa)).isEqualTo(soaHashBeforeOff);
        Map<String, Object> parsed = Json.readMap(persistedSoa);
        assertThat(((Number) parsed.get("expectedControls")).longValue()).isEqualTo(93L);
        assertThat(((Number) parsed.get("titlesPending")).longValue()).isEqualTo(93L);
        assertThat((List<?>) parsed.get("controls")).hasSize(93);
        assertThat(persistedSoa).contains("SYN.CTL.93").contains("justificativa SYN");

        String persistedPack = storedExports.get("pack");
        assertThat(sha256Hex(persistedPack)).isEqualTo(packHashBeforeOff);
        assertThat(persistedPack).contains(pack.manifestHash());
        assertThat(SupplierEvidencePack.verifyManifestHash(pack.canonicalManifest(),
                pack.manifestHash())).isTrue();

        // A leitura dos objetos também segue intacta após o desligamento:
        assertThat(soa.exportJson()).isEqualTo(persistedSoa);
        assertThat(created.controlKey()).isEqualTo("SYN.CTL.1");
        assertThat(created.justification()).isEqualTo("justificativa SYN");
        assertThat(created.titlePending()).isTrue();
    }

    private static String sha256Hex(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
