package br.com.ziipsan.platform.templates;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * Nome social × nome civil nos documentos (P2.17, CAD-67 sobre a P1.41).
 *
 * <p>Regra da P1.41: {@code social_name} só existe em PF; é usado no
 * atendimento, no portal e nas mensagens; o nome civil é mantido ONDE A
 * LEI EXIGE — no documento fiscal e nos termos. O parâmetro é
 * {@code registry.social_name_display} (classe d), com padrão protetivo de
 * fábrica {@link Mode#SOCIAL_EVERYWHERE_EXCEPT_FISCAL}: nome social em
 * toda interação, exceto documento fiscal. É a mesma regra que o
 * {@code BillHolderResolver} do cadastro aplica à conta ("a conta mostra
 * o nome social cadastrado (PF com social_name) quando
 * {@code registry.social_name_display} é SOCIAL_EVERYWHERE_EXCEPT_FISCAL
 * (padrão); o nome civil só vale onde a lei exige (documento fiscal
 * próprio, P2.8 — fora desta conta)").
 *
 * <p>Para os documentos do motor da P2.30, o "onde a lei exige" desta
 * tarefa é exatamente {@link Context#FISCAL_DOCUMENT} (documento fiscal
 * próprio — ex.: a NFAg-e da P2.6 quando impressa) e {@link Context#TERM}
 * (termos — ex.: termo de parcelamento): neles sai o nome civil. Em todo o
 * resto (conta, comprovantes, avisos, cartas, certidões, OS impressa,
 * laudos, relatórios, e-mail/SMS/WhatsApp) sai o nome social de PF com
 * {@code social_name} cadastrado. PJ não tem nome social nesta regra: sai
 * o nome cadastrado ({@code name} — razão/nome da pessoa jurídica).
 *
 * <p>Classe pura: quem chama resolve os nomes do cadastro (P1.41) e o
 * valor vigente do parâmetro; aqui não há banco. Valor de parâmetro
 * desconhecido é erro explícito — imprimir o nome errado num documento
 * não é um erro silencioso aceitável (mesma postura de
 * {@code HolderNameDisplay.fromParameterValue} da P2.5).
 */
public final class DocumentNamePolicy {

    /** Chave em PARAMETROS-DO-TENANT (P1.41). */
    public static final String PARAMETER_KEY = "registry.social_name_display";

    /** Modo de exibição do nome social. */
    public enum Mode {
        /** Padrão de fábrica (protetivo): social em tudo, exceto fiscal/termos. */
        SOCIAL_EVERYWHERE_EXCEPT_FISCAL,
        /** O tenant desligou o nome social: civil em todo documento. */
        CIVIL_NAME_ONLY;

        /** Padrão de fábrica declarado na P1.41. */
        public static final Mode FACTORY_DEFAULT = SOCIAL_EVERYWHERE_EXCEPT_FISCAL;
    }

    /** Onde o documento se situa quanto à exigência legal do nome civil. */
    public enum Context {
        /** Conta e documentos comuns: vale o nome social (regra geral). */
        ORDINARY,
        /** Documento fiscal próprio: a lei exige o nome civil (CAD-67). */
        FISCAL_DOCUMENT,
        /** Termos (ex.: termo de parcelamento): a lei exige o nome civil. */
        TERM
    }

    private DocumentNamePolicy() {
    }

    /**
     * Lê o valor vigente do parâmetro. Ausente ou em branco = padrão de
     * fábrica. O valor vigente circula como JSON
     * ({@code parameter_versions.value}): {@code "\"CIVIL_NAME_ONLY\""},
     * <b>entre aspas</b> — ele é interpretado pela classe {@link Json} do
     * core, como na regra irmã {@code HolderNameDisplay.fromParameterValue}
     * da P2.5 (correção da revisão cruzada da P2.17: nada de retirar aspas
     * à mão); conteúdo que não é JSON válido lança a
     * {@link IllegalStateException} daquela classe. O texto já decodificado
     * (sem aspas), de chamadores que leem o valor fora do motor de
     * parâmetros, continua aceito; qualquer outro valor é recusado
     * nomeando o valor.
     */
    public static Mode modeFromParameterValue(String value) {
        if (value == null || value.isBlank()) {
            return Mode.FACTORY_DEFAULT;
        }
        String trimmed = value.strip();
        String name = trimmed;
        if (trimmed.startsWith("\"")) {
            JsonNode node = Json.readTree(trimmed);
            if (!node.isTextual()) {
                throw new IllegalArgumentException("valor desconhecido de " + PARAMETER_KEY + ": " + value
                        + " (esperado SOCIAL_EVERYWHERE_EXCEPT_FISCAL ou CIVIL_NAME_ONLY)");
            }
            name = node.asString();
        }
        try {
            return Mode.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("valor desconhecido de " + PARAMETER_KEY + ": " + value
                    + " (esperado SOCIAL_EVERYWHERE_EXCEPT_FISCAL ou CIVIL_NAME_ONLY)", e);
        }
    }

    /** O contexto deste contexto exige o nome civil por lei? */
    public static boolean civilNameLegallyRequired(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context e obrigatorio");
        }
        return context == Context.FISCAL_DOCUMENT || context == Context.TERM;
    }

    /**
     * O nome a imprimir no documento.
     *
     * @param personType  {@code PF} ou {@code PJ} (como no cadastro)
     * @param civilName   nome civil cadastrado ({@code customers.name}); obrigatório
     * @param socialName  nome social cadastrado ({@code customers.social_name}); só PF
     * @param mode        modo vigente de {@code registry.social_name_display}
     * @param context     onde o documento se situa (fiscal/termo × comum)
     */
    public static String printedName(String personType, String civilName, String socialName,
                                     Mode mode, Context context) {
        if (civilName == null || civilName.isBlank()) {
            throw new IllegalArgumentException("nome civil cadastrado e obrigatorio para o documento");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode e obrigatorio (use modeFromParameterValue)");
        }
        if (context == null) {
            throw new IllegalArgumentException("context e obrigatorio");
        }
        String civil = civilName.strip();
        if (mode == Mode.CIVIL_NAME_ONLY || civilNameLegallyRequired(context)) {
            return civil;
        }
        if ("PF".equals(personType) && socialName != null && !socialName.isBlank()) {
            return socialName.strip();
        }
        return civil;
    }
}
