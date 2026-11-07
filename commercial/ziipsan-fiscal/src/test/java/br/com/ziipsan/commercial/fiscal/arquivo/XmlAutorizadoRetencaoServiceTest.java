package br.com.ziipsan.commercial.fiscal.arquivo;

import br.com.ziipsan.commercial.fiscal.nfage.NfageEmissionService;
import br.com.ziipsan.commercial.fiscal.nfage.NfageProductionGuard;
import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Arquivamento com retenção do XML autorizado (P2.18): gravação só se ausente
 * na porta de armazenamento (duplo — sobre a {@code ObjectStorage} real o
 * adaptador compõe {@code exists}+{@code put}, sem atomicidade; ver a porta),
 * hash na {@code HashChain} (duplo), recusa de
 * apagar e de sobrescrever, leitura com conferência de SHA-256 contra o
 * registro e de coerência registro × elo, e guarda de
 * origem do ambiente local.
 *
 * <p><b>Vetores independentes:</b> os SHA-256 dos XML sintéticos abaixo e os
 * hashes de elo esperados foram calculados FORA deste código, com
 * {@code hashlib} (Python), e fixados como literais — nunca gerados pela
 * classe sob teste nem pelo duplo da cadeia. O duplo da cadeia usa a forma
 * canônica documentada nele ({@code sha256(eloAnterior + "\n" + referencia +
 * "\n" + sha256 + "\n")}, gênese de 64 zeros), que é a forma do DUPLO: ela
 * prova o encadeamento que o serviço exige da porta; a forma canônica da
 * {@code HashChain} real é da implementação dela (ver RELATORIO-P2.18.md).
 *
 * <p>Os XML são fixtures sintéticas de teste (URN de teste, assinatura
 * declarada como sintética de certificado de teste) — nenhum leiaute real de
 * NFAg-e é presumido, como na P2.6.
 */
class XmlAutorizadoRetencaoServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String KEY_1 = "42261211222333000181750010000001231876543214";
    private static final String KEY_2 = "35270134028316000103750020009876541123456782";

    private static final String XML_1 = "<NFAg-e xmlns=\"urn:ziipsan:teste-sintetico\">"
            + "<infNFAg-e Id=\"NFAg-e42261211222333000181750010000001231876543214\">"
            + "<ide><mod>75</mod><nNF>123</nNF></ide>"
            + "<emit><CNPJ>11222333000181</CNPJ></emit>"
            + "<total><vNF>100.00</vNF></total></infNFAg-e>"
            + "<Signature>SINTETICO-CERTIFICADO-DE-TESTE</Signature></NFAg-e>";
    private static final String XML_2 = "<NFAg-e xmlns=\"urn:ziipsan:teste-sintetico\">"
            + "<infNFAg-e Id=\"NFAg-e35270134028316000103750020009876541123456782\">"
            + "<ide><mod>75</mod><nNF>987654</nNF></ide>"
            + "<emit><CNPJ>34028316000103</CNPJ></emit>"
            + "<total><vNF>250.00</vNF></total></infNFAg-e>"
            + "<Signature>SINTETICO-CERTIFICADO-DE-TESTE</Signature></NFAg-e>";
    private static final String XML_1_ALTERADO = XML_1.replace("100.00", "999.00");

    // SHA-256 calculados com hashlib sobre os bytes UTF-8 exatos dos XML acima.
    private static final String SHA_1 = "2d0edf6845d5f2dfcb0be1471508f17c1b64d022a8d937e26f5f1a5985e5c2d3";
    private static final String SHA_2 = "22d7fd52fcda8295eb21d650a44a355f93554af7799353a269d02e41a10df832";
    private static final String SHA_1_ALTERADO = "925855da9aaf51e391c30e29d88fb7141dc73383d4a95f93a9a88ea4f18cc850";

    // Elos esperados do duplo da cadeia (forma canônica do duplo, hashlib).
    private static final String ELO_1 = "6e4f80ff23c0d13631d46f907d7eee2dd64c2c7fccde9a2f584f73d64eafa962";
    private static final String ELO_2 = "a8ecf25b459eef920fc98f1e61337385daba739a386b342c75354ce6d3d0d307";

    private static final String CHAVE_1 = "retencao/nfage/11111111-2222-3333-4444-555555555555/"
            + "42261211222333000181750010000001231876543214.xml";
    private static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");
    private static final Instant RETIDO_ATE = Instant.parse("2031-11-07T12:00:00Z");
    private static final String URI_COEFRE_1 = "cofre://xml-assinados/" + KEY_1 + ".xml";
    private static final String URI_COEFRE_2 = "cofre://xml-assinados/" + KEY_2 + ".xml";

    // ------------------------------------------------------------------ duplos

    /** Armazenamento em memória com a semântica da porta: gravação só se ausente. */
    private static final class ArmazenamentoMemoria implements ArmazenamentoObjetosPort {
        final Map<String, byte[]> objetos = new HashMap<>();
        final Map<String, Map<String, String>> metadados = new HashMap<>();
        final AtomicInteger gravacoes = new AtomicInteger();
        final AtomicInteger apagamentos = new AtomicInteger();
        /** Simula a corrida check-then-put: existe() viu a chave livre, mas a gravação encontra a chave ocupada. */
        boolean simularCorridaNaGravacao;

        @Override
        public String gravarSeAusente(String chave, byte[] conteudo, Map<String, String> meta) {
            if (simularCorridaNaGravacao) {
                throw new ObjetoJaGravadoException(chave);
            }
            if (objetos.containsKey(chave)) {
                throw new ObjetoJaGravadoException(chave);
            }
            gravacoes.incrementAndGet();
            objetos.put(chave, conteudo.clone());
            metadados.put(chave, Map.copyOf(meta));
            return "emulador-s3://" + chave;
        }

        @Override
        public Optional<byte[]> ler(String chave) {
            byte[] b = objetos.get(chave);
            return b == null ? Optional.empty() : Optional.of(b.clone());
        }

        @Override
        public boolean existe(String chave) {
            return objetos.containsKey(chave);
        }

        @Override
        public void apagar(String chave) {
            apagamentos.incrementAndGet();
            objetos.remove(chave);
        }

        /** Corrompe o objeto por fora do serviço (simula adulteração no armazenamento). */
        void adulterar(String chave, byte[] novosBytes) {
            objetos.put(chave, novosBytes.clone());
        }
    }

    /** Duplo da cadeia: elo = sha256(eloAnterior + "\n" + referencia + "\n" + sha + "\n"). */
    private static final class CadeiaMemoria implements HashChainPort {
        private String ultimoElo = "0".repeat(64);
        final Map<String, String> elos = new LinkedHashMap<>();
        /** Quando não nulo, registrar devolve este valor cru (ex.: elo fora do formato SHA-256). */
        String eloForcado;

        @Override
        public String registrar(String referencia, String sha256Conteudo) {
            if (eloForcado != null) {
                return eloForcado;
            }
            String canonico = ultimoElo + "\n" + referencia + "\n" + sha256Conteudo + "\n";
            String elo = sha256(canonico.getBytes(StandardCharsets.UTF_8));
            elos.put(referencia, elo);
            ultimoElo = elo;
            return elo;
        }

        @Override
        public Optional<String> consultar(String referencia) {
            return Optional.ofNullable(elos.get(referencia));
        }

        private static String sha256(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class LeituraCofreMemoria implements XmlAssinadoLeituraPort {
        final Map<String, byte[]> porUri = new HashMap<>();
        final AtomicInteger leituras = new AtomicInteger();

        @Override
        public byte[] lerXmlAssinado(String xmlStorageUri) {
            leituras.incrementAndGet();
            byte[] b = porUri.get(xmlStorageUri);
            if (b == null) {
                throw new IllegalStateException("URI não encontrada no cofre do duplo: " + xmlStorageUri);
            }
            return b.clone();
        }
    }

    private static final class StoreMemoria implements ArquivoRetencaoStore {
        final Map<String, RegistroRetencaoXml> porDocumento = new HashMap<>();

        @Override
        public Optional<RegistroRetencaoXml> buscar(UUID tenantId, String accessKey) {
            return Optional.ofNullable(porDocumento.get(tenantId + "|" + accessKey));
        }

        @Override
        public void registrar(RegistroRetencaoXml registro) {
            porDocumento.put(registro.tenantId() + "|" + registro.accessKey(), registro);
        }
    }

    /** Palco completo de um teste: duplos + serviço com relógio fixo. */
    private record Palco(ArmazenamentoMemoria armazenamento, CadeiaMemoria cadeia,
                         LeituraCofreMemoria cofre, StoreMemoria store,
                         XmlAutorizadoRetencaoService service) {
    }

    private static Palco palco() {
        return palcoEm(AGORA);
    }

    private static Palco palcoEm(Instant instante) {
        var armazenamento = new ArmazenamentoMemoria();
        var cadeia = new CadeiaMemoria();
        var cofre = new LeituraCofreMemoria();
        var store = new StoreMemoria();
        var service = new XmlAutorizadoRetencaoService(
                Clock.fixed(instante, ZoneOffset.UTC), armazenamento, cadeia, cofre, store);
        return new Palco(armazenamento, cadeia, cofre, store, service);
    }

    // ------------------------------------------------------------------ fábricas

    private static NfageEmissionService.NfageEmitida documento(
            String accessKey, NfageStatus status, String xmlUri, String xmlSha256) {
        return new NfageEmissionService.NfageEmitida(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), accessKey, status,
                new BigDecimal("100.00"),
                OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC),
                status == NfageStatus.AUTHORIZED ? "PROTOCOLO-SINTETICO-1" : null,
                null, null, xmlUri, xmlSha256, "{}");
    }

    private static NfageEmissionService.NfageEmitida doc1Autorizado() {
        return documento(KEY_1, NfageStatus.AUTHORIZED, URI_COEFRE_1, SHA_1);
    }

    private static NfageProductionGuard.ContextoAmbiente ambienteSimulado() {
        return new NfageProductionGuard.ContextoAmbiente(
                NfageProductionGuard.Ambiente.SIMULATED, 2, false, true);
    }

    private static XmlAutorizadoRetencaoService.ComandoArquivamento comando(
            NfageEmissionService.NfageEmitida doc, OrigemXmlAutorizado origem,
            NfageProductionGuard.ContextoAmbiente ambiente) {
        return new XmlAutorizadoRetencaoService.ComandoArquivamento(doc, origem, ambiente, RETIDO_ATE);
    }

    private static Palco palcoComDoc1() {
        Palco p = palco();
        p.cofre().porUri.put(URI_COEFRE_1, XML_1.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    // ------------------------------------------------------------------ arquivamento

    @Test
    @DisplayName("arquiva XML do simulador: cópia no armazenamento, SHA-256 literal e elo na cadeia")
    void arquivaXmlDoSimulador() {
        Palco p = palcoComDoc1();

        RegistroRetencaoXml r = p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));

        assertThat(r.xmlSha256()).isEqualTo(SHA_1);
        assertThat(r.hashChain()).isEqualTo(ELO_1);
        assertThat(r.chaveObjeto()).isEqualTo(CHAVE_1);
        assertThat(r.uriRetencao()).isEqualTo("emulador-s3://" + CHAVE_1);
        assertThat(r.retidoAte()).isEqualTo(RETIDO_ATE);
        assertThat(p.armazenamento().objetos.get(CHAVE_1))
                .isEqualTo(XML_1.getBytes(StandardCharsets.UTF_8));
        assertThat(p.armazenamento().metadados.get(CHAVE_1))
                .containsEntry("sha-256", SHA_1)
                .containsEntry("chave-acesso", KEY_1)
                .containsEntry("retido-ate", RETIDO_ATE.toString());
        assertThat(p.cadeia().consultar(CHAVE_1)).contains(ELO_1);
        assertThat(p.store().buscar(TENANT, KEY_1)).contains(r);
        // O retrato de auditoria é JSON de verdade (Json do core), nunca Map.toString().
        Map<String, Object> retrato = Json.readMap(r.registroJson());
        assertThat(retrato).containsEntry("xmlSha256", SHA_1)
                .containsEntry("hashChain", ELO_1)
                .containsEntry("chaveObjeto", CHAVE_1);
    }

    @Test
    @DisplayName("arquiva sintético com certificado de teste e encadeia o segundo elo na HashChain")
    void arquivaSinteticoEEncadeia() {
        Palco p = palcoComDoc1();
        p.cofre().porUri.put(URI_COEFRE_2, XML_2.getBytes(StandardCharsets.UTF_8));

        RegistroRetencaoXml r1 = p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));
        RegistroRetencaoXml r2 = p.service().arquivar(comando(
                documento(KEY_2, NfageStatus.AUTHORIZED, URI_COEFRE_2, SHA_2),
                OrigemXmlAutorizado.SINTETICO_CERTIFICADO_TESTE, ambienteSimulado()));

        assertThat(r1.hashChain()).isEqualTo(ELO_1);
        // ELO_2 só é ELO_2 porque o elo anterior era ELO_1: o encadeamento é real.
        assertThat(r2.hashChain()).isEqualTo(ELO_2);
        assertThat(r2.xmlSha256()).isEqualTo(SHA_2);
        assertThat(p.cadeia().elos).hasSize(2);
    }

    @Test
    @DisplayName("rearquivar o mesmo documento e hash é idempotente: sem nova gravação e sem novo elo")
    void rearquivarIdenticoEhIdempotente() {
        Palco p = palcoComDoc1();
        var cmd = comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado());

        RegistroRetencaoXml r1 = p.service().arquivar(cmd);
        RegistroRetencaoXml r2 = p.service().arquivar(cmd);

        assertThat(r2.id()).isEqualTo(r1.id());
        assertThat(p.armazenamento().gravacoes).hasValue(1);
        assertThat(p.cadeia().elos).hasSize(1);
    }

    // ------------------------------------------------------------------ recusas de imutabilidade

    @Test
    @DisplayName("sobrescrever a cópia retida com outro hash é recusado e o original permanece")
    void recusaSobrescritaComOutroHash() {
        Palco p = palcoComDoc1();
        p.service().arquivar(comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));
        // Mesmo tenant e mesma chave de acesso, mas outro XML (outro hash declarado
        // e outros bytes no cofre): é uma tentativa de sobrescrita da cópia retida.
        p.cofre().porUri.put(URI_COEFRE_1, XML_1_ALTERADO.getBytes(StandardCharsets.UTF_8));
        var docAlterado = documento(KEY_1, NfageStatus.AUTHORIZED, URI_COEFRE_1, SHA_1_ALTERADO);

        assertThatThrownBy(() -> p.service().arquivar(
                comando(docAlterado, OrigemXmlAutorizado.SIMULADOR, ambienteSimulado())))
                .isInstanceOf(RetencaoImutavelException.class)
                .hasMessageContaining("Sobrescrita recusada");

        assertThat(p.armazenamento().objetos.get(CHAVE_1))
                .isEqualTo(XML_1.getBytes(StandardCharsets.UTF_8));
        assertThat(p.armazenamento().gravacoes).hasValue(1);
        assertThat(p.cadeia().elos).hasSize(1);
        assertThat(p.store().buscar(TENANT, KEY_1)).get()
                .extracting(RegistroRetencaoXml::xmlSha256).isEqualTo(SHA_1);
    }

    @Test
    @DisplayName("apagar a cópia retida é recusado e a remoção nunca é delegada ao armazenamento")
    void recusaApagarObjetoRetido() {
        Palco p = palcoComDoc1();
        p.service().arquivar(comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));

        assertThatThrownBy(() -> p.service().solicitarRemocao(TENANT, KEY_1))
                .isInstanceOf(RetencaoImutavelException.class)
                .hasMessageContaining("bloqueio de retenção até");

        assertThat(p.armazenamento().apagamentos).hasValue(0);
        assertThat(p.armazenamento().existe(CHAVE_1)).isTrue();
        // E a cópia continua legível e conferida depois da tentativa de remoção.
        assertThat(p.service().lerConferido(TENANT, KEY_1))
                .isEqualTo(XML_1.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("mesmo com a retenção expirada, este serviço não remove: expurgo é processo separado")
    void recusaApagarMesmoAposExpirar() {
        Palco p = palcoComDoc1();
        p.service().arquivar(comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));
        // Mesmo palco (mesmos duplos), relógio depois do fim da retenção.
        var serviceDepois = new XmlAutorizadoRetencaoService(
                Clock.fixed(Instant.parse("2032-01-01T00:00:00Z"), ZoneOffset.UTC),
                p.armazenamento(), p.cadeia(), p.cofre(), p.store());

        assertThatThrownBy(() -> serviceDepois.solicitarRemocao(TENANT, KEY_1))
                .isInstanceOf(RetencaoImutavelException.class)
                .hasMessageContaining("processo separado");
        assertThat(p.armazenamento().apagamentos).hasValue(0);
        assertThat(p.armazenamento().existe(CHAVE_1)).isTrue();
    }

    // ------------------------------------------------------------------ integridade

    @Test
    @DisplayName("leitura devolve o XML conferindo o SHA-256")
    void leituraConfereSha256() {
        Palco p = palcoComDoc1();
        p.service().arquivar(comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));

        assertThat(p.service().lerConferido(TENANT, KEY_1))
                .isEqualTo(XML_1.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("leitura com bytes adulterados no armazenamento é erro explícito, nunca devolve o XML")
    void leituraRecusaHashDivergente() {
        Palco p = palcoComDoc1();
        p.service().arquivar(comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));
        p.armazenamento().adulterar(CHAVE_1, XML_1_ALTERADO.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> p.service().lerConferido(TENANT, KEY_1))
                .isInstanceOf(IntegridadeXmlException.class)
                .hasMessageContaining("diverge do registro de retenção");
    }

    @Test
    @DisplayName("bytes do cofre divergentes do xml_sha256 do documento abortam sem gravar nem encadear")
    void arquivamentoRecusaBytesDivergentes() {
        Palco p = palco();
        p.cofre().porUri.put(URI_COEFRE_1, XML_1_ALTERADO.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado())))
                .isInstanceOf(IntegridadeXmlException.class)
                .hasMessageContaining("diverge do xml_sha256 do documento");

        assertThat(p.armazenamento().objetos).isEmpty();
        assertThat(p.cadeia().elos).isEmpty();
        assertThat(p.store().buscar(TENANT, KEY_1)).isEmpty();
    }

    @Test
    @DisplayName("documento não autorizado não é arquivado")
    void recusaDocumentoNaoAutorizado() {
        Palco p = palcoComDoc1();
        var docReservado = documento(KEY_1, NfageStatus.RESERVED, URI_COEFRE_1, SHA_1);

        assertThatThrownBy(() -> p.service().arquivar(
                comando(docReservado, OrigemXmlAutorizado.SIMULADOR, ambienteSimulado())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTORIZADO");
        assertThat(p.armazenamento().objetos).isEmpty();
        assertThat(p.cofre().leituras).hasValue(0);
    }

    // ------------------------------------------------------------------ guarda de origem

    @Test
    @DisplayName("XML da homologação com e-CNPJ da Ziip/TGP não entra no armazenamento com retenção")
    void recusaXmlDeHomologacao() {
        Palco p = palcoComDoc1();

        assertThatThrownBy(() -> p.service().arquivar(comando(
                doc1Autorizado(), OrigemXmlAutorizado.HOMOLOGACAO_ECNPJ_ZIIP_TGP, ambienteSimulado())))
                .isInstanceOf(OrigemXmlRecusadaException.class)
                .hasMessageContaining("apagado ao fim de cada ciclo");

        assertThat(p.armazenamento().objetos).isEmpty();
        assertThat(p.cadeia().elos).isEmpty();
        assertThat(p.cofre().leituras).hasValue(0);
    }

    @Test
    @DisplayName("ambiente de homologação (tpAmb=2) é recusado mesmo com origem de simulador declarada")
    void recusaAmbienteHomologacao() {
        Palco p = palcoComDoc1();
        var homologacao = new NfageProductionGuard.ContextoAmbiente(
                NfageProductionGuard.Ambiente.HOMOLOGATION, 2, false, false);

        assertThatThrownBy(() -> p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, homologacao)))
                .isInstanceOf(OrigemXmlRecusadaException.class)
                .hasMessageContaining("HOMOLOGATION");
        assertThat(p.armazenamento().objetos).isEmpty();
    }

    @Test
    @DisplayName("ambiente de produção é recusado no armazenamento local (production-allowed falsa, P0.27)")
    void recusaProducaoNoArquivoLocal() {
        Palco p = palcoComDoc1();
        var producao = new NfageProductionGuard.ContextoAmbiente(
                NfageProductionGuard.Ambiente.PRODUCTION, 1, true, false);

        assertThatThrownBy(() -> p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, producao)))
                .isInstanceOf(OrigemXmlRecusadaException.class)
                .hasMessageContaining("PRODUCTION");
        assertThat(p.armazenamento().objetos).isEmpty();
    }

    @Test
    @DisplayName("fim de retenção no passado é erro de comando, sem gravação")
    void recusaRetidoAteNoPassado() {
        Palco p = palcoComDoc1();
        var cmd = new XmlAutorizadoRetencaoService.ComandoArquivamento(
                doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado(),
                Instant.parse("2020-01-01T00:00:00Z"));

        assertThatThrownBy(() -> p.service().arquivar(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retidoAte");
        assertThat(p.armazenamento().objetos).isEmpty();
    }

    @Test
    @DisplayName("leitura de documento sem cópia retida falha explicitamente")
    void leituraSemArquivoFalha() {
        Palco p = palco();
        assertThatThrownBy(() -> p.service().lerConferido(TENANT, KEY_1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem cópia retida");
    }

    // -------------------------------------------------- correções pós-revisão (A4/A6/A7/A8/A9)

    @Test
    @DisplayName("leitura com elo da cadeia divergente do registro é recusada (coerência registro × elo)")
    void leituraRecusaEloDivergente() {
        Palco p = palcoComDoc1();
        p.service().arquivar(comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));
        // A cadeia passa a devolver outro elo (válido no formato) para a mesma referência.
        p.cadeia().elos.put(CHAVE_1, ELO_2);

        assertThatThrownBy(() -> p.service().lerConferido(TENANT, KEY_1))
                .isInstanceOf(IntegridadeXmlException.class)
                .hasMessageContaining("Elo da HashChain diverge do registro de retenção");
        // Os bytes conferem contra o registro — a recusa é específica do ramo do elo.
        assertThat(p.armazenamento().objetos.get(CHAVE_1))
                .isEqualTo(XML_1.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("leitura com elo ausente na cadeia é recusada sem devolver bytes")
    void leituraRecusaEloAusente() {
        Palco p = palcoComDoc1();
        p.service().arquivar(comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));
        p.cadeia().elos.remove(CHAVE_1);

        assertThatThrownBy(() -> p.service().lerConferido(TENANT, KEY_1))
                .isInstanceOf(IntegridadeXmlException.class)
                .hasMessageContaining("Registro da HashChain não encontrado");
    }

    @Test
    @DisplayName("leitura com objeto sumido do armazenamento é erro explícito de retenção violada")
    void leituraRecusaObjetoSumido() {
        Palco p = palcoComDoc1();
        p.service().arquivar(comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));
        // Some com o objeto por fora do serviço (mapa direto: sem passar pelo apagar contado).
        p.armazenamento().objetos.remove(CHAVE_1);

        assertThatThrownBy(() -> p.service().lerConferido(TENANT, KEY_1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Objeto retido sumiu do armazenamento")
                .hasMessageContaining(CHAVE_1);
        assertThat(p.store().buscar(TENANT, KEY_1)).isPresent();
    }

    @Test
    @DisplayName("arquivar sobre objeto órfão (sem registro) é recusado com erro diagnosticável, sem tocar o objeto")
    void arquivamentoRecusaObjetoOrfaoSemRegistro() {
        Palco p = palcoComDoc1();
        // Órfão: objeto presente na chave determinística sem nenhum registro conhecido
        // (estado de uma falha parcial entre gravar, encadear e registrar).
        byte[] bytesOrfaos = XML_1.getBytes(StandardCharsets.UTF_8);
        p.armazenamento().adulterar(CHAVE_1, bytesOrfaos);

        assertThatThrownBy(() -> p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado())))
                .isInstanceOf(RetencaoImutavelException.class)
                .hasMessageContaining("objeto órfão")
                .hasMessageContaining("sem registro conhecido")
                .hasMessageContaining("reconciliação");

        // Nada foi sobrescrito, lido do cofre, encadeado ou registrado por cima do órfão.
        assertThat(p.armazenamento().objetos.get(CHAVE_1)).isEqualTo(bytesOrfaos);
        assertThat(p.armazenamento().gravacoes).hasValue(0);
        assertThat(p.cofre().leituras).hasValue(0);
        assertThat(p.cadeia().elos).isEmpty();
        assertThat(p.store().buscar(TENANT, KEY_1)).isEmpty();
    }

    @Test
    @DisplayName("corrida check-then-put: ObjetoJaGravadoException da gravação é traduzida em recusa de sobrescrita")
    void arquivamentoTraduzCorridaCheckThenPut() {
        Palco p = palcoComDoc1();
        // existe() vê a chave livre (mapa vazio), mas a gravação encontra a chave
        // ocupada — a corrida entre a conferência e a gravação.
        p.armazenamento().simularCorridaNaGravacao = true;

        assertThatThrownBy(() -> p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado())))
                .isInstanceOf(RetencaoImutavelException.class)
                .hasMessageContaining("corrida check-then-put")
                .hasMessageContaining("ObjetoJaGravadoException traduzida");

        assertThat(p.armazenamento().objetos).isEmpty();
        assertThat(p.cadeia().elos).isEmpty();
        assertThat(p.store().buscar(TENANT, KEY_1)).isEmpty();
    }

    @Test
    @DisplayName("elo devolvido pela cadeia fora do formato SHA-256 aborta o arquivamento sem registrar")
    void arquivamentoRecusaEloForaDeFormato() {
        Palco p = palcoComDoc1();
        p.cadeia().eloForcado = "elo-que-nao-e-sha256";

        assertThatThrownBy(() -> p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fora do formato SHA-256");

        assertThat(p.store().buscar(TENANT, KEY_1)).isEmpty();
        assertThat(p.cadeia().elos).isEmpty();
    }

    @Test
    @DisplayName("documento SIGNED (assinado, não autorizado) não é arquivado")
    void recusaDocumentoAssinado() {
        Palco p = palcoComDoc1();
        var docAssinado = documento(KEY_1, NfageStatus.SIGNED, URI_COEFRE_1, SHA_1);

        assertThatThrownBy(() -> p.service().arquivar(
                comando(docAssinado, OrigemXmlAutorizado.SIMULADOR, ambienteSimulado())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIGNED");
        assertThat(p.armazenamento().objetos).isEmpty();
        assertThat(p.cofre().leituras).hasValue(0);
    }

    @Test
    @DisplayName("documento PENDING_RECONCILIATION não é arquivado")
    void recusaDocumentoPendenteReconciliacao() {
        Palco p = palcoComDoc1();
        var docPendente = documento(KEY_1, NfageStatus.PENDING_RECONCILIATION, URI_COEFRE_1, SHA_1);

        assertThatThrownBy(() -> p.service().arquivar(
                comando(docPendente, OrigemXmlAutorizado.SIMULADOR, ambienteSimulado())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_RECONCILIATION");
        assertThat(p.armazenamento().objetos).isEmpty();
        assertThat(p.cofre().leituras).hasValue(0);
    }

    @Test
    @DisplayName("produção sem habilitação explícita é recusada pelo NfageProductionGuard (IllegalStateException dele, antes da guarda própria)")
    void recusaProducaoSemHabilitacaoVemDoGuard() {
        Palco p = palcoComDoc1();
        // Contexto PRODUCTION bem-formado no tpAmb, mas SEM habilitação explícita:
        // quem recusa é o guard da P2.6, não a guarda própria desta camada.
        var producaoSemHabilitacao = new NfageProductionGuard.ContextoAmbiente(
                NfageProductionGuard.Ambiente.PRODUCTION, 1, false, false);

        assertThatThrownBy(() -> p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, producaoSemHabilitacao)))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(OrigemXmlRecusadaException.class)
                .hasMessageContaining("habilitação explícita de produção");
        assertThat(p.armazenamento().objetos).isEmpty();
        assertThat(p.cofre().leituras).hasValue(0);
    }

    @Test
    @DisplayName("rearquivar idêntico com origem divergente do registro é recusado (idempotente é só a repetição idêntica)")
    void rearquivarComOrigemDivergenteEhRecusado() {
        Palco p = palcoComDoc1();
        RegistroRetencaoXml r1 = p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));

        assertThatThrownBy(() -> p.service().arquivar(comando(
                doc1Autorizado(), OrigemXmlAutorizado.SINTETICO_CERTIFICADO_TESTE, ambienteSimulado())))
                .isInstanceOf(RetencaoImutavelException.class)
                .hasMessageContaining("origem/retidoAte divergentes")
                .hasMessageContaining("repetição idêntica");

        assertThat(p.store().buscar(TENANT, KEY_1)).contains(r1);
        assertThat(p.armazenamento().gravacoes).hasValue(1);
        assertThat(p.cadeia().elos).hasSize(1);
    }

    @Test
    @DisplayName("rearquivar idêntico com retidoAte divergente do registro é recusado (idempotente é só a repetição idêntica)")
    void rearquivarComRetidoAteDivergenteEhRecusado() {
        Palco p = palcoComDoc1();
        RegistroRetencaoXml r1 = p.service().arquivar(
                comando(doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado()));
        var cmdDivergente = new XmlAutorizadoRetencaoService.ComandoArquivamento(
                doc1Autorizado(), OrigemXmlAutorizado.SIMULADOR, ambienteSimulado(),
                Instant.parse("2035-11-07T12:00:00Z"));

        assertThatThrownBy(() -> p.service().arquivar(cmdDivergente))
                .isInstanceOf(RetencaoImutavelException.class)
                .hasMessageContaining("origem/retidoAte divergentes")
                .hasMessageContaining("repetição idêntica");

        assertThat(p.store().buscar(TENANT, KEY_1)).contains(r1);
        assertThat(p.store().buscar(TENANT, KEY_1).get().retidoAte()).isEqualTo(RETIDO_ATE);
        assertThat(p.armazenamento().gravacoes).hasValue(1);
        assertThat(p.cadeia().elos).hasSize(1);
    }

    @Test
    @DisplayName("RegistroRetencaoXml recusa ambiente e registroJson nulos (contrato na construção)")
    void registroRetencaoRecusaAmbienteERegistroJsonNulos() {
        assertThatThrownBy(() -> new RegistroRetencaoXml(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), KEY_1, CHAVE_1,
                "emulador-s3://" + CHAVE_1, SHA_1, ELO_1,
                OrigemXmlAutorizado.SIMULADOR, null, RETIDO_ATE, AGORA, "{}"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Ambiente do registro de retenção é obrigatório");

        assertThatThrownBy(() -> new RegistroRetencaoXml(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), KEY_1, CHAVE_1,
                "emulador-s3://" + CHAVE_1, SHA_1, ELO_1,
                OrigemXmlAutorizado.SIMULADOR, "SIMULATED", RETIDO_ATE, AGORA, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Retrato JSON do registro de retenção é obrigatório");
    }
}
