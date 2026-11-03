package br.com.ziipsan.platform.console;

import java.util.List;

/**
 * Checklist de implantação (P1.6, plano v3.0 linha 634): os 9 passos que o
 * painel do tenant mostra sobre {@code platform.onboarding_step} e que são
 * a fonte do gate automático de pronto para produção.
 *
 * <p>Fonte do conteúdo: o manifesto do pacote de vertical (ADR-0037), seção
 * {@code onboarding} de {@code packages/sanitation/vertical.json}. Esta lista
 * NÃO é uma segunda fonte inventada: o teste de contrato
 * {@code OnboardingManifestTest} quebra se ela divergir do manifesto (chave
 * e título, na mesma ordem). O manifesto ainda não viaja dentro do jar da
 * aplicação, por isso o seed em runtime lê daqui e o teste garante a
 * igualdade com o manifesto — carregar o arquivo em runtime sem ele estar
 * empacotado seria fingir uma fonte que não existe no deploy.
 */
public final class OnboardingChecklist {

    /** Um passo do checklist: chave estável (coluna step_key) e título exibido. */
    public record Step(String key, String title) {
    }

    public static final List<Step> STEPS = List.of(
            new Step("parameters_accepted", "Parâmetros pendentes de aceite"),
            new Step("import_reconciliation", "Importação e conciliação"),
            new Step("fiscal_certificate", "Certificado fiscal"),
            new Step("psp_test", "Teste com o PSP"),
            new Step("mfa_policy", "Política de MFA"),
            new Step("restore_rehearsal", "Restauração ensaiada"),
            new Step("dpo_informed", "Encarregado informado"),
            new Step("dpa_signed", "DPA assinado"),
            new Step("training", "Treinamento"));

    private OnboardingChecklist() {
    }
}
