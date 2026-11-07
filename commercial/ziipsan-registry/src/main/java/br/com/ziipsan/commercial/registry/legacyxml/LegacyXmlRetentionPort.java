package br.com.ziipsan.commercial.registry.legacyxml;

/**
 * Costura do importador de legado (P2.19) sobre o armazenamento com
 * retenção imutável da P2.18 (módulo {@code commercial/ziipsan-fiscal},
 * pacote {@code arquivo}: {@code XmlAutorizadoRetencaoService} sobre
 * {@code ArmazenamentoObjetosPort} + {@code HashChainPort}).
 *
 * <p><b>Por que uma porta, e o que a integração precisa decidir:</b> o
 * registry não depende do fiscal no {@code pom.xml} (nem o fiscal do
 * registry) — é o mesmo desenho das portas da P2.6/P2.18. E há uma
 * diferença real a resolver na integração, declarada sem disfarce: o
 * serviço da P2.18 como entregue arquiva a cópia de um documento da P2.6
 * ({@code NfageEmitida} AUTHORIZED, lido do cofre) e a sua guarda de origem
 * só conhece {@code SIMULADOR} e {@code SINTETICO_CERTIFICADO_TESTE} — a
 * origem {@code LEGACY_IMPORT} desta tarefa <b>não existe</b> naquele enum.
 * O adaptador da integração, portanto, ou estende a P2.18 com a origem e o
 * caminho do documento de legado (o desenho esperado: os bytes já chegam
 * validados daqui, não vêm do cofre da P2.6), ou liga esta porta direto às
 * portas internas da P2.18 ({@code ArmazenamentoObjetosPort} com gravação
 * só-se-ausente + {@code HashChainPort}). Nos dois casos, o contrato que
 * ESTA camada exige é o de baixo — e sem implementação ligada, nada é
 * aceito (a porta é obrigatória no construtor do serviço).
 *
 * <p>Contrato: os bytes vão <b>exatamente como recebidos</b> (sem
 * alteração do arquivo — nem re-serialização, nem normalização), sob a
 * origem {@code LEGACY_IMPORT}, e o retorno traz o hash que a retenção
 * registrou, que tem de ser o SHA-256 dos próprios bytes (o serviço
 * confere antes de aceitar o documento).
 */
public interface LegacyXmlRetentionPort {

    /** Origem gravada de todo documento aceito por este importador. */
    String ORIGIN_LEGACY_IMPORT = "LEGACY_IMPORT";

    /**
     * @param chaveObjeto  chave determinística do objeto retido
     * @param uriRetencao  URI da cópia retida
     * @param xmlSha256    SHA-256 registrado pela retenção (64 hex minúsculos)
     * @param hashChain    hash do elo gravado na HashChain para esta cópia
     */
    record RetentionResult(String chaveObjeto, String uriRetencao, String xmlSha256, String hashChain) {

        /**
         * Validação de construção (correção A6 da revisão, padrão do
         * {@code RegistroRetencaoXml} da P2.18): um resultado de retenção
         * incompleto ou com hash fora do formato NUNCA atravessa o
         * serviço — sem ela, {@code null}/vazio viraria documento ACEITO
         * com retenção nula no registro e só estouraria longe da causa,
         * no CHECK da V14_3.
         */
        public RetentionResult {
            if (chaveObjeto == null || chaveObjeto.isBlank()) {
                throw new IllegalArgumentException("Resultado da retenção exige a chave do objeto retido.");
            }
            if (uriRetencao == null || uriRetencao.isBlank()) {
                throw new IllegalArgumentException("Resultado da retenção exige a URI da cópia retida.");
            }
            if (xmlSha256 == null || !xmlSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Resultado da retenção exige xmlSha256 em 64 hex minúsculos.");
            }
            if (hashChain == null || !hashChain.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Resultado da retenção exige o hash do elo da HashChain em 64 hex minúsculos.");
            }
        }
    }

    /**
     * Arquiva os bytes no armazenamento imutável da P2.18.
     *
     * @param tenantId   tenant dono do documento
     * @param accessKey  chave de acesso do documento (44 dígitos)
     * @param xmlBytes   bytes exatos recebidos no lote — sem alteração
     * @param xmlSha256  SHA-256 dos bytes, calculado pelo importador
     * @return o registro da retenção (chave, URI, hash conferido e elo da cadeia)
     */
    RetentionResult arquivarLegado(java.util.UUID tenantId, String accessKey, byte[] xmlBytes, String xmlSha256);
}
