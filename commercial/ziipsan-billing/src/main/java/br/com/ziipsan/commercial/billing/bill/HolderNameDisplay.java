package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * Nome impresso na conta: parametro {@code bill.holder_name_display} (classe b,
 * catalogo da V4_4; decisao do dono de 28/10/2026, item 6; NR 11, art. 33,
 * paragrafo 2).
 *
 * <p>O padrao de fabrica e o mais protetivo, {@link #USER_HOLDER_ONLY}: so o
 * usuario titular aparece. {@link #USER_HOLDER_AND_OWNER} acrescenta o
 * proprietario e so vale com aceite formal do tenant fora do padrao (classe b) --
 * quem valida o aceite e a gravacao do parametro (P1.6); aqui, qualquer valor
 * fora dos dois e recusado, nunca interpretado por aproximacao.
 */
public enum HolderNameDisplay {

    USER_HOLDER_ONLY,
    USER_HOLDER_AND_OWNER;

    /** Chave em PARAMETROS-DO-TENANT. */
    public static final String PARAMETER_KEY = "bill.holder_name_display";

    /** Padrao de fabrica (o mesmo da definicao no catalogo, V4_4). */
    public static final HolderNameDisplay FACTORY_DEFAULT = USER_HOLDER_ONLY;

    /**
     * Le o valor vigente do parametro. Ausente ou em branco = padrao de fabrica
     * (parametro ainda nao proposto pelo tenant). Valor desconhecido e erro
     * explicito: imprimir o nome errado na conta nao e um erro silencioso aceitavel.
     *
     * <p>O valor vigente circula como JSON ({@code parameter_versions.value}):
     * {@code "\"USER_HOLDER_AND_OWNER\""}, <b>entre aspas</b>. Ele e interpretado
     * pela classe {@link Json} do core, como nas regras irmaos
     * ({@code MeteringParameters}, {@code PartialPeriodRule}); conteudo que nao
     * e JSON valido lanca {@link IllegalStateException} daquela classe. O texto
     * ja decodificado (sem aspas), de chamadores que leem o valor fora do motor
     * de parametros, continua aceito.
     */
    public static HolderNameDisplay fromParameterValue(String value) {
        if (value == null || value.isBlank()) {
            return FACTORY_DEFAULT;
        }
        String trimmed = value.trim();
        String name = trimmed;
        if (trimmed.startsWith("\"")) {
            JsonNode node = Json.readTree(trimmed);
            if (!node.isTextual()) {
                throw new IllegalArgumentException("valor desconhecido de " + PARAMETER_KEY + ": " + value
                        + " (esperado USER_HOLDER_ONLY ou USER_HOLDER_AND_OWNER)");
            }
            name = node.asText();
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("valor desconhecido de " + PARAMETER_KEY + ": " + value
                    + " (esperado USER_HOLDER_ONLY ou USER_HOLDER_AND_OWNER)", e);
        }
    }

    /**
     * Compoe o nome impresso a partir do nome do usuario titular (o do snapshot da
     * conta, DAD-15) e, na opcao ampliada, do proprietario.
     *
     * @param userHolderName nome do usuario titular; obrigatorio nas duas opcoes
     * @param ownerName      nome do proprietario; so entra em {@link #USER_HOLDER_AND_OWNER}
     *                       e, ausente/em branco ou igual ao do titular, o impresso e
     *                       so o do titular (nunca um separador pendurado nem nome repetido)
     */
    public String printedName(String userHolderName, String ownerName) {
        if (userHolderName == null || userHolderName.isBlank()) {
            throw new IllegalArgumentException("nome do usuario titular e obrigatorio para o nome impresso");
        }
        String holder = userHolderName.trim();
        if (this == USER_HOLDER_ONLY || ownerName == null || ownerName.isBlank()
                || ownerName.trim().equals(holder)) {
            return holder;
        }
        return holder + " / " + ownerName.trim();
    }
}
