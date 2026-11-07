package br.com.ziipsan.commercial.fiscal.nfage;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.json.Json;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Emissão da NFAg-e (modelo 75) de uma conta, sobre o motor fiscal do dicionário
 * (V4_5, P1.8) — tarefa P2.6.
 *
 * <p><b>Idempotência (aceite expresso):</b> a origem do documento é {@code BILL} +
 * {@code billId}. Antes de qualquer reserva, o serviço consulta a porta
 * {@link NfageDocumentoStore} pela NFAg-e viva da origem: ciclo reexecutado devolve a
 * existente — <b>não</b> reserva número novo, <b>não</b> assina de novo, <b>não</b>
 * chama o gateway de novo. Uma conta tem no máximo uma NFAg-e viva (e
 * {@code bills.fiscal_document_id} a aponta, V4_7).
 *
 * <p><b>O que é validado aqui, sem inventar integração:</b> CNPJ do emitente e do
 * destinatário pela validação de CNPJ alfanumérico da P1.39 ({@link DocumentNumber},
 * RAS-03 — nenhum validador duplicado); município IBGE do destinatário (7 dígitos, do
 * endereço da P1.3, DAD-04) e dados do emitente (tenant da P1.6, DAD-32) entram como
 * <b>entradas do comando</b> (portas de leitura de quem chama), nunca buscados por
 * integração presumida; trava tripla de produção por {@link NfageProductionGuard};
 * total do documento por {@link NfageTotalRule} (NT 2026.004) sobre os valores dos
 * itens da conta — alíquota e valor de IBS/CBS vêm do item, nunca de constante.
 *
 * <p><b>O que NÃO é feito aqui:</b> assinatura e transmissão reais. Elas ficam atrás
 * das portas {@link NfageSignerGateway} (cofre §2.4/P1.8) e {@link NfageGateway}
 * (SEFAZ), sem implementação nesta tarefa. Sem as duas portas ligadas, o documento
 * para em {@link NfageStatus#RESERVED} — nenhum estado de autorizado é fingido. A
 * homologação na SEFAZ do piloto ({@code tpAmb=2}) com o e-CNPJ da Ziip/TGP é aceite
 * externo pendente (ver relatório): este serviço <b>não</b> é a prova dela.
 *
 * <p><b>Campos condicionais ao MOC definitivo</b> (marcados no comando e na V4_7):
 * destinatário sem CPF ou CNPJ, código de classificação do item
 * ({@code fiscal_item_code}), grupo IBS/CBS por item e documento do destinatário
 * cifrado com hash. Eles são carregados ligados à {@code versaoLeiaute} do comando;
 * o leiaute XML da NFAg-e <b>não</b> é inventado aqui — o payload JSON abaixo é o
 * registro interno do documento, não o XML do modelo 75.
 *
 * <p>{@code issued_at} é gravado no fuso {@code America/Sao_Paulo} (DAD-18), a partir
 * do {@link Clock} injetado.
 */
public final class NfageEmissionService {

    /** Origem do documento fiscal de uma conta. */
    public static final String SOURCE_TYPE_BILL = "BILL";

    /** Fuso do issued_at do dicionário fiscal (DAD-18). */
    public static final ZoneId ISSUE_ZONE = ZoneId.of("America/Sao_Paulo");

    /** Item da conta que entra no documento (grupo IBS/CBS por item, NT 2026.002). */
    public record ItemComando(UUID billItemId, String fiscalItemCode, BigDecimal valorLiquido,
                              String ibsCbsCst, String ibsCbsCclassTrib, BigDecimal ibsCbsBase,
                              BigDecimal ibsRate, BigDecimal ibsValue,
                              BigDecimal cbsRate, BigDecimal cbsValue) {
    }

    /**
     * @param tenantId                     tenant emissor (dados do emitente no tenant, P1.6/DAD-32)
     * @param billId                       conta de origem (origem BILL + billId)
     * @param dataFatoGerador              data do fato gerador (fronteira de obrigatoriedade, Ato 4/2026)
     * @param emitenteCnpj                 CNPJ do emitente (numérico ou alfanumérico, P1.39)
     * @param emitenteUf                   código da UF do emitente (2 dígitos, compõe a chave)
     * @param serie                        série fiscal reservada em fiscal_series (V4_5)
     * @param numero                       número reservado em fiscal_series (sem lacuna, V4_5)
     * @param tipoEmissao                  tipo de emissão da chave (contingência usa o seu tipo)
     * @param codigoNumerico               código numérico de 8 dígitos da chave
     * @param contingencia                 emissão em contingência
     * @param motivoContingencia           motivo da contingência (obrigatório se contingencia)
     * @param destinatarioDocumento        CNPJ do destinatário (P1.39); nulo só com o indicador abaixo
     * @param destinatarioSemDocumento     CONDICIONAL AO MOC: destinatário sem CPF ou CNPJ
     * @param destinatarioMunicipioIbge    município do destinatário (IBGE 7 dígitos, endereço P1.3/DAD-04)
     * @param versaoLeiaute                versão do leiaute (MOC) à qual os condicionais se ligam
     * @param ambiente                     contexto de ambiente (trava tripla avaliada na emissão)
     * @param itens                        itens da conta com os valores fiscais (nunca inventados aqui)
     */
    public record ComandoEmissao(UUID tenantId, UUID billId, LocalDate dataFatoGerador,
                                 String emitenteCnpj, int emitenteUf, int serie, long numero,
                                 int tipoEmissao, String codigoNumerico,
                                 boolean contingencia, String motivoContingencia,
                                 String destinatarioDocumento, boolean destinatarioSemDocumento,
                                 String destinatarioMunicipioIbge, String versaoLeiaute,
                                 NfageProductionGuard.ContextoAmbiente ambiente,
                                 List<ItemComando> itens) {
    }

    /**
     * @param payloadJson registro interno do documento, serializado com {@link Json} do
     *                    core (não é o XML do modelo 75 — leiaute é do MOC)
     */
    public record NfageEmitida(UUID id, UUID tenantId, UUID billId, String accessKey,
                               NfageStatus status, BigDecimal totalDocumento,
                               OffsetDateTime issuedAt, String protocolo,
                               String codigoRejeicao, String mensagemRejeicao,
                               String xmlStorageUri, String xmlSha256, String payloadJson) {

        NfageEmitida comStatus(NfageStatus novo, String protocolo, String codRej, String msgRej,
                               String xmlUri, String xmlHash) {
            return new NfageEmitida(id, tenantId, billId, accessKey, novo, totalDocumento,
                    issuedAt, protocolo, codRej, msgRej, xmlUri, xmlHash, payloadJson);
        }
    }

    private final Clock clock;
    private final NfageDocumentoStore store;
    private final NfageSignerGateway signer;
    private final NfageGateway gateway;

    /** Sem portas de assinatura/transmissão: a emissão para em RESERVED (ver javadoc). */
    public NfageEmissionService(Clock clock, NfageDocumentoStore store) {
        this(clock, store, null, null);
    }

    public NfageEmissionService(Clock clock, NfageDocumentoStore store,
                                NfageSignerGateway signer, NfageGateway gateway) {
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
        this.store = Objects.requireNonNull(store, "Porta de persistência do documento é obrigatória");
        this.signer = signer;
        this.gateway = gateway;
    }

    public NfageEmitida emitir(ComandoEmissao cmd) {
        Objects.requireNonNull(cmd, "Comando de emissão da NFAg-e é obrigatório");
        validar(cmd);

        // Idempotência: a NFAg-e viva da origem é devolvida como está, sem nova
        // reserva, sem nova assinatura e sem nova chamada ao gateway.
        Optional<NfageEmitida> viva = store.buscarVivaPorOrigem(cmd.tenantId(), SOURCE_TYPE_BILL, cmd.billId());
        if (viva.isPresent()) {
            return viva.get();
        }

        NfageProductionGuard.exigirPermitido(cmd.ambiente());

        LocalDate emissao = LocalDate.ofInstant(clock.instant(), ISSUE_ZONE);
        BigDecimal total = NfageTotalRule.totalDocumento(emissao, cmd.itens().stream()
                .map(i -> new NfageTotalRule.ItemTotal(i.valorLiquido(), i.ibsValue(), i.cbsValue()))
                .toList());
        String accessKey = NfageAccessKey.build(cmd.emitenteUf(), YearMonth.from(emissao),
                cmd.emitenteCnpj(), cmd.serie(), cmd.numero(), cmd.tipoEmissao(), cmd.codigoNumerico());

        String emitente = DocumentNumber.normalize(cmd.emitenteCnpj());
        String destinatario = cmd.destinatarioSemDocumento()
                ? null : DocumentNumber.normalize(cmd.destinatarioDocumento());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelo", NfageAccessKey.MODEL);
        payload.put("versaoLeiaute", cmd.versaoLeiaute());
        payload.put("chaveAcesso", accessKey);
        payload.put("origemTipo", SOURCE_TYPE_BILL);
        payload.put("origemId", cmd.billId().toString());
        payload.put("emitenteCnpj", emitente);
        payload.put("ambiente", cmd.ambiente().ambiente().name());
        payload.put("tpAmb", cmd.ambiente().tpAmb());
        payload.put("contingencia", cmd.contingencia());
        if (cmd.contingencia()) {
            payload.put("motivoContingencia", cmd.motivoContingencia());
        }
        // Condicional ao MOC: sem documento, grava-se o indicador, nunca um documento presumido.
        payload.put("destinatarioSemDocumento", cmd.destinatarioSemDocumento());
        if (destinatario != null) {
            payload.put("destinatarioDocumento", destinatario);
        }
        payload.put("destinatarioMunicipioIbge", cmd.destinatarioMunicipioIbge());
        payload.put("dataFatoGerador", cmd.dataFatoGerador().toString());
        payload.put("nfageObrigatoria", NfageObrigatoriedade.exigeNfage(cmd.dataFatoGerador()));
        payload.put("totalDocumento", total);
        payload.put("itens", cmd.itens().stream().map(i -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("billItemId", i.billItemId().toString());
            // Condicionais ao MOC (NT 2026.002): código de classificação e grupo IBS/CBS por item.
            item.put("fiscalItemCode", i.fiscalItemCode());
            item.put("valorLiquido", i.valorLiquido());
            item.put("ibsCbsCst", i.ibsCbsCst());
            item.put("ibsCbsCclassTrib", i.ibsCbsCclassTrib());
            item.put("ibsCbsBase", i.ibsCbsBase());
            item.put("ibsRate", i.ibsRate());
            item.put("ibsValue", i.ibsValue());
            item.put("cbsRate", i.cbsRate());
            item.put("cbsValue", i.cbsValue());
            return item;
        }).toList());

        NfageEmitida doc = new NfageEmitida(UUID.randomUUID(), cmd.tenantId(), cmd.billId(),
                accessKey, NfageStatus.RESERVED, total,
                OffsetDateTime.ofInstant(clock.instant(), ISSUE_ZONE),
                null, null, null, null, null, Json.write(payload));
        store.registrar(doc);

        if (signer == null || gateway == null) {
            // Sem assinatura/transmissão ligadas, o documento fica RESERVADO.
            return doc;
        }

        NfageSignerGateway.Assinatura assinatura = signer.assinar(accessKey, doc.payloadJson());
        NfageStateMachine.requireTransition(doc.status(), NfageStatus.SIGNED);
        doc = doc.comStatus(NfageStatus.SIGNED, null, null, null,
                assinatura.xmlStorageUri(), assinatura.xmlSha256());
        store.atualizar(doc);

        NfageStateMachine.requireTransition(doc.status(), NfageStatus.TRANSMITTED);
        doc = doc.comStatus(NfageStatus.TRANSMITTED, null, null, null,
                doc.xmlStorageUri(), doc.xmlSha256());
        store.atualizar(doc);

        NfageGateway.ResultadoTransmissao resultado = gateway.transmitir(
                new NfageGateway.DocumentoParaTransmissao(accessKey, assinatura.xmlStorageUri()));
        Objects.requireNonNull(resultado, "Gateway de transmissão devolveu resultado nulo");
        NfageStatus desfecho = switch (resultado.desfecho()) {
            case AUTHORIZED -> NfageStatus.AUTHORIZED;
            case REJECTED -> NfageStatus.REJECTED;
            case PENDING_RECONCILIATION -> NfageStatus.PENDING_RECONCILIATION;
        };
        NfageStateMachine.requireTransition(doc.status(), desfecho);
        doc = doc.comStatus(desfecho, resultado.protocolo(),
                resultado.codigoRejeicao(), resultado.mensagemRejeicao(),
                doc.xmlStorageUri(), doc.xmlSha256());
        store.atualizar(doc);
        return doc;
    }

    private void validar(ComandoEmissao cmd) {
        if (cmd.tenantId() == null) {
            throw new IllegalArgumentException("Tenant emissor é obrigatório (dados do emitente no tenant, P1.6)");
        }
        if (cmd.billId() == null) {
            throw new IllegalArgumentException("A NFAg-e exige a conta de origem (origem BILL + billId)");
        }
        if (cmd.dataFatoGerador() == null) {
            throw new IllegalArgumentException("Data do fato gerador é obrigatória");
        }
        if (!DocumentNumber.isValidCnpj(cmd.emitenteCnpj())) {
            throw new IllegalArgumentException("CNPJ do emitente inválido (validação P1.39, CNPJ alfanumérico)");
        }
        if (cmd.destinatarioSemDocumento()) {
            // Condicional ao MOC: o indicador dispensa o documento, mas documento
            // informado junto do indicador é contradição — erro explícito.
            if (cmd.destinatarioDocumento() != null && !cmd.destinatarioDocumento().isBlank()) {
                throw new IllegalArgumentException(
                        "Destinatário marcado como sem documento (condicional ao MOC) não pode trazer documento");
            }
        } else if (!DocumentNumber.isValidCnpj(cmd.destinatarioDocumento())) {
            throw new IllegalArgumentException("CNPJ do destinatário inválido (validação P1.39, CNPJ alfanumérico)");
        }
        if (cmd.destinatarioMunicipioIbge() == null
                || !cmd.destinatarioMunicipioIbge().matches("\\d{7}")) {
            throw new IllegalArgumentException("Município do destinatário exige código IBGE de 7 dígitos (endereço da P1.3)");
        }
        if (cmd.versaoLeiaute() == null || cmd.versaoLeiaute().isBlank()) {
            throw new IllegalArgumentException(
                    "Versão do leiaute é obrigatória: os campos condicionais se ligam a ela");
        }
        if (cmd.contingencia() && (cmd.motivoContingencia() == null || cmd.motivoContingencia().isBlank())) {
            throw new IllegalArgumentException("Emissão em contingência exige o motivo da contingência");
        }
        if (!cmd.contingencia() && cmd.motivoContingencia() != null && !cmd.motivoContingencia().isBlank()) {
            throw new IllegalArgumentException("Motivo de contingência sem emissão em contingência é contradição");
        }
        if (cmd.ambiente() == null) {
            throw new IllegalArgumentException("Contexto de ambiente é obrigatório (trava tripla de produção)");
        }
        if (cmd.itens() == null || cmd.itens().isEmpty()) {
            throw new IllegalArgumentException("A NFAg-e exige ao menos um item da conta");
        }
        for (ItemComando item : cmd.itens()) {
            if (item.billItemId() == null) {
                throw new IllegalArgumentException("Item da NFAg-e exige o item da conta de origem");
            }
            if (item.valorLiquido() == null || item.valorLiquido().signum() < 0) {
                throw new IllegalArgumentException("Item com valor líquido nulo ou negativo");
            }
            // Espelho em código do CHECK chk_bill_items_ibs_cbs_nonnegative da
            // V4_7 (correção da revisão cruzada da P2.6): base, alíquotas e
            // valores do grupo IBS/CBS, quando presentes, nunca são negativos.
            if ((item.ibsCbsBase() != null && item.ibsCbsBase().signum() < 0)
                    || (item.ibsRate() != null && item.ibsRate().signum() < 0)
                    || (item.ibsValue() != null && item.ibsValue().signum() < 0)
                    || (item.cbsRate() != null && item.cbsRate().signum() < 0)
                    || (item.cbsValue() != null && item.cbsValue().signum() < 0)) {
                throw new IllegalArgumentException(
                        "Item com base, alíquota ou valor de IBS/CBS negativo (grupo por item, NT 2026.002)");
            }
        }
    }
}
