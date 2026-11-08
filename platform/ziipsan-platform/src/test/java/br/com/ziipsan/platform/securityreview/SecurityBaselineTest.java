package br.com.ziipsan.platform.securityreview;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** P3.8: a linha de base so vale completa — capitulo sem caso, API sem cobertura ou nivel errado reprova. */
class SecurityBaselineTest {

    @Test
    @DisplayName("matriz valida: 14 capitulos ASVS cobertos, API Top 10 completa, A03 e A10 com caso")
    void matrizValida() {
        assertThat(SecurityBaseline.asvsChapters()).hasSize(14);
        assertThat(SecurityBaseline.validate()).isEmpty();
    }

    @Test
    @DisplayName("nivel 3 so em autenticacao, pagamento, dados pessoais e auth da API; o resto e nivel 2")
    void niveisPorComponente() {
        assertThat(SecurityBaseline.componentLevels())
                .filteredOn(c -> c.level() == SecurityBaseline.Level.L3)
                .hasSize(4);
        assertThat(SecurityBaseline.componentLevels())
                .filteredOn(c -> c.level() == SecurityBaseline.Level.L2)
                .hasSize(4);
    }

    @Test
    @DisplayName("taxonomia OWASP Top 10:2025 e a OFICIAL de 2025, codigo e nome (D1: a de 2021 rotulada nao volta)")
    void taxonomiaOwaspTop10_2025Oficial() {
        assertThat(SecurityBaseline.owaspTop10_2025()).containsExactly(
                "A01 Broken Access Control",
                "A02 Security Misconfiguration",
                "A03 Software Supply Chain Failures",
                "A04 Cryptographic Failures",
                "A05 Injection",
                "A06 Insecure Design",
                "A07 Authentication Failures",
                "A08 Software or Data Integrity Failures",
                "A09 Security Logging and Alerting Failures",
                "A10 Mishandling of Exceptional Conditions");
        // Os codigos dos casos seguem a semantica 2025: criptografia = A04,
        // misconfiguration = A02, e a categoria extinta de 2021
        // ("Vulnerable and Outdated Components") nao aparece em caso algum.
        assertThat(SecurityCase.COFRE.owaspTop10()).containsExactlyInAnyOrder("A02", "A04");
        assertThat(SecurityCase.ISOLAMENTO_TENANTS.owaspTop10()).contains("A04").doesNotContain("A02");
        assertThat(SecurityCase.DAST_AUTENTICADA.owaspTop10()).containsExactly("A02");
        assertThat(SecurityCase.CADEIA_SUPRIMENTOS.owaspTop10()).containsExactly("A03");
    }

    @Test
    @DisplayName("matriz JSON sai pela classe Json do core, com o estado RNF de cada capitulo exposto e criterio LLM da ED-43")
    void matrizJson() {
        JsonNode tree = Json.readTree(SecurityBaseline.matrixJson());
        assertThat(tree.get("problems").size()).isZero();
        assertThat(tree.get("llmTop10_2025CriterionFor").asString()).isEqualTo("ED-43");
        assertThat(tree.get("llmTop10_2025").size()).isEqualTo(10);
        // O campo rnfConfirmada e EXPOSTO em todo capitulo, qualquer que
        // seja o valor (correcao D8: este teste NAO trava a pendencia —
        // quando a integracao confirmar todos os mapeamentos, ele segue
        // verde). O que se exige: pendencia, quando existir, vem com a
        // fonte/declaracao preenchida — nunca pendencia silenciosa.
        assertThat(tree.get("asvsChapters").size()).isEqualTo(14);
        for (JsonNode ch : tree.get("asvsChapters")) {
            assertThat(ch.has("rnfConfirmada")).isTrue();
            assertThat(ch.get("rnfConfirmada").isBoolean()).isTrue();
            assertThat(ch.get("rnfFonte").asString()).isNotBlank();
            assertThat(ch.get("rnfRefs").asString()).isNotBlank();
            if (!ch.get("rnfConfirmada").asBoolean()) {
                assertThat(ch.get("rnfFonte").asString())
                        .as("capitulo %s pendente precisa declarar a fonte da pendencia", ch.get("chapter"))
                        .isNotBlank();
            }
        }
        // A lista 2025 gravada na matriz e a oficial (mesma trava do D1).
        assertThat(tree.get("owaspTop10_2025").get(1).asString()).isEqualTo("A02 Security Misconfiguration");
        assertThat(tree.get("owaspTop10_2025").get(3).asString()).isEqualTo("A04 Cryptographic Failures");
    }

    @Test
    @DisplayName("casos do pentest local estao todos no registro, com execucao declarada")
    void casosDoPentestRegistrados() {
        assertThat(SecurityCase.values()).extracting(SecurityCase::name)
                .contains("VINCULO", "ENUMERACAO_MATRICULA", "ENUMERACAO_CODIGO_VINCULACAO",
                        "ENUMERACAO_LOGIN", "ENUMERACAO_RECUPERACAO", "ENUMERACAO_CONVIDADO",
                        "TROCA_TOKEN_REALMS", "DOWNLOAD_PORTAL", "ACESSO_ASSISTIDO",
                        "ACESSO_EMERGENCIA", "DEFESA_AUTOMACAO", "ISOLAMENTO_TENANTS",
                        "COFRE", "DAST_AUTENTICADA", "API_PUBLICA_TOP10");
    }
}
