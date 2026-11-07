package br.com.ziipsan.commercial.fiscal.nfage;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serviço de emissão da NFAg-e: idempotência por conta (origem BILL + billId), CNPJ
 * alfanumérico da P1.39 no emitente e no destinatário (teste do consumidor exigido
 * pela P1.39), regra de total da NT 2026.004 pelo relógio, trava de produção e a
 * garantia de que, sem gateway, nenhum documento nasce "autorizado".
 *
 * <p>Vetores de CNPJ alfanumérico (conferidos independentemente do validador, mod-11
 * ASCII−48): o oficial {@code 12ABC34501DE} tem DV {@code 35}; {@code AB1234567800}
 * tem DV {@code 26}.
 */
class NfageEmissionServiceTest {

    private static final String CNPJ_NUMERICO = "11.222.333/0001-81";
    private static final String CNPJ_ALFA_EMITENTE = "12.ABC.345/01DE-35";
    private static final String CNPJ_ALFA_DESTINATARIO = "AB123456780026";

    /** Store em memória: a "viva" da origem é a que está em estado vivo. */
    private static final class LojaMemoria implements NfageDocumentoStore {
        final Map<UUID, NfageEmissionService.NfageEmitida> porId = new HashMap<>();
        final AtomicInteger registros = new AtomicInteger();

        @Override
        public Optional<NfageEmissionService.NfageEmitida> buscarVivaPorOrigem(
                UUID tenantId, String sourceType, UUID sourceId) {
            return porId.values().stream()
                    .filter(d -> d.tenantId().equals(tenantId) && d.billId().equals(sourceId)
                            && NfageStateMachine.isLive(d.status()))
                    .findFirst();
        }

        @Override
        public void registrar(NfageEmissionService.NfageEmitida documento) {
            registros.incrementAndGet();
            porId.put(documento.id(), documento);
        }

        @Override
        public void atualizar(NfageEmissionService.NfageEmitida documento) {
            porId.put(documento.id(), documento);
        }
    }

    private static Clock clockEm(String instante) {
        return Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
    }

    private static List<NfageEmissionService.ItemComando> itens() {
        // Somas à mão: líquido 60+40 = 100,00; IBS 1,50+1,00 = 2,50; CBS 5,40+3,60 = 9,00.
        return List.of(
                new NfageEmissionService.ItemComando(UUID.randomUUID(), "ITEM-AGUA",
                        new BigDecimal("60.00"), "000", "000001", new BigDecimal("60.00"),
                        new BigDecimal("0.0250"), new BigDecimal("1.50"),
                        new BigDecimal("0.0900"), new BigDecimal("5.40")),
                new NfageEmissionService.ItemComando(UUID.randomUUID(), "ITEM-ESGOTO",
                        new BigDecimal("40.00"), "000", "000001", new BigDecimal("40.00"),
                        new BigDecimal("0.0250"), new BigDecimal("1.00"),
                        new BigDecimal("0.0900"), new BigDecimal("3.60")));
    }

    private static NfageEmissionService.ComandoEmissao comando(UUID billId, String emitente,
                                                               String destinatario) {
        return new NfageEmissionService.ComandoEmissao(
                UUID.randomUUID(), billId, java.time.LocalDate.of(2026, 12, 10),
                emitente, 42, 1, 123, 1, "87654321",
                false, null, destinatario, false, "4205407", "MOC-TESTE",
                new NfageProductionGuard.ContextoAmbiente(
                        NfageProductionGuard.Ambiente.HOMOLOGATION, 2, false, true),
                itens());
    }

    private static NfageSignerGateway signerFalso() {
        return (accessKey, payloadJson) -> new NfageSignerGateway.Assinatura(
                "cofre://nfage/" + accessKey + ".xml", "a".repeat(64));
    }

