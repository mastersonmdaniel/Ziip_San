package br.com.ziipsan.commercial.fiscal.arquivo;

import br.com.ziipsan.commercial.fiscal.nfage.NfageEmissionService;
import br.com.ziipsan.commercial.fiscal.nfage.NfageProductionGuard;
import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;
import br.com.ziipsan.core.json.Json;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Arquivamento com retenção imutável da cópia do XML <b>autorizado</b> da
 * NFAg-e (P2.18; ADR-0003; ADR-0022) — a cópia de retenção do documento da
 * P2.6 em estado {@link NfageStatus#AUTHORIZED}.
 *
 * <p><b>Desenho (como a retenção é garantida no ambiente local):</b>
 *
 * <ol>
 *   <li>a cópia é gravada no emulador de S3 local <b>pela interface da
 *       P0.45</b> (costura {@link ArmazenamentoObjetosPort}), sob uma chave
 *       determinística por tenant + chave de acesso, com <b>gravação só se
 *       ausente na semântica da porta</b> — sobrescrever não é uma operação
 *       que este serviço ofereça. <b>Precisão sobre a interface real</b>
 *       (lida na revisão cruzada): a {@code ObjectStorage} da P0.45 é só
 *       {@code put(bucket,key,InputStream,contentType)→void},
 *       {@code get}, {@code exists} e {@code delete} — <b>não há gravação
 *       condicional nem atômica, não há metadados e não há retorno de
 *       URI</b>. O adaptador da porta compõe {@code exists} + {@code put}
 *       (check-then-put): a <b>corrida check-then-put é um fato conhecido</b>
 *       e o seu tratamento (serializar por documento/chave ou aceitar e
 *       tratar o conflito detectado) é <b>decisão pendente nomeada da
 *       integração</b> — esta camada não promete atomicidade que a
 *       interface não dá. Quando o adaptador detecta a ocupação, ele lança
 *       {@code ObjetoJaGravadoException} e este serviço a traduz em recusa
 *       de sobrescrita (ramo coberto por teste);</li>
 *   <li>o SHA-256 do XML é <b>registrado na {@code HashChain}</b> do projeto
 *       (costura {@link HashChainPort}) no ato do arquivamento, e o hash do
 *       elo volta ao registro de retenção;</li>
 *   <li><b>apagar é recusado sempre</b> por {@link #solicitarRemocao}: a
 *       recusa acontece nesta camada, antes de qualquer delegação — o método
 *       de remoção da interface de armazenamento nunca é chamado sobre objeto
 *       retido (os testes contam as chamadas no duplo). Em produção, o S3 com
 *       Object Lock da LC.5 impõe o mesmo bloqueio no provedor;</li>
 *   <li><b>a leitura confere exatamente duas coisas</b>:
 *       {@link #lerConferido} recalcula o SHA-256 dos bytes lidos e o compara
 *       com o <b>registro de retenção</b> (bytes × SHA do registro), e
 *       compara o elo devolvido pela cadeia com o elo gravado no
 *       <b>registro</b> (coerência registro × elo). É só isso que é provado:
 *       a porta da cadeia ({@link HashChainPort#consultar}) <b>não recebe o
 *       hash</b>, logo <b>não há conferência elo × conteúdo</b> (elo ×
 *       SHA-256 recalculado) nesta camada — ver a pendência nomeada na
 *       porta. Qualquer divergência é {@link IntegridadeXmlException}
 *       explícita e os bytes suspeitos <b>não</b> são devolvidos;</li>
 *   <li><b>guarda de origem</b>: no ambiente local só entram XML do simulador
 *       ou sintéticos com certificado de teste, em ambiente
 *       {@code SIMULATED} do dicionário. XML da homologação com o e-CNPJ da
 *       Ziip/TGP — que são apagados ao fim de cada ciclo (P3.7) —, ambiente de
 *       homologação e ambiente de produção são recusados antes de qualquer
 *       leitura ou gravação ({@link OrigemXmlRecusadaException}). <b>Ressalva
 *       de precedência:</b> antes das recusas próprias desta guarda, roda o
 *       {@code NfageProductionGuard} da P2.6 — um contexto incoerente para
 *       ele (ex.: {@code PRODUCTION} sem habilitação explícita, ou
 *       {@code HOMOLOGATION} com {@code tpAmb} incoerente) é recusado com o
 *       {@code IllegalStateException} <b>do guard</b>, não com
 *       {@code OrigemXmlRecusadaException}. Os dois caminhos são
 *       fail-closed e anteriores a qualquer leitura.</li>
 * </ol>
 *
 * <p><b>Não-atomicidade declarada (sem transação distribuída fingida):</b>
 * arquivar é a sequência gravar → encadear → registrar, <b>sem atomicidade
 * entre os três passos</b>. Uma falha no meio (cadeia ou store lançando
 * depois da gravação) deixa um <b>objeto órfão</b>: objeto presente no
 * armazenamento sem registro conhecido. Esse estado é tratado de forma
 * explícita e segura nesta camada: a tentativa seguinte cai no ramo
 * "objeto órfão sem registro" e é recusada com erro claro e diagnosticável
 * ({@link RetencaoImutavelException}) — <b>nunca</b> se sobrescreve nem se
 * arquiva por cima de conteúdo desconhecido. A <b>reconciliação</b> dos
 * órfãos (identificar, diagnosticar e resolver cada um) é <b>pendência
 * nomeada da integração</b>, não deste serviço.
 *
 * <p><b>Antes de arquivar, a integridade da origem é conferida:</b> os bytes
 * são lidos da área do cofre pela URI que a assinatura da P2.6 devolveu
 * ({@link XmlAssinadoLeituraPort}) e o SHA-256 calculado tem de ser igual ao
 * {@code xml_sha256} do documento; divergência aborta o arquivamento sem
 * gravar nada e sem tocar a cadeia.
 *
 * <p><b>Idempotência e sobrescrita:</b> idempotente é <b>só a repetição
 * idêntica</b> — rearquivar o mesmo documento, com o mesmo hash, a mesma
 * origem declarada e o mesmo {@code retidoAte} devolve o registro existente
 * (sem nova gravação e sem novo elo). Rearquivar a mesma chave de acesso
 * com hash diferente é tentativa de sobrescrita e é recusada
 * ({@link RetencaoImutavelException}); rearquivar o idêntico com origem ou
 * {@code retidoAte} <b>divergentes</b> do registro também é recusado com
 * erro explícito (o registro original manda; divergência não é absorvida
 * em silêncio). Objeto presente no armazenamento sem registro conhecido
 * (órfão) também fecha a porta — nada se sobrescreve sobre conteúdo
 * desconhecido.
 *
 * <p><b>Fora de escopo (P3.3):</b> segunda via do documento e Pix em
 * contingência estática não são tratados aqui (PLT-40).
 */
public final class XmlAutorizadoRetencaoService {

    /** Prefixo das chaves de retenção no armazenamento de objetos. */
    public static final String PREFIXO_CHAVE = "retencao/nfage/";

    /**
     * @param documento documento fiscal da P2.6 (tem de estar AUTHORIZED, com
     *                  a URI e o SHA-256 do XML assinado da assinatura/cofre)
     * @param origem    origem declarada do XML (guarda de origem do aceite)
     * @param ambiente  contexto de ambiente do dicionário (P2.6) do documento
     * @param retidoAte fim do bloqueio de retenção, definido pela configuração
     *                  de quem chama — nenhum prazo legal é presumido aqui
     */
    public record ComandoArquivamento(NfageEmissionService.NfageEmitida documento,
                                      OrigemXmlAutorizado origem,
                                      NfageProductionGuard.ContextoAmbiente ambiente,
                                      Instant retidoAte) {
    }

    private final Clock clock;
    private final ArmazenamentoObjetosPort armazenamento;
    private final HashChainPort hashChain;
    private final XmlAssinadoLeituraPort leituraCofre;
    private final ArquivoRetencaoStore store;

    public XmlAutorizadoRetencaoService(Clock clock, ArmazenamentoObjetosPort armazenamento,
                                        HashChainPort hashChain, XmlAssinadoLeituraPort leituraCofre,
                                        ArquivoRetencaoStore store) {
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
        this.armazenamento = Objects.requireNonNull(armazenamento,
                "Armazenamento de objetos da P0.45 é obrigatório (sem ele, nada se arquiva)");
        this.hashChain = Objects.requireNonNull(hashChain,
                "HashChain é obrigatória (sem ela, nada se arquiva)");
        this.leituraCofre = Objects.requireNonNull(leituraCofre,
                "Leitura do XML assinado no cofre é obrigatória");
        this.store = Objects.requireNonNull(store, "Porta de registros de retenção é obrigatória");
    }

    /** Chave determinística da cópia retida de um documento. */
    public static String chaveObjeto(UUID tenantId, String accessKey) {
        return PREFIXO_CHAVE + tenantId + "/" + accessKey + ".xml";
    }

    /**
     * Arquiva a cópia retida do XML autorizado, registrando o SHA-256 na
     * {@code HashChain}. Idempotente <b>só para a repetição idêntica</b>
     * (mesmo documento, mesmo hash, mesma origem e mesmo {@code retidoAte}).
     *
     * @throws IllegalStateException      se o documento não estiver AUTHORIZED
     *                                    ou sem URI/hash do XML assinado; ou
     *                                    se o {@code NfageProductionGuard} da
     *                                    P2.6 recusar o contexto de ambiente
     *                                    (precedência dele sobre a guarda
     *                                    própria — ver o javadoc da classe)
     * @throws OrigemXmlRecusadaException se a guarda de origem própria
     *                                    recusar o XML
     * @throws IntegridadeXmlException    se os bytes do cofre divergirem do
     *                                    {@code xml_sha256} do documento
     * @throws RetencaoImutavelException  se a chave já retiver outro conteúdo
     *                                    (tentativa de sobrescrita), se houver
     *                                    objeto órfão sem registro na chave,
     *                                    se a corrida check-then-put for
     *                                    detectada na gravação, ou se o
     *                                    rearquivamento divergir em origem
     *                                    ou {@code retidoAte} do registro
     */
    public RegistroRetencaoXml arquivar(ComandoArquivamento cmd) {
        Objects.requireNonNull(cmd, "Comando de arquivamento é obrigatório");
        NfageEmissionService.NfageEmitida doc = Objects.requireNonNull(
                cmd.documento(), "Documento fiscal do arquivamento é obrigatório");
        validarDocumentoAutorizado(doc);
        exigirOrigemLocalPermitida(cmd);
        Instant agora = clock.instant();
        if (cmd.retidoAte() == null || !cmd.retidoAte().isAfter(agora)) {
            throw new IllegalArgumentException(
                    "Fim da retenção (retidoAte) é obrigatório e tem de ser posterior ao arquivamento");
        }

        // Sobrescrita e idempotência decidem-se ANTES de ler ou gravar qualquer byte.
        String chave = chaveObjeto(doc.tenantId(), doc.accessKey());
        Optional<RegistroRetencaoXml> existente = store.buscar(doc.tenantId(), doc.accessKey());
        if (existente.isPresent()) {
            RegistroRetencaoXml registroExistente = existente.get();
            if (!registroExistente.xmlSha256().equals(doc.xmlSha256())) {
                throw new RetencaoImutavelException(
                        "Sobrescrita recusada: a chave de acesso " + doc.accessKey()
                                + " já tem cópia retida com outro SHA-256 (retenção imutável, P2.18)");
            }
            // Idempotente é só a repetição IDÊNTICA: mesmo hash com origem ou
            // retidoAte divergentes do registro não é absorvido em silêncio —
            // o registro original manda e a divergência é erro explícito.
            if (registroExistente.origem() != cmd.origem()
                    || !registroExistente.retidoAte().equals(cmd.retidoAte())) {
                throw new RetencaoImutavelException(
                        "Rearquivamento recusado: a chave de acesso " + doc.accessKey()
                                + " já tem cópia retida com origem/retidoAte divergentes do pedido"
                                + " (registrado: origem " + registroExistente.origem()
                                + ", retidoAte " + registroExistente.retidoAte()
                                + "; pedido: origem " + cmd.origem()
                                + ", retidoAte " + cmd.retidoAte()
                                + ") — idempotente é só a repetição idêntica (P2.18)");
            }
            return registroExistente;
        }
        if (armazenamento.existe(chave)) {
            // Objeto ÓRFÃO: presente sem registro conhecido. Pode ser resíduo
            // de uma falha parcial entre gravar → encadear → registrar (a
            // sequência não é atômica — ver o javadoc da classe). O tratamento
            // desta camada é explícito e seguro: erro diagnosticável, nunca
            // sobrescrever nem arquivar por cima. A reconciliação do órfão é
            // pendência nomeada da integração.
            throw new RetencaoImutavelException(
                    "Arquivamento recusado: objeto órfão na chave de retenção " + chave
                            + " — objeto presente sem registro conhecido (possível falha parcial"
                            + " entre gravar, encadear e registrar). Nada foi sobrescrito nem"
                            + " arquivado por cima; a reconciliação deste órfão é processo"
                            + " explícito da integração (P2.18)");
        }

        // Integridade na origem: os bytes do cofre têm de ser o XML cujo hash o
        // documento declara. Divergência aborta sem gravar e sem tocar a cadeia.
        byte[] xml = Objects.requireNonNull(
                leituraCofre.lerXmlAssinado(doc.xmlStorageUri()),
                "Leitura do cofre devolveu XML nulo para a URI " + doc.xmlStorageUri());
        String shaCalculado = Sha256.deBytes(xml);
        if (!shaCalculado.equals(doc.xmlSha256())) {
            throw new IntegridadeXmlException(
                    "SHA-256 dos bytes lidos do cofre (" + shaCalculado
                            + ") diverge do xml_sha256 do documento (" + doc.xmlSha256()
                            + ") — arquivamento abortado, nada foi gravado");
        }

        Map<String, String> metadados = new LinkedHashMap<>();
        metadados.put("sha-256", shaCalculado);
        metadados.put("chave-acesso", doc.accessKey());
        metadados.put("tenant-id", doc.tenantId().toString());
        metadados.put("documento-id", doc.id().toString());
        metadados.put("retido-ate", cmd.retidoAte().toString());
        metadados.put("origem", cmd.origem().name());
        metadados.put("content-type", "application/xml");

        String uriRetencao;
        try {
            uriRetencao = armazenamento.gravarSeAusente(chave, xml, metadados);
        } catch (ArmazenamentoObjetosPort.ObjetoJaGravadoException e) {
            // Tradução da corrida check-then-put: entre o existe() acima e a
            // gravação, a chave foi ocupada (por outro processo ou detectada
            // pelo adaptador sobre a ObjectStorage real, que não tem gravação
            // atômica — ver ArmazenamentoObjetosPort). O desfecho para quem
            // chamou é o mesmo — sobrescrita recusada, nada gravado por cima.
            throw new RetencaoImutavelException(
                    "Sobrescrita recusada: corrida check-then-put detectada na gravação da chave "
                            + chave + " — a chave foi ocupada entre a conferência de existência"
                            + " e a gravação (ObjetoJaGravadoException traduzida; retenção"
                            + " imutável, P2.18)");
        }

        String elo = hashChain.registrar(chave, shaCalculado);
        if (!Sha256.formatoValido(elo)) {
            throw new IllegalStateException(
                    "HashChain devolveu hash de elo fora do formato SHA-256 (64 hex minúsculos)");
        }

        Map<String, Object> retrato = new LinkedHashMap<>();
        retrato.put("tarefa", "P2.18");
        retrato.put("documentoId", doc.id().toString());
        retrato.put("chaveAcesso", doc.accessKey());
        retrato.put("chaveObjeto", chave);
        retrato.put("uriRetencao", uriRetencao);
        retrato.put("xmlSha256", shaCalculado);
        retrato.put("hashChain", elo);
        retrato.put("origem", cmd.origem().name());
        retrato.put("ambiente", cmd.ambiente().ambiente().name());
        retrato.put("retidoAte", cmd.retidoAte().toString());
        retrato.put("arquivadoEm", agora.toString());

        RegistroRetencaoXml registro = new RegistroRetencaoXml(
                UUID.randomUUID(), doc.tenantId(), doc.id(), doc.accessKey(), chave,
                uriRetencao, shaCalculado, elo, cmd.origem(),
                cmd.ambiente().ambiente().name(), cmd.retidoAte(), agora, Json.write(retrato));
        store.registrar(registro);
        return registro;
    }

    /**
     * Lê a cópia retida conferindo exatamente: (1) bytes × SHA-256 do
     * <b>registro</b> de retenção (hash recalculado dos bytes lidos) e
     * (2) coerência <b>registro × elo</b> (elo consultado na cadeia igual ao
     * elo do registro). A porta da cadeia não recebe o hash, portanto esta
     * leitura <b>não</b> confere elo × conteúdo (elo × SHA-256 recalculado).
     *
     * @return bytes exatos do XML arquivado, já conferidos
     * @throws IllegalStateException   se o documento não tiver cópia retida ou
     *                                 o objeto tiver sumido do armazenamento
     * @throws IntegridadeXmlException se qualquer das duas conferências acima
     *                                 divergir, ou se o elo estiver ausente na
     *                                 cadeia — os bytes suspeitos nunca são
     *                                 devolvidos
     */
    public byte[] lerConferido(UUID tenantId, String accessKey) {
        Objects.requireNonNull(tenantId, "Tenant da leitura é obrigatório");
        Objects.requireNonNull(accessKey, "Chave de acesso da leitura é obrigatória");
        RegistroRetencaoXml registro = store.buscar(tenantId, accessKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Documento sem cópia retida neste armazenamento (chave de acesso "
                                + accessKey + ")"));
        byte[] xml = armazenamento.ler(registro.chaveObjeto())
                .orElseThrow(() -> new IllegalStateException(
                        "Objeto retido sumiu do armazenamento: " + registro.chaveObjeto()
                                + " — retenção violada fora deste serviço, investigar"));
        String shaCalculado = Sha256.deBytes(xml);
        if (!shaCalculado.equals(registro.xmlSha256())) {
            throw new IntegridadeXmlException(
                    "SHA-256 da leitura (" + shaCalculado + ") diverge do registro de retenção ("
                            + registro.xmlSha256() + ") — XML suspeito não é devolvido");
        }
        String elo = hashChain.consultar(registro.chaveObjeto())
                .orElseThrow(() -> new IntegridadeXmlException(
                        "Registro da HashChain não encontrado para " + registro.chaveObjeto()
                                + " — leitura recusada sem a conferência da cadeia"));
        if (!elo.equals(registro.hashChain())) {
            throw new IntegridadeXmlException(
                    "Elo da HashChain diverge do registro de retenção para "
                            + registro.chaveObjeto() + " — leitura recusada");
        }
        return xml;
    }

    /**
     * Pedido de remoção de uma cópia retida: <b>recusado sempre</b>.
     *
     * <p>Com a retenção vigente, a recusa é o bloqueio de retenção do aceite.
     * Com a retenção já expirada, a recusa continua: expurgo é um processo
     * separado e auditado, fora deste serviço — esta camada nunca delega a
     * remoção de objeto retido ao armazenamento.
     *
     * @throws RetencaoImutavelException sempre, para documento com cópia retida
     * @throws IllegalStateException     se o documento não tiver cópia retida
     */
    public void solicitarRemocao(UUID tenantId, String accessKey) {
        Objects.requireNonNull(tenantId, "Tenant da remoção é obrigatório");
        Objects.requireNonNull(accessKey, "Chave de acesso da remoção é obrigatória");
        RegistroRetencaoXml registro = store.buscar(tenantId, accessKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Documento sem cópia retida neste armazenamento (chave de acesso "
                                + accessKey + ")"));
        Instant agora = clock.instant();
        if (agora.isBefore(registro.retidoAte())) {
            throw new RetencaoImutavelException(
                    "Remoção recusada: cópia retida da chave de acesso " + accessKey
                            + " está sob bloqueio de retenção até " + registro.retidoAte()
                            + " (retenção imutável, P2.18)");
        }
        throw new RetencaoImutavelException(
                "Remoção recusada: a retenção da chave de acesso " + accessKey
                        + " expirou em " + registro.retidoAte()
                        + ", mas o expurgo é um processo separado e auditado — "
                        + "este serviço nunca remove cópia retida (P2.18)");
    }

    private void validarDocumentoAutorizado(NfageEmissionService.NfageEmitida doc) {
        if (doc.status() != NfageStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Só se arquiva com retenção o XML AUTORIZADO (P2.18): documento em "
                            + doc.status());
        }
        if (doc.xmlStorageUri() == null || doc.xmlStorageUri().isBlank()) {
            throw new IllegalStateException(
                    "Documento AUTHORIZED sem a URI do XML assinado no cofre (P2.6) — nada a arquivar");
        }
        if (!Sha256.formatoValido(doc.xmlSha256())) {
            throw new IllegalStateException(
                    "Documento AUTHORIZED sem xml_sha256 no formato do dicionário (64 hex minúsculos)");
        }
    }

    /**
     * Guarda de origem do ambiente local (aceite da P2.18): só SIMULATED com
     * origem de simulador ou de sintético com certificado de teste. A coerência
     * ambiente×tpAmb é a da trava do dicionário da P2.6, reutilizada — nunca
     * duplicada — antes das recusas próprias desta guarda.
     *
     * <p><b>Precedência:</b> {@code NfageProductionGuard.exigirPermitido} roda
     * <b>primeiro</b>; a recusa de um contexto incoerente para ele (ex.:
     * produção sem habilitação explícita) sai como o
     * {@code IllegalStateException} <b>do guard</b>, não como
     * {@code OrigemXmlRecusadaException} — as recusas próprias abaixo só são
     * alcançadas por contextos que o guard aceita.
     */
    private void exigirOrigemLocalPermitida(ComandoArquivamento cmd) {
        Objects.requireNonNull(cmd.origem(), "Origem do XML é obrigatória (guarda de origem da P2.18)");
        Objects.requireNonNull(cmd.ambiente(), "Contexto de ambiente é obrigatório (guarda de origem da P2.18)");
        NfageProductionGuard.exigirPermitido(cmd.ambiente());
        if (cmd.origem() == OrigemXmlAutorizado.HOMOLOGACAO_ECNPJ_ZIIP_TGP) {
            throw new OrigemXmlRecusadaException(
                    "XML da homologação com o e-CNPJ da Ziip/TGP não entra no armazenamento "
                            + "com retenção: ele é apagado ao fim de cada ciclo de homologação (P3.7)");
        }
        switch (cmd.ambiente().ambiente()) {
            case HOMOLOGATION -> throw new OrigemXmlRecusadaException(
                    "Ambiente HOMOLOGATION não entra no armazenamento com retenção local: "
                            + "os XML da homologação são apagados ao fim de cada ciclo (P3.7)");
            case PRODUCTION -> throw new OrigemXmlRecusadaException(
                    "Ambiente PRODUCTION não entra no armazenamento local: os perfis locais têm "
                            + "ziipsan.fiscal.production-allowed falsa (P0.27); a retenção de "
                            + "produção é o S3 com Object Lock da LC.5");
            case SIMULATED -> {
                // Origem SIMULADOR ou SINTETICO_CERTIFICADO_TESTE em ambiente
                // simulado: as únicas combinações que o aceite admite aqui.
            }
        }
    }
}
