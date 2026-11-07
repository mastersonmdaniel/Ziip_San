package br.com.ziipsan.commercial.fiscal.nfage;

/**
 * Fonte versionada dos códigos de rejeição ({@code cStat}) da NFAg-e (modelo 75)
 * que o ZiipSan explica no catálogo de erros da P1.22 (P2.16; ADR-0020).
 *
 * <p>Proveniência das mensagens ({@link #officialMessage()}), conferida na
 * correção pós-revisão da P2.16: MOC da NFAg-e — Visão Geral v1.00l
 * (ago/2026), regras gerais A/B/B-0/C/D/E e regras de consulta/eventos
 * I/J/K/L, e Anexo I (Leiaute e Regras de Validação) v1.00k, regras G.
 * As mensagens são <b>literais</b> do MOC da NFAg-e — que, para vários
 * códigos, difere da tabela da NF-e (ex.: 244, 418, 478, 598, 599, 600).
 * Exceções declaradas: 229–234 (IE do emitente/destinatário) e 999 vêm da
 * tabela de rejeições da NF-e, em substância conferida na revisão — o
 * Anexo I vigente não traz regra ativa com esses {@code cStat} (as regras
 * de IE da NFAg-e são as de DV, 209/427, e as validações cadastrais de IE
 * foram desativadas na v1.00g do Anexo I); para esses códigos NÃO se alega
 * literalidade no MOC da NFAg-e.
 *
 * <p>Remoções pós-revisão (aplicabilidade ao modelo 75, M1): 201, 205, 206,
 * 219 e 225 foram removidos — o MOC vigente da NFAg-e não prevê
 * inutilização de numeração, denegação nem verificação de circulação, e a
 * recepção da NFAg-e é síncrona, sem lote (os números 201/205/206 só
 * aparecem no MOC como códigos de exibição da consulta pública por QR Code,
 * que não são {@code cStat} de rejeição). O 224 foi mantido com a mensagem
 * do MOC: no modelo 75 ele é "NFAg já substituída" (Anexo I, G36), não
 * faixa de inutilização.
 *
 * <p>A explicação voltada ao operador (o que significa, causas e o que
 * fazer) NÃO vive aqui — vive no catálogo da P1.22, em
 * {@code content/help/errors/nfage-<cStat>.md}, sob a chave
 * {@link #catalogCode()} ({@code nfage:<cStat>}, o formato que a V9_3 do
 * core reserva em {@code support.error_explanation}). Código novo entra
 * aqui por alteração deste enum; o teste de cobertura da P2.16
 * ({@code FiscalRejectionCatalogCoverageTest}) falha enquanto o código
 * novo não tiver explicação no catálogo — é esse o portão do aceite.
 */
public enum NfageRejectionCode {

    C203("203", "Emissor não habilitado para emissão da NFAg"),
    C204("204", "Duplicidade de NFAg"),
    C213("213", "CNPJ-Base do Emitente difere do CNPJ-Base do Certificado Digital"),
    C214("214", "Tamanho da mensagem excedeu o limite estabelecido"),
    C215("215", "Falha no schema XML"),
    C216("216", "Chave de Acesso difere da cadastrada"),
    C217("217", "NFAg não consta na base de dados da SEFAZ"),
    C218("218", "NFAg já está cancelada na base de dados da SEFAZ"),
    C224("224", "NFAg já está substituída na base de dados da SEFAZ"),
    C226("226", "Código da UF do Emitente diverge da UF autorizadora"),
    C227("227", "Erro na composição do Campo ID"),
    C228("228", "NFAg com Data-Hora de Emissão muito atrasada"),
    C229("229", "IE do emitente não informada"),
    C230("230", "IE do emitente não cadastrada"),
    C231("231", "IE do emitente não vinculada ao CNPJ/CPF"),
    C232("232", "IE do destinatário não informada"),
    C233("233", "IE do destinatário não cadastrada"),
    C234("234", "IE do destinatário não vinculada ao CNPJ"),
    C236("236", "Chave de Acesso inválida"),
    C239("239", "Versão informada para a NFAg não suportada"),
    C243("243", "XML Malformado"),
    C244("244", "Falha na descompactação da área de dados"),
    C252("252", "Ambiente informado diverge do Ambiente de recebimento"),
    C280("280", "Certificado Transmissor inválido"),
    C281("281", "Certificado Transmissor Data Validade"),
    C282("282", "Certificado Transmissor sem CNPJ"),
    C283("283", "Certificado Transmissor - erro Cadeia de Certificação"),
    C284("284", "Certificado Transmissor revogado"),
    C285("285", "Certificado Transmissor difere ICP-Brasil"),
    C286("286", "Certificado Transmissor erro no acesso a LCR"),
    C290("290", "Certificado Assinatura inválido"),
    C291("291", "Certificado Assinatura Data Validade"),
    C292("292", "Certificado Assinatura sem CNPJ"),
    C293("293", "Certificado Assinatura - erro Cadeia de Certificação"),
    C294("294", "Certificado Assinatura revogado"),
    C295("295", "Certificado Assinatura difere ICP-Brasil"),
    C296("296", "Certificado Assinatura erro no acesso a LCR"),
    C297("297", "Assinatura difere do calculado"),
    C298("298", "Assinatura difere do padrão do Projeto"),
    C402("402", "XML da área de dados com codificação diferente de UTF-8"),
    C404("404", "Uso de prefixo de namespace não permitido"),
    C418("418", "Site de autorização inválido"),
    C478("478", "Consulta a uma Chave de Acesso muito antiga"),
    C539("539", "Duplicidade de NFAg, com diferença na Chave de Acesso"),
    C598("598", "Usar somente o namespace padrão da NFAg"),
    C599("599", "Não é permitida a presença de caracteres de edição no início/fim da mensagem ou entre as tags da mensagem"),
    C600("600", "Chave de Acesso difere da existente em BD"),
    C999("999", "Erro não catalogado");

    private final String code;
    private final String officialMessage;

    NfageRejectionCode(String code, String officialMessage) {
        this.code = code;
        this.officialMessage = officialMessage;
    }

    /** {@code cStat} da rejeição, como devolvido pela SEFAZ (ex.: {@code "204"}). */
    public String code() {
        return code;
    }

    /** Mensagem oficial para este {@code cStat} (proveniência no javadoc da classe). */
    public String officialMessage() {
        return officialMessage;
    }

    /** Chave do catálogo de erros da P1.22: {@code nfage:<cStat>} (formato da V9_3). */
    public String catalogCode() {
        return "nfage:" + code;
    }
}
