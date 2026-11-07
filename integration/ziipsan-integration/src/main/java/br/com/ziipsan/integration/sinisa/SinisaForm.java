package br.com.ziipsan.integration.sinisa;

/**
 * Formularios do SINISA cobertos pelo mapa da P4.2 (aceite: "mapa das variaveis dos
 * formularios financeiro, tecnico e de balanco").
 *
 * <p>A estrutura segue o Manual de Preenchimento oficial do SINISA (Ministerio das
 * Cidades/SNSA, prestadores de abastecimento de agua): os formularios de Gestao
 * Administrativa e Financeira dividem-se em Receita e Cobranca; Despesas, Investimento
 * e Pessoal; e Balanco Patrimonial; os formularios tecnicos cobrem atendimento,
 * aspectos operacionais, infraestruturas e qualidade da prestacao. O SINISA coleta os
 * componentes agua e esgoto em modulos separados, com formularios proprios por modulo;
 * este enum classifica o formulario dentro do modulo, nao substitui o modulo.
 */
public enum SinisaForm {

    FINANCEIRO("Gestao administrativa e financeira (receita e cobranca; despesas, investimento e pessoal)"),
    TECNICO("Gestao tecnica (atendimento, operacional, infraestruturas e qualidade)"),
    BALANCO("Balanco patrimonial");

    private final String descricao;

    SinisaForm(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}
