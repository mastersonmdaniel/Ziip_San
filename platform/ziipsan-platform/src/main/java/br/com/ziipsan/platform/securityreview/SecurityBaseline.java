package br.com.ziipsan.platform.securityreview;

import br.com.ziipsan.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Linha de base de seguranca da homologacao local (P3.8, PLT-08):
 * OWASP ASVS 5.0 como criterio de aceite — nivel 2 em geral e nivel 3 em
 * autenticacao, pagamento e dados pessoais (cartao do dono de 29/10/2026)
 * — com os capitulos mapeados as RNF-30 a RNF-39, os casos desta tarefa e
 * da LC.8, e a matriz requisito-teste-evidencia gerada em JSON (somente
 * pela classe {@link Json} do core) para o CI anexar como evidencia.
 *
 * <p><b>Honestidade do mapeamento RNF:</b> do
 * REQUISITOS-NAO-FUNCIONAIS so veio o trecho introdutorio no espelho via
 * MCP (que teve sucesso parcial — ver espelho/LEIA-ME.md): as definicoes
 * linha a linha das RNF-30 a RNF-39 nao vieram no trecho retornado. As ligacoes marcadas
 * {@code rnfConfirmada=true} tem citacao explicita encontrada nas fontes
 * locais (LC.4/ADR-0011 para a RNF-32, P0.40 para a RNF-35, LC.8 para a
 * RNF-36, levantamento 18h para as RNF-33 e RNF-38, ED-18 para a RNF-39).
 * As demais ficam {@code rnfConfirmada=false} e sao pendencia declarada
 * de integracao — a matriz expoe a pendencia em vez de esconde-la.
 */
public final class SecurityBaseline {

    public enum Level { L2, L3 }

    /** Capitulo do ASVS 5.0 com o mapeamento RNF declarado. */
    public record AsvsChapter(int number, String title, String rnfRefs, boolean rnfConfirmada, String rnfFonte) { }

    /** Componente do produto com o nivel ASVS exigido. */
    public record ComponentLevel(String component, Level level, String source) { }

    private SecurityBaseline() { }

    /** Os 14 capitulos do ASVS 5.0. */
    public static List<AsvsChapter> asvsChapters() {
        return List.of(
                new AsvsChapter(1, "Architecture", "RNF-30..RNF-39 (a confirmar)", false,
                        "sem citacao especifica encontrada nas fontes locais"),
                new AsvsChapter(2, "Authentication", "RNF-30..RNF-39 (a confirmar); criterio geral da RNF-36 via LC.8", false,
                        "LC.8 cita RNF-36 para o pentest, nao para este capitulo em particular"),
                new AsvsChapter(3, "Session Management", "RNF-30..RNF-39 (a confirmar)", false,
                        "sem citacao especifica encontrada nas fontes locais"),
                new AsvsChapter(4, "Access Control", "RNF-38", true,
                        "levantamento 18h: testes de RLS e de isolamento no PostgreSQL real servem de evidencia da RNF-38"),
                new AsvsChapter(5, "Validation, Sanitization and Encoding", "RNF-30..RNF-39 (a confirmar)", false,
                        "sem citacao especifica encontrada nas fontes locais"),
                new AsvsChapter(6, "Stored Cryptography", "RNF-32", true,
                        "LC.4 e ADR-0011 citam a RNF-32 para KMS, KEK e cofre de chaves"),
                new AsvsChapter(7, "Error Handling and Logging", "RNF-30..RNF-39 (a confirmar)", false,
                        "sem citacao especifica encontrada nas fontes locais"),
                new AsvsChapter(8, "Data Protection", "RNF-38", true,
                        "levantamento 18h: isolamento/RLS como evidencia da RNF-38"),
                new AsvsChapter(9, "Communication", "RNF-33", true,
                        "levantamento 18h: comunicacao segura SSL tratada sob a RNF-33"),
                new AsvsChapter(10, "Malicious Code", "RNF-35", true,
                        "P0.40: licencas de terceiros pelo SBOM sob a RNF-35 (cadeia de suprimentos)"),
                new AsvsChapter(11, "Business Logic", "RNF-30..RNF-39 (a confirmar)", false,
                        "sem citacao especifica encontrada nas fontes locais"),
                new AsvsChapter(12, "File and Resources", "RNF-30..RNF-39 (a confirmar)", false,
                        "sem citacao especifica encontrada nas fontes locais"),
                new AsvsChapter(13, "API and Web Service", "RNF-36", true,
                        "LC.8 cita a RNF-36 para o pentest que cobre a API e o portal"),
                new AsvsChapter(14, "Configuration", "RNF-35", true,
                        "P0.40/SBOM sob a RNF-35; configuracao verificada no CI"));
    }