    @Test
    @DisplayName("idempotência: ciclo reexecutado devolve a mesma NFAg-e e o gateway é chamado uma única vez")
    void idempotentReexecution() {
        LojaMemoria loja = new LojaMemoria();
        AtomicInteger chamadasGateway = new AtomicInteger();
        AtomicInteger chamadasAssinante = new AtomicInteger();
        NfageGateway gateway = doc -> {
            chamadasGateway.incrementAndGet();
            return new NfageGateway.ResultadoTransmissao(
                    NfageGateway.Desfecho.AUTHORIZED, "PROTOCOLO-TESTE-1", null, null);
        };
        // Assinante contado (revisão cruzada da P2.6): a prova de "não assina
        // de novo" passa a ser asserção, não só leitura do código.
        NfageSignerGateway assinanteContado = (accessKey, payloadJson) -> {
            chamadasAssinante.incrementAndGet();
            return new NfageSignerGateway.Assinatura(
                    "cofre://nfage/" + accessKey + ".xml", "a".repeat(64));
        };
        NfageEmissionService service = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), loja, assinanteContado, gateway);

        UUID billId = UUID.randomUUID();
        NfageEmissionService.ComandoEmissao cmd = comando(billId, CNPJ_NUMERICO, CNPJ_NUMERICO);
        NfageEmissionService.NfageEmitida primeira = service.emitir(cmd);

        assertThat(primeira.status()).isEqualTo(NfageStatus.AUTHORIZED);
        assertThat(primeira.protocolo()).isEqualTo("PROTOCOLO-TESTE-1");
        // Chave do vetor 1 (DV calculado à mão): emissão de 15/12/2026 em America/Sao_Paulo.
        assertThat(primeira.accessKey()).isEqualTo("42261211222333000181750010000001231876543214");
        // Regra de 2026: total é só o líquido (100,00), IBS/CBS fora do total.
        assertThat(primeira.totalDocumento()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(primeira.issuedAt().getOffset().toString()).isEqualTo("-03:00");

        // Reexecução do ciclo, até com outro número de série "reservado": devolve a viva.
        NfageEmissionService.ComandoEmissao reexecucao = new NfageEmissionService.ComandoEmissao(
                cmd.tenantId(), billId, cmd.dataFatoGerador(), cmd.emitenteCnpj(), 42, 9, 999,
                1, "11111111", false, null, CNPJ_NUMERICO, false, "4205407", "MOC-TESTE",
                cmd.ambiente(), itens());
        NfageEmissionService.NfageEmitida segunda = service.emitir(reexecucao);

        assertThat(segunda.id()).isEqualTo(primeira.id());
        assertThat(segunda.accessKey()).isEqualTo(primeira.accessKey());
        assertThat(chamadasGateway).hasValue(1);
        assertThat(chamadasAssinante).hasValue(1);
        assertThat(loja.registros).hasValue(1);
    }

    @Test
    @DisplayName("contrato das portas (revisão cruzada): AUTHORIZED sem protocolo, REJECTED sem rejeição e assinatura sem hash válido não existem")
    void gatewayAndSignerContracts() {
        assertThatThrownBy(() -> new NfageGateway.ResultadoTransmissao(
                NfageGateway.Desfecho.AUTHORIZED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("protocolo");
        assertThatThrownBy(() -> new NfageGateway.ResultadoTransmissao(
                NfageGateway.Desfecho.REJECTED, null, null, "Rejeição de teste"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rejeição");
        assertThatThrownBy(() -> new NfageSignerGateway.Assinatura("cofre://nfage/x.xml", "ABC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-256");
        assertThatThrownBy(() -> new NfageSignerGateway.Assinatura(" ", "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("URI");
    }

    @Test
    @DisplayName("sem portas de assinatura/transmissão, o documento para em RESERVED — nunca autorizado por presunção")
    void withoutGatewayStaysReserved() {
        LojaMemoria loja = new LojaMemoria();
        NfageEmissionService service = new NfageEmissionService(clockEm("2026-12-15T15:00:00Z"), loja);
        NfageEmissionService.NfageEmitida doc = service.emitir(
                comando(UUID.randomUUID(), CNPJ_NUMERICO, CNPJ_NUMERICO));
        assertThat(doc.status()).isEqualTo(NfageStatus.RESERVED);
        assertThat(doc.protocolo()).isNull();
    }

    @Test
    @DisplayName("rejeição da SEFAZ (via gateway) vira REJECTED com código e mensagem; pendência vira PENDING_RECONCILIATION")
    void gatewayOutcomes() {
        LojaMemoria loja = new LojaMemoria();
        NfageGateway rejeita = doc -> new NfageGateway.ResultadoTransmissao(
                NfageGateway.Desfecho.REJECTED, null, "539", "Rejeição de teste");
        NfageEmissionService s1 = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), loja, signerFalso(), rejeita);
        NfageEmissionService.NfageEmitida rej = s1.emitir(
                comando(UUID.randomUUID(), CNPJ_NUMERICO, CNPJ_NUMERICO));
        assertThat(rej.status()).isEqualTo(NfageStatus.REJECTED);
        assertThat(rej.codigoRejeicao()).isEqualTo("539");
        assertThat(rej.mensagemRejeicao()).isEqualTo("Rejeição de teste");

        NfageGateway pendente = doc -> new NfageGateway.ResultadoTransmissao(
                NfageGateway.Desfecho.PENDING_RECONCILIATION, null, null, null);
        NfageEmissionService s2 = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), new LojaMemoria(), signerFalso(), pendente);
        assertThat(s2.emitir(comando(UUID.randomUUID(), CNPJ_NUMERICO, CNPJ_NUMERICO)).status())
                .isEqualTo(NfageStatus.PENDING_RECONCILIATION);
    }

    @Test
    @DisplayName("total pela NT 2026.004 com relógio em 2027: líquido + IBS + CBS = 111,50")
    void totalIn2027() {
        NfageEmissionService service = new NfageEmissionService(
                clockEm("2027-01-10T15:00:00Z"), new LojaMemoria());
        NfageEmissionService.NfageEmitida doc = service.emitir(
                comando(UUID.randomUUID(), CNPJ_NUMERICO, CNPJ_NUMERICO));
        assertThat(doc.totalDocumento()).isEqualByComparingTo(new BigDecimal("111.50"));
    }

    @Test
    @DisplayName("CNPJ alfanumérico (P1.39) aceito no destinatário e normalizado no payload; DV conferido como literal")
    void alphanumericRecipient() {
        assertThat(DocumentNumber.cnpjCheckDigits("12ABC34501DE")).isEqualTo("35");
        assertThat(DocumentNumber.cnpjCheckDigits("AB1234567800")).isEqualTo("26");

        NfageEmissionService service = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), new LojaMemoria());
        NfageEmissionService.NfageEmitida doc = service.emitir(
                comando(UUID.randomUUID(), CNPJ_NUMERICO, CNPJ_ALFA_DESTINATARIO));
        JsonNode payload = Json.readTree(doc.payloadJson());
        assertThat(payload.get("destinatarioDocumento").asString()).isEqualTo(CNPJ_ALFA_DESTINATARIO);
        assertThat(payload.get("destinatarioMunicipioIbge").asString()).isEqualTo("4205407");
        assertThat(payload.get("modelo").asString()).isEqualTo("75");
    }

    @Test
    @DisplayName("emitente alfanumérico é CNPJ válido, mas a chave numérica o recusa como condicional ao MOC (erro explícito)")
    void alphanumericIssuerStopsAtAccessKey() {
        NfageEmissionService service = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), new LojaMemoria());
        assertThatThrownBy(() -> service.emitir(
                comando(UUID.randomUUID(), CNPJ_ALFA_EMITENTE, CNPJ_NUMERICO)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("alfanumérico");
    }

    @Test
    @DisplayName("CNPJ inválido no emitente ou no destinatário (inclusive alfanumérico com DV errado) é erro explícito")
    void invalidCnpjRejected() {
        NfageEmissionService service = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), new LojaMemoria());
        assertThatThrownBy(() -> service.emitir(
                comando(UUID.randomUUID(), "12.ABC.345/01DE-36", CNPJ_NUMERICO)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("emitente");
        assertThatThrownBy(() -> service.emitir(
                comando(UUID.randomUUID(), CNPJ_NUMERICO, "AB123456780027")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("destinatário");
    }

    @Test
    @DisplayName("destinatário sem documento (condicional ao MOC): indicador sem documento emite; indicador com documento é contradição")
    void recipientWithoutDocument() {
        NfageEmissionService service = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), new LojaMemoria());
        NfageEmissionService.ComandoEmissao base = comando(UUID.randomUUID(), CNPJ_NUMERICO, CNPJ_NUMERICO);
        NfageEmissionService.ComandoEmissao semDoc = new NfageEmissionService.ComandoEmissao(
                base.tenantId(), base.billId(), base.dataFatoGerador(), base.emitenteCnpj(),
                42, 1, 123, 1, "87654321", false, null, null, true, "4205407",
                "MOC-TESTE", base.ambiente(), itens());
        NfageEmissionService.NfageEmitida doc = service.emitir(semDoc);
        JsonNode payload = Json.readTree(doc.payloadJson());
        assertThat(payload.get("destinatarioSemDocumento").asBoolean()).isTrue();
        assertThat(payload.has("destinatarioDocumento")).isFalse();

        NfageEmissionService.ComandoEmissao contradicao = new NfageEmissionService.ComandoEmissao(
                base.tenantId(), UUID.randomUUID(), base.dataFatoGerador(), base.emitenteCnpj(),
                42, 1, 123, 1, "87654321", false, null, CNPJ_NUMERICO, true, "4205407",
                "MOC-TESTE", base.ambiente(), itens());
        assertThatThrownBy(() -> service.emitir(contradicao))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem documento");
    }

    @Test
    @DisplayName("trava de produção dentro do serviço: PRODUCTION sem habilitação não emite")
    void productionGuardInsideService() {
        NfageEmissionService service = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), new LojaMemoria());
        NfageEmissionService.ComandoEmissao base = comando(UUID.randomUUID(), CNPJ_NUMERICO, CNPJ_NUMERICO);
        NfageEmissionService.ComandoEmissao producao = new NfageEmissionService.ComandoEmissao(
                base.tenantId(), base.billId(), base.dataFatoGerador(), base.emitenteCnpj(),
                42, 1, 123, 1, "87654321", false, null, CNPJ_NUMERICO, false, "4205407",
                "MOC-TESTE",
                new NfageProductionGuard.ContextoAmbiente(
                        NfageProductionGuard.Ambiente.PRODUCTION, 1, false, false),
                itens());
        assertThatThrownBy(() -> service.emitir(producao))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("habilitação");
    }

    @Test
    @DisplayName("contingência sem motivo é erro; comando incompleto é erro explícito")
    void contingencyAndIncompleteCommand() {
        NfageEmissionService service = new NfageEmissionService(
                clockEm("2026-12-15T15:00:00Z"), new LojaMemoria());
        NfageEmissionService.ComandoEmissao base = comando(UUID.randomUUID(), CNPJ_NUMERICO, CNPJ_NUMERICO);
        NfageEmissionService.ComandoEmissao contingenciaSemMotivo = new NfageEmissionService.ComandoEmissao(
                base.tenantId(), base.billId(), base.dataFatoGerador(), base.emitenteCnpj(),
                42, 1, 123, 9, "87654321", true, null, CNPJ_NUMERICO, false, "4205407",
                "MOC-TESTE", base.ambiente(), itens());
        assertThatThrownBy(() -> service.emitir(contingenciaSemMotivo))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contingência");

        assertThatThrownBy(() -> service.emitir(new NfageEmissionService.ComandoEmissao(
                base.tenantId(), null, base.dataFatoGerador(), base.emitenteCnpj(),
                42, 1, 123, 1, "87654321", false, null, CNPJ_NUMERICO, false, "4205407",
                "MOC-TESTE", base.ambiente(), itens())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conta de origem");
    }
}
