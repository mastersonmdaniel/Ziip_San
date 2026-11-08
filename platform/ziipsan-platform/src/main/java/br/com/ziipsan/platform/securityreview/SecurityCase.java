package br.com.ziipsan.platform.securityreview;

import java.util.Set;

/**
 * Caso automatizado da revisao de seguranca da homologacao local (P3.8,
 * PLT-08). Cada caso e um item do aceite: ou ele tem teste executavel
 * nomeado, ou a sua execucao e declarada (stack completa no CI, DAST ou
 * criterio de outra tarefa) — caso sem forma de execucao declarada nao
 * entra neste registro (a {@link SecurityBaseline} reprova registro
 * incompleto).
 *
 * <p>Os comportamentos esperados NAO sao inventados aqui: vem das tarefas
 * donas citadas em {@code sourceTask} (P3.3, P3.17, P3.20, P2.26, P0.27 e
 * das regras de isolamento/cofre ja entregues). Este registro so organiza
 * a prova.
 *
 * <p>Os codigos {@code owaspTop10} seguem a semantica OFICIAL do OWASP
 * Top 10:2025 (correcao D1 da revisao cruzada): A02 = Security
 * Misconfiguration, A04 = Cryptographic Failures, A05 = Injection,
 * A06 = Insecure Design, A07 = Authentication Failures, A09 = Security
 * Logging and Alerting Failures — NAO a taxonomia de 2021.
 */
public enum SecurityCase {

    VINCULO("Vinculo do portal: resposta igual para matricula inexistente, codigo errado e documento errado",
            "P3.20 (regras); LinkingCode e SecondCopyService da P3.3",
            Set.of(4, 2), Set.of("A01", "A07"), Set.of(),
            ExecutionKind.AUTOMATED_UNIT_AND_CI_STACK, true,
            "HomologationSecurityPortalTest.vinculoRespostaUniforme sobre o ConsumerLinkService real da P3.20 (unidade) + teste de integracao do vinculo na stack (CI)"),
    ENUMERACAO_MATRICULA("Enumeracao de matricula no vinculo e no portal",
            "P3.20; ADR-0043",
            Set.of(4, 11), Set.of("A01"), Set.of("API1"),
            ExecutionKind.AUTOMATED_UNIT_AND_CI_STACK, true,
            "HomologationSecurityPortalTest: matricula numerica nao tem forma de codigo de vinculacao"),
    ENUMERACAO_CODIGO_VINCULACAO("Enumeracao do codigo de vinculacao",
            "P3.3/P3.20; ADR-0043",
            Set.of(2, 4), Set.of("A07"), Set.of("API6"),
            ExecutionKind.AUTOMATED_UNIT, true,
            "HomologationSecurityPortalTest: codigo de alta entropia (matriculaNaoECodigo), hash cego storageHash/matches (codigoVinculacaoHashCego) e limite por codigo no convidado (convidadoLimitePorCodigo)"),
    ENUMERACAO_LOGIN("Enumeracao de CPF, CNPJ ou e-mail no login: resposta e trabalho iguais",
            "P3.3",
            Set.of(2), Set.of("A07"), Set.of("API2"),
            ExecutionKind.AUTOMATED_UNIT, true,
            "HomologationSecurityPortalTest: inexistente e senha errada sao indistintos; verificacao roda nos dois casos"),
    ENUMERACAO_RECUPERACAO("Enumeracao na recuperacao de senha: mensagem e enfileiramento uniformes",
            "P3.3",
            Set.of(2), Set.of("A07"), Set.of("API2", "API6"),
            ExecutionKind.AUTOMATED_UNIT, true,
            "HomologationSecurityPortalTest: mesma mensagem e mesmo trabalho, exista a conta ou nao"),
    ENUMERACAO_CONVIDADO("Enumeracao do codigo no caminho de convidado (ADR-0043, item 4)",
            "P3.3; ADR-0043 item 4",
            Set.of(4, 11), Set.of("A01"), Set.of("API1", "API6"),
            ExecutionKind.AUTOMATED_UNIT, true,
            "HomologationSecurityPortalTest: inexistente, sem fatura e sufixo errado sao indistintos; 429 no limite; sem sessao"),
    TROCA_TOKEN_REALMS("Troca de token entre realms: portal nao acessa backoffice e vice-versa",
            "P3.3",
            Set.of(3, 4), Set.of("A01", "A07"), Set.of("API5"),
            ExecutionKind.AUTOMATED_UNIT, true,
            "HomologationSecurityPortalTest sobre PortalRealmPolicy nos dois sentidos"),
    DOWNLOAD_PORTAL("Download do portal: so unidade vinculada, com registro de acesso a dado pessoal",
            "P3.3/P3.20",
            Set.of(4, 8, 12), Set.of("A01"), Set.of("API1"),
            ExecutionKind.AUTOMATED_UNIT_AND_CI_STACK, true,
            "HomologationSecurityPortalTest sobre SecondCopyService; registro no personal_data_access_log e da integracao"),
    ACESSO_ASSISTIDO("Acesso assistido: sem consentimento vigente, negado e auditado; revelar/exportar sempre bloqueados",
            "P3.17; ADR-0020",
            Set.of(4, 7), Set.of("A01", "A09"), Set.of(),
            ExecutionKind.AUTOMATED_UNIT, true,
            "HomologationSecurityAccessTest (core): negacao auditada no audit_log do tenant com o consentId"),
    ACESSO_EMERGENCIA("Acesso de emergencia: exige incidente declarado, PLATFORM_OWNER e MFA no momento; escrita 403",
            "P3.17; ADR-0020",
            Set.of(2, 4, 7), Set.of("A01", "A07", "A09"), Set.of(),
            ExecutionKind.AUTOMATED_UNIT, true,
            "HomologationSecurityAccessTest (core): negacao auditada no tenant e na plataforma com o incidentId"),
    DEFESA_AUTOMACAO("Defesa contra automacao da P3.3 (PLT-22): stuffing, limites e ausencia de teste cognitivo sem alternativa",
            "P3.3 (PLT-22); WCAG 2.2 criterio 3.3.8",
            Set.of(2, 11), Set.of("A07"), Set.of("API4", "API6"),
            ExecutionKind.AUTOMATED_UNIT, true,
            "HomologationSecurityPortalTest: ALLOWED_KINDS so tem desafios nao cognitivos; deteccao de stuffing"),
    ISOLAMENTO_TENANTS("Isolamento entre tenants (RLS) e cofre: dado de um tenant nao aparece em outro",
            "P0.27/P0.45; RNF-38 (testes de RLS como evidencia, levantamento 18h)",
            Set.of(1, 4, 6, 8), Set.of("A01", "A04"), Set.of("API1"),
            ExecutionKind.AUTOMATED_CI_STACK, true,
            "Testes de integracao de RLS das tarefas donas na stack (CI); nenhum dublê em memoria substitui esta prova"),
    COFRE("Cofre local: credencial e certificado nao aparecem em log, resposta ou fora do cofre",
            "P0.45/P0.27; ADR-0033",
            Set.of(6, 7, 14), Set.of("A02", "A04"), Set.of("API8"),
            ExecutionKind.AUTOMATED_CI_STACK, true,
            "Conferencia do cofre da P3.7 e varreduras da stack (CI); segredo real nunca entra em fixture"),
    DAST_AUTENTICADA("Varredura DAST autenticada (OWASP ZAP) nos contextos do portal e do backoffice",
            "P3.8; P0.27",
            Set.of(5, 9, 13, 14), Set.of("A02"), Set.of("API8"),
            ExecutionKind.DAST_CI, true,
            "scripts/security/run-dast.sh + zap-plano-autenticado.yaml; relatorio avaliado pelo FindingGate"),
    API_PUBLICA_TOP10("API publica da P2.26 contra o OWASP API Security Top 10 2023",
            "P2.26",
            Set.of(13, 4), Set.of("A01"), Set.of("API1", "API2", "API3", "API4", "API5", "API6", "API7", "API8", "API9", "API10"),
            ExecutionKind.AUTOMATED_CI_STACK, true,
            "Testes de contrato e de seguranca da propria P2.26 na stack (CI); a P3.8 registra o criterio e cobra a evidencia na matriz"),
    CADEIA_SUPRIMENTOS("Falha na cadeia de suprimentos de software (OWASP Top 10:2025 A03)",
            "P3.8; P0.40 (SBOM/licencas, RNF-35)",
            Set.of(10, 14), Set.of("A03"), Set.of(),
            ExecutionKind.AUTOMATED_CI_STACK, false,
            "SBOM e checagens do CI (P0.40) + npm audit do scripts/ci.sh; achado alto bloqueia pelo FindingGate"),
    CONDICOES_EXCEPCIONAIS("Tratamento de condicoes excepcionais (OWASP Top 10:2025 A10)",
            "P3.8",
            Set.of(7, 11), Set.of("A10"), Set.of(),
            ExecutionKind.AUTOMATED_UNIT_AND_CI_STACK, false,
            "Casos de falha fechada desta suite (porta ausente, JSON invalido, relatorio malformado) + testes das tarefas donas"),
    LLM_TOP10_ED43("OWASP Top 10 para LLM 2025 como criterio da ED-43 (registro, nao execucao)",
            "ED-43; ADR-0042",
            Set.of(11, 13), Set.of(), Set.of(),
            ExecutionKind.EXTERNAL_TASK_CRITERION, false,
            "Criterio registrado na matriz; a execucao e da ED-43, fora do escopo da P3.8 e da LC.8 local");

