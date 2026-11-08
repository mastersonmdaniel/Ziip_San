package br.com.ziipsan.platform.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Capacitação da TI do cliente (ED-11): o conteúdo técnico que a equipe de
 * TI do tenant precisa dominar para operar o ZiipSan sem depender da Ziip
 * no dia a dia.
 *
 * <p>Cada tópico está amarrado à tarefa do plano que constrói a função —
 * nenhum tópico existe sem a função existir. Tópicos com prática
 * ({@code handsOn}) só contam como entregues quando exercitados no tenant
 * de pré-treinamento: para TI, ouvir falar de restauração não é saber
 * restaurar. O tópico da instância dedicada é <b>condicional</b>: só entra
 * na régua do tenant cujo contrato a exige (LC.15/ADR-0024) — ele chega
 * marcado como não obrigatório e a conclusão o ignora, a menos que quem
 * avalia o declare requerido pelo contrato (ver o parâmetro de
 * {@link #evaluate}).
 *
 * <p>Esta capacitação é o conteúdo do módulo {@code TI_CLIENTE} do
 * catálogo de treinamento ({@link TrainingPlan#MOD_TI}) e segue a mesma
 * régua de 40 h dele; esta classe cuida do <i>conteúdo</i> (tópicos e
 * práticas), e {@link TrainingCompletion} cuida da entrega (unidades,
 * horas e participantes) do módulo.
 */
public final class ItEnablement {

    private ItEnablement() {
    }

    public record Topic(String key, String title, String sourceRef,
                        boolean mandatory, boolean handsOn) {
        public Topic {
            if (key == null || key.isBlank() || title == null || title.isBlank()
                    || sourceRef == null || sourceRef.isBlank()) {
                throw new IllegalArgumentException(
                        "Tópico da TI exige chave, título e tarefa de origem.");
            }
        }
    }

    public static final String TOPIC_DEDICATED_INSTANCE = "INSTANCIA_DEDICADA";

    public static final List<Topic> TOPICS = List.of(
            new Topic("IDENTIDADE_E_SSO",
                    "Keycloak em produção: organizações, MFA do tenant e SSO (OIDC, SAML, LDAP)",
                    "P0.29, P0.15, ED-06", true, true),
            new Topic("USUARIOS_E_PERFIS",
                    "Gestão de usuários, catálogo de permissões recurso:ação e perfis "
                            + "de fábrica e próprios do tenant",
                    "P0.13, P1.37, ED-06", true, true),
            new Topic("PARAMETROS_DO_TENANT",
                    "Parâmetros do tenant: propor, aprovar e aceitar, vigência futura e "
                            + "pacote de parâmetros aceitos entre homologação e produção",
                    "P0.19, P1.6, ED-09", true, true),
            new Topic("INTEGRACOES",
                    "API pública, webhooks de saída e hub de integrações: credenciais, "
                            + "saúde, reprocessamento e teste de contrato",
                    "P2.26, P3.37", true, true),
            new Topic("BACKUP_E_RESTAURACAO",
                    "Backup e PITR, restauração ensaiada da implantação e restauração "
                            + "seletiva de um tenant",
                    "P0.28, ED-09, P3.33", true, true),
            new Topic("OPERACAO_E_OBSERVABILIDADE",
                    "Runbook de operação, plantão, página pública de status e error tracking",
                    "P3.11, P3.18", true, false),
            new Topic("ACESSO_ASSISTIDO_E_EMERGENCIA",
                    "Acesso assistido com consentimento e acesso de emergência: limites, "
                            + "registro e auditoria",
                    "P3.17, P3.8", true, false),
            new Topic("CERTIFICADO_FISCAL",
                    "Certificado fiscal no cofre do tenant e homologação fiscal a partir "
                            + "do ambiente local",
                    "P1.8, P3.7", true, false),
            new Topic(TOPIC_DEDICATED_INSTANCE,
                    "Instância dedicada (quando o contrato exige): acesso administrativo "
                            + "do órgão somente leitura, com MFA e auditoria",
                    "LC.15, ED-04, ADR-0024", false, false));

    public record ItCompletion(Set<String> deliveredTopicKeys, Set<String> handsOnDoneKeys) {
        public ItCompletion {
            deliveredTopicKeys = deliveredTopicKeys == null ? Set.of() : Set.copyOf(deliveredTopicKeys);
            handsOnDoneKeys = handsOnDoneKeys == null ? Set.of() : Set.copyOf(handsOnDoneKeys);
        }
    }

    public record Result(boolean complete, List<String> failures) {
    }

    /**
     * Avalia a capacitação da TI.
     *
     * @param dedicatedInstanceRequired o contrato do tenant exige instância
     *                                  dedicada (LC.15); sem isso, o tópico
     *                                  condicional não bloqueia
     */
    public static Result evaluate(ItCompletion completion, boolean dedicatedInstanceRequired) {
        if (completion == null) {
            throw new IllegalArgumentException("O registro da capacitação da TI é obrigatório.");
        }
        List<String> failures = new ArrayList<>();
        for (Topic t : TOPICS) {
            boolean required = t.mandatory()
                    || (dedicatedInstanceRequired && TOPIC_DEDICATED_INSTANCE.equals(t.key()));
            if (!required) {
                continue;
            }
            if (!completion.deliveredTopicKeys().contains(t.key())) {
                failures.add("Tópico da TI não entregue: " + t.key() + " (" + t.title() + ")");
                continue;
            }
            if (t.handsOn() && !completion.handsOnDoneKeys().contains(t.key())) {
                failures.add("Tópico da TI sem a prática no tenant de pré-treinamento: "
                        + t.key());
            }
        }
        return new Result(failures.isEmpty(), List.copyOf(failures));
    }
}