    /**
     * Lista dos componentes de cada nivel (exigencia literal do aceite:
     * "a lista dos componentes de cada nivel fica no plano de teste desta
     * tarefa"). Nivel 3 so para autenticacao, pagamento e dados pessoais.
     */
    public static List<ComponentLevel> componentLevels() {
        return List.of(
                new ComponentLevel("Autenticacao: realms Keycloak (ziipsan e ziipsan-portal), login do portal, MFA, gov.br, recuperacao de senha", Level.L3, "P3.3; P0.43; cartao do dono de 29/10/2026"),
                new ComponentLevel("Pagamento: Pix e boleto (P3.2), pagamento convidado, devolucao e pagamentos nao identificados (P3.29)", Level.L3, "P3.2; P3.3; P3.29"),
                new ComponentLevel("Dados pessoais: portal do consumidor, vinculo, downloads, personal_data_access_log, cifra e indice cego, cofre de segredos", Level.L3, "P3.3; P3.20; P0.45"),
                new ComponentLevel("API publica: autenticacao e autorizacao do cliente OAuth", Level.L3, "P2.26 (autenticacao e pagamento/dados no mesmo canal)"),
                new ComponentLevel("Backoffice e Central Ziip (operacao do tenant e da plataforma)", Level.L2, "P3.15; P0.43"),
                new ComponentLevel("Cobranca e faturamento (billing, metering, fiscal)", Level.L2, "P2.5; P2.6; P3.6"),
                new ComponentLevel("Notificacoes, relatorios, GIS e demais modulos de negocio", Level.L2, "P3.5; P3.22; P3.25"),
                new ComponentLevel("API publica: recursos de leitura e webhooks (fora autenticacao/autorizacao)", Level.L2, "P2.26"));
    }

    /**
     * Categorias do OWASP Top 10:2025 registradas na matriz — taxonomia
     * OFICIAL de 2025 (correcao D1 da revisao cruzada: a primeira versao
     * trazia a lista de 2021 rotulada de 2025). Em 2025: A02 e Security
     * Misconfiguration, A04 e Cryptographic Failures, A05 e Injection,
     * A06 e Insecure Design, A07 e Authentication Failures e A09 e
     * Security Logging and Alerting Failures; a categoria "Vulnerable
     * and Outdated Components" (2021) foi extinta e fundida em A03, e o
     * SSRF foi fundido em A01. Os codigos dos casos em
     * {@link SecurityCase} seguem ESTA semantica.
     */
    public static List<String> owaspTop10_2025() {
        return List.of("A01 Broken Access Control", "A02 Security Misconfiguration",
                "A03 Software Supply Chain Failures", "A04 Cryptographic Failures",
                "A05 Injection", "A06 Insecure Design",
                "A07 Authentication Failures",
                "A08 Software or Data Integrity Failures",
                "A09 Security Logging and Alerting Failures",
                "A10 Mishandling of Exceptional Conditions");
    }

    /** Itens do OWASP API Security Top 10 2023 (teste da API da P2.26). */
    public static List<String> apiSecurityTop10_2023() {
        return List.of("API1 Broken Object Level Authorization", "API2 Broken Authentication",
                "API3 Broken Object Property Level Authorization", "API4 Unrestricted Resource Consumption",
                "API5 Broken Function Level Authorization", "API6 Unrestricted Access to Sensitive Business Flows",
                "API7 Server Side Request Forgery", "API8 Security Misconfiguration",
                "API9 Improper Inventory Management", "API10 Unsafe Consumption of APIs");
    }

    /** Itens do OWASP Top 10 para LLM 2025 — criterio registrado da ED-43. */
    public static List<String> llmTop10_2025() {
        return List.of("LLM01 Prompt Injection", "LLM02 Sensitive Information Disclosure",
                "LLM03 Supply Chain", "LLM04 Data and Model Poisoning", "LLM05 Improper Output Handling",
                "LLM06 Excessive Agency", "LLM07 System Prompt Leakage", "LLM08 Vector and Embedding Weaknesses",
                "LLM09 Misinformation", "LLM10 Unbounded Consumption");
    }

