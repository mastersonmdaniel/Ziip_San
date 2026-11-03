package br.com.ziipsan.core.document;

/**
 * Política de máscara de documento de um tenant (P0.11, ADR-0008).
 *
 * <p>O teto é validado pelo sistema, não pela configuração: nenhum padrão pode expor dígitos
 * suficientes para reconstruir o documento combinado com a busca. Para o CPF, no máximo 3
 * dígitos-base ficam visíveis (restam pelo menos 6 desconhecidos, ou seja, pelo menos 1 milhão
 * de candidatos) e nenhum dígito verificador fica visível (cada um reduziria o espaço de busca
 * em cerca de 10 vezes). Subir o teto exige novo ADR.
 *
 * <p>O padrão de fábrica é {@link #DEFAULT} (3 dígitos-base visíveis). O CNPJ mantém o padrão
 * atual (só a ordem do estabelecimento) até a definição do teto próprio, que considera o caso
 * do empresário individual (tarefa derivada do ADR-0008, pendente de decisão do dono): por
 * isso {@link #violatesCap(String)} só verifica, no CNPJ, a exposição de dígito verificador.
 *
 * <p>A política persistida por tenant ainda depende do motor de parâmetros do tenant (P0.19):
 * até lá, o único padrão em vigor no código é o padrão de fábrica, e qualquer caminho futuro
 * de gravação de configuração deve construir a política por este record — o construtor é o
 * ponto de recusa de um teto acima do permitido.
 */
public record MaskPolicy(int visibleCpfBaseDigits) {

    /** Teto do ADR-0008: no máximo 3 dígitos-base do CPF visíveis. */
    public static final int MAX_VISIBLE_CPF_BASE_DIGITS = 3;

    /** Padrão de fábrica: os 3 últimos dígitos-base do CPF (ex.: {@code ***.***.247-**}). */
    public static final MaskPolicy DEFAULT = new MaskPolicy(MAX_VISIBLE_CPF_BASE_DIGITS);

    public MaskPolicy {
        if (visibleCpfBaseDigits < 0 || visibleCpfBaseDigits > MAX_VISIBLE_CPF_BASE_DIGITS) {
            throw new IllegalArgumentException(
                    "teto de máscara recusado (ADR-0008): o CPF pode expor no máximo "
                            + MAX_VISIBLE_CPF_BASE_DIGITS + " dígitos-base, sem dígito verificador; pedido: "
                            + visibleCpfBaseDigits);
        }
    }

    /**
     * Verdadeiro se uma máscara gravada viola o teto: CPF com mais de 3 dígitos-base visíveis
     * ou com dígito verificador visível; CNPJ com dígito verificador visível. Formas que não
     * são máscara de CPF (11 posições) nem de CNPJ (14 posições) não são julgáveis por esta
     * regra e devolvem falso (ex.: "documento inválido", sem dígito algum).
     */
    public static boolean violatesCap(String masked) {
        if (masked == null) {
            return false;
        }
        String compact = masked.replace(".", "").replace("-", "").replace("/", "");
        if (compact.length() == 11) {
            return violatesCpfMask(compact);
        }
        if (compact.length() == 14) {
            return compact.charAt(12) != '*' || compact.charAt(13) != '*';
        }
        return false;
    }

    private static boolean violatesCpfMask(String compact) {
        if (compact.charAt(9) != '*' || compact.charAt(10) != '*') {
            return true;
        }
        int visibleBase = 0;
        for (int i = 0; i < 9; i++) {
            if (compact.charAt(i) != '*') {
                visibleBase++;
            }
        }
        return visibleBase > MAX_VISIBLE_CPF_BASE_DIGITS;
    }
}
