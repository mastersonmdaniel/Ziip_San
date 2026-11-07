package br.com.ziipsan.commercial.fiscal.nfse;

/**
 * Fonte versionada dos códigos de rejeição da NFS-e no padrão nacional
 * (códigos {@code E....} da DPS/NFS-e) que o ZiipSan explica no catálogo de
 * erros da P1.22 (P2.16; ADR-0020).
 *
 * <p>A NFS-e nacional tem tabela própria de rejeições, distinta da NF-e: os
 * códigos começam por {@code E} e são definidos na documentação do Sistema
 * Nacional da NFS-e (anexos e notas técnicas do CGNFS-e). A
 * {@link #officialMessage()} resume a mensagem oficial do código; a explicação
 * voltada ao operador NÃO vive aqui — vive no catálogo da P1.22, em
 * {@code content/help/errors/nfse-<código>.md}, sob a chave
 * {@link #catalogCode()} ({@code nfse:<código>}, o formato que a V9_3 do core
 * reserva em {@code support.error_explanation}).
 *
 * <p><b>Limite declarado (sem dado inventado):</b> a tabela completa do Sistema
 * Nacional (que inclui as faixas da Reforma Tributária, E0854–E1607, e os
 * códigos técnicos de leiaute/assinatura) não está versionada neste
 * repositório. Este enum cobre os códigos de negócio conferidos na
 * documentação pública do padrão nacional aplicáveis ao fluxo do ZiipSan
 * (DPS do serviço avulso da P3.6 e, adiante, da conta): competência, cadastro
 * municipal/CNC, retenção, substituição e os códigos de IBS/CBS já vigentes
 * (E0322, E0854, E0958). Código novo entra aqui por alteração deste enum; o
 * teste de cobertura da P2.16 ({@code FiscalRejectionCatalogCoverageTest})
 * falha enquanto o código novo não tiver explicação no catálogo.
 *
 * <p><b>Correção pós-revisão (P2.16):</b> acrescentados E0020, E0025, E0038,
 * E0039, E0046, E0050, E0056 e E0061, com mensagens conferidas nas centrais
 * públicas que reproduzem os anexos do Sistema Nacional (TecnoSpeed e
 * NotaPlano). Ressalva de literalidade: a mensagem do E0025 é a do texto
 * oficial com <b>correção ortográfica declarada</b> da transcrição pública
 * consultada ("autorizasção de uso do emissores" → "autorização de uso dos
 * emissores") — adaptação declarada, não literalidade alegada.
 */
public enum NfseRejectionCode {

    E0010("E0010", "A série da DPS está fora da faixa do canal"),
    E0015("E0015", "A data de competência está no futuro (posterior à data de emissão)"),
    E0016("E0016", "A data de competência é anterior à entrada do município no Sistema Nacional"),
    E0018("E0018", "A data de competência é anterior à abertura do CNPJ"),
    E0020("E0020", "A data de competência informada na DPS deve ser igual ou posterior à data de inscrição do CPF do emitente no cadastro CPF"),
    E0023("E0023", "A data de competência deve ser igual ou posterior à data do indicador municipal registrada no CNC do município"),
    E0025("E0025", "A data de competência informada na DPS deve ser igual ou posterior à data de autorização de uso dos emissores, registrada no CNC do município correspondente ao município emissor da DPS (cLocEmi) para o contribuinte"),
    E0029("E0029", "O motivo da emissão não pode ser preenchido se o emitente for o prestador de serviço"),
    E0031("E0031", "Não pode haver retenção do ISSQN pelo tomador quando o município de incidência não for o do tomador"),
    E0032("E0032", "Não pode haver retenção do ISSQN pelo intermediário quando o município de incidência não for o do intermediário"),
    E0034("E0034", "Somente é permitido informar a chave de acesso de NFS-e rejeitada se o emitente for tomador ou intermediário e o motivo da emissão for por rejeição"),
    E0037("E0037", "O código do município emissor informado na DPS é inexistente no cadastro de convênio municipal do Sistema Nacional"),
    E0038("E0038", "A situação do convênio do município emissor informado na DPS deve ser \"ATIVO\" no cadastro de convênio municipal do sistema nacional"),
    E0039("E0039", "O município emissor informado na DPS deve estar parametrizado para utilizar os emissores públicos nacionais, conforme parametrização do município no Sistema Nacional NFS-e"),
    E0046("E0046", "Uma NFS-e cancelada não pode ser substituída. Informe uma chave de NFS-e não cancelada anteriormente."),
    E0050("E0050", "Uma NFS-e não pode ser substituída fora do prazo estabelecido pelo município emissor da NFS-e"),
    E0056("E0056", "NFS-e não pode ser substituída pois não possui identificação do tomador"),
    E0058("E0058", "Não poderá ocorrer a substituição de NFS-e com alteração da identificação do não emitente, conforme parametrização do município emissor"),
    E0060("E0060", "Na substituição, data de competência, subitem da lista nacional de serviços, código complementar municipal e local da prestação não podem ser alterados"),
    E0061("E0061", "Os campos identificação do Tomador (se identificado na DPS), data de competência (dCompet), e valor do serviço (vServ), não podem ser alterados quando a opção do simples nacional for MEI (opSimpNac = 2) ou ME/EPP (opSimpNac = 3)"),
    E0065("E0065", "Não é possível a substituição de NFS-e gerada em ambientes geradores diferentes"),
    E0068("E0068", "Não é possível a substituição, pois a NFS-e possui Solicitação de Análise Fiscal para Cancelamento aguardando resposta"),
    E0070("E0070", "Não é possível a substituição, pois já ocorreu a manifestação de confirmação do serviço"),
    E0072("E0072", "Não é possível a substituição, pois já ocorreu a manifestação tácita da NFS-e"),
    E0074("E0074", "Não é permitida a substituição de NFS-e que possua Evento de Tributos Recolhidos vinculado, conforme parametrização do município de incidência do ISSQN"),
    E0076("E0076", "Não é permitida a substituição de NFS-e que possua Evento de Bloqueio de Ofício para o Cancelamento por Substituição vigente"),
    E0084("E0084", "O Sistema Nacional não reconhece o prestador como estabelecido no município emissor na data da nota"),
    E0322("E0322", "É obrigatório informar na DPS um item da NBS se for declarada qualquer informação de IBS/CBS"),
    E0854("E0854", "As informações de IBS/CBS exigem a DPS na versão de leiaute que as suporta (versão 1.01)"),
    E0958("E0958", "O código de classificação tributária informado não é compatível com uma operação de prestação de serviços");

    private final String code;
    private final String officialMessage;

    NfseRejectionCode(String code, String officialMessage) {
        this.code = code;
        this.officialMessage = officialMessage;
    }

    /** Código da rejeição no padrão nacional (ex.: {@code "E0010"}). */
    public String code() {
        return code;
    }

    /** Mensagem oficial da documentação do Sistema Nacional para este código. */
    public String officialMessage() {
        return officialMessage;
    }

    /** Chave do catálogo de erros da P1.22: {@code nfse:<código>} (formato da V9_3). */
    public String catalogCode() {
        return "nfse:" + code;
    }
}