    /**
     * Problemas da linha de base: registro incompleto e problema, nunca
     * lacuna silenciosa. Lista vazia = matriz completa nos termos
     * declarados (pendencias RNF aparecem como campo, nao como problema,
     * porque estao expostas na propria matriz).
     */
    public static List<String> validate() {
        List<String> problems = new ArrayList<>();
        Set<Integer> coveredChapters = new TreeSet<>();
        for (SecurityCase c : SecurityCase.values()) {
            if (c.title() == null || c.title().isBlank() || c.evidence() == null || c.evidence().isBlank()) {
                problems.add("caso " + c.name() + " sem titulo ou sem evidencia declarada");
            }
            coveredChapters.addAll(c.asvsChapters());
        }
        for (AsvsChapter ch : asvsChapters()) {
            if (!coveredChapters.contains(ch.number())) {
                problems.add("capitulo ASVS " + ch.number() + " sem caso da P3.8/LC.8");
            }
            if (ch.rnfRefs() == null || ch.rnfRefs().isBlank()) {
                problems.add("capitulo ASVS " + ch.number() + " sem mapeamento RNF declarado");
            }
        }
        boolean hasL3Auth = componentLevels().stream()
                .anyMatch(c -> c.level() == Level.L3 && c.component().startsWith("Autenticacao"));
        boolean hasL3Pay = componentLevels().stream()
                .anyMatch(c -> c.level() == Level.L3 && c.component().startsWith("Pagamento"));
        boolean hasL3Personal = componentLevels().stream()
                .anyMatch(c -> c.level() == Level.L3 && c.component().startsWith("Dados pessoais"));
        if (!hasL3Auth || !hasL3Pay || !hasL3Personal) {
            problems.add("nivel 3 precisa cobrir autenticacao, pagamento e dados pessoais");
        }
        Set<String> apiCovered = new TreeSet<>();
        for (SecurityCase c : SecurityCase.values()) {
            apiCovered.addAll(c.apiTop10());
        }
        for (String item : apiSecurityTop10_2023()) {
            String code = item.substring(0, 4);
            if (!apiCovered.contains(code)) {
                problems.add("OWASP API Top 10 sem caso: " + item);
            }
        }
        for (String required : List.of("A03", "A10")) {
            boolean covered = false;
            for (SecurityCase c : SecurityCase.values()) {
                covered |= c.owaspTop10().contains(required);
            }
            if (!covered) {
                problems.add("OWASP Top 10:2025 sem caso para a categoria nova " + required);
            }
        }
        // Taxonomia 2025 conferida por codigo E por nome oficial: codigo de
        // caso fora da lista, ou lista fora da oficial, e problema (D1).
        Set<String> officialCodes = new TreeSet<>();
        for (String item : owaspTop10_2025()) {
            officialCodes.add(item.substring(0, 3));
        }
        if (officialCodes.size() != 10) {
            problems.add("OWASP Top 10:2025 precisa ter exatamente os codigos A01 a A10");
        }
        for (SecurityCase c : SecurityCase.values()) {
            for (String code : c.owaspTop10()) {
                if (!officialCodes.contains(code)) {
                    problems.add("caso " + c.name() + " usa codigo fora do OWASP Top 10:2025: " + code);
                }
            }
        }
        return problems;
    }

    /** Matriz requisito-teste-evidencia em JSON, gerada no CI como evidencia da rodada. */
    public static String matrixJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("task", "P3.8");
        root.put("standard", "OWASP ASVS 5.0");
        root.put("levelRule", "L2 em geral; L3 em autenticacao, pagamento e dados pessoais");
        List<Object> chapters = new ArrayList<>();
        for (AsvsChapter ch : asvsChapters()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chapter", ch.number());
            m.put("title", ch.title());
            m.put("rnfRefs", ch.rnfRefs());
            m.put("rnfConfirmada", ch.rnfConfirmada());
            m.put("rnfFonte", ch.rnfFonte());
            List<String> cases = new ArrayList<>();
            for (SecurityCase c : SecurityCase.values()) {
                if (c.asvsChapters().contains(ch.number())) {
                    cases.add(c.name());
                }
            }
            m.put("cases", cases);
            chapters.add(m);
        }
        root.put("asvsChapters", chapters);
        List<Object> cases = new ArrayList<>();
        for (SecurityCase c : SecurityCase.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("case", c.name());
            m.put("title", c.title());
            m.put("sourceTask", c.sourceTask());
            m.put("execution", c.execution().name());
            m.put("inLc8Scope", c.inLc8Scope());
            m.put("evidence", c.evidence());
            m.put("owaspTop10_2025", List.copyOf(c.owaspTop10()));
            m.put("apiTop10_2023", List.copyOf(c.apiTop10()));
            cases.add(m);
        }
        root.put("cases", cases);
        List<Object> components = new ArrayList<>();
        for (ComponentLevel c : componentLevels()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("component", c.component());
            m.put("level", c.level().name());
            m.put("source", c.source());
            components.add(m);
        }
        root.put("componentLevels", components);
        root.put("owaspTop10_2025", owaspTop10_2025());
        root.put("apiSecurityTop10_2023", apiSecurityTop10_2023());
        root.put("llmTop10_2025CriterionFor", "ED-43");
        root.put("llmTop10_2025", llmTop10_2025());
        root.put("problems", validate());
        return Json.write(root);
    }
}