    /** Como o caso e executado — declarado, nunca presumido. */
    public enum ExecutionKind {
        AUTOMATED_UNIT,
        AUTOMATED_UNIT_AND_CI_STACK,
        AUTOMATED_CI_STACK,
        DAST_CI,
        EXTERNAL_TASK_CRITERION
    }

    private final String title;
    private final String sourceTask;
    private final Set<Integer> asvsChapters;
    private final Set<String> owaspTop10;
    private final Set<String> apiTop10;
    private final ExecutionKind execution;
    private final boolean inLc8Scope;
    private final String evidence;

    SecurityCase(String title, String sourceTask, Set<Integer> asvsChapters, Set<String> owaspTop10,
                 Set<String> apiTop10, ExecutionKind execution, boolean inLc8Scope, String evidence) {
        this.title = title;
        this.sourceTask = sourceTask;
        this.asvsChapters = asvsChapters;
        this.owaspTop10 = owaspTop10;
        this.apiTop10 = apiTop10;
        this.execution = execution;
        this.inLc8Scope = inLc8Scope;
        this.evidence = evidence;
    }

    public String title() { return title; }
    public String sourceTask() { return sourceTask; }
    public Set<Integer> asvsChapters() { return asvsChapters; }
    public Set<String> owaspTop10() { return owaspTop10; }
    public Set<String> apiTop10() { return apiTop10; }
    public ExecutionKind execution() { return execution; }
    public boolean inLc8Scope() { return inLc8Scope; }
    public String evidence() { return evidence; }
}
