package br.com.ziipsan.commercial.fiscal.homologacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import br.com.ziipsan.core.json.Json;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Área separada e expurgo com exclusão registrada (P3.7).
 *
 * <p>Hashes literais calculados FORA da classe sob teste: {@code HASH_ABC} é o
 * SHA-256 público de {@code "abc"} e {@code HASH_VAZIO} o de {@code ""}; o hash
 * do lote dos dois, pelo algoritmo documentado em {@code Sha256.doLote}
 * (ordenados, um por linha, quebra final), foi calculado com ferramenta
 * independente e fixado como literal {@code HASH_LOTE_DOIS}.
 */
class AreaSeparadaHomologacaoTest {

    private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");
    private static final String HASH_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String HASH_VAZIO = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String HASH_LOTE_DOIS = "ff481ce4c9482a1350469a8ff263915d6a478123e3a0c03f6eba5cb3e38a150f";
    private static final String AREA = AreaSeparadaHomologacao.ESQUEMA_AREA;

    private static CicloHomologacao cicloEncerrado() {
        CicloHomologacao ciclo = new CicloHomologacao("2026-11-ciclo-01", T0);
        for (CicloHomologacao.Etapa e : CicloHomologacao.Etapa.values()) {
            ciclo.registrarEtapa(e);
        }
        ciclo.encerrar("diario: resultado do ciclo", T0.plusSeconds(7200));
        return ciclo;
    }

    @Test
    @DisplayName("URI fora da área separada é recusada (imutável, backup, tabela do produto)")
    void uriForaDaAreaRecusada() {
        assertThatThrownBy(() -> new AreaSeparadaHomologacao.XmlHomologacao(
                "c1", "imutavel://fiscal/doc.xml", HASH_ABC, "THUMB"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AreaSeparadaHomologacao.XmlHomologacao(
                "c1", "backup-local://vol/doc.xml", HASH_ABC, "THUMB"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AreaSeparadaHomologacao.XmlHomologacao(
                "c1", "tabela:fiscal_documents/doc.xml", HASH_ABC, "THUMB"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("expurgo devolve exclusão registrada com ciclo, data, quantidade, hash do lote e resultado no diário")
    void expurgoComExclusaoRegistrada() {
        CicloHomologacao ciclo = new CicloHomologacao("2026-11-ciclo-01", T0);
        AreaSeparadaHomologacao area = new AreaSeparadaHomologacao(ciclo, "THUMB-ZIIP");
        area.gravar(new AreaSeparadaHomologacao.XmlHomologacao(
                "2026-11-ciclo-01", AREA + "2026-11-ciclo-01/nfse-1.xml", HASH_ABC, "THUMB-ZIIP"));
        area.gravar(new AreaSeparadaHomologacao.XmlHomologacao(
                "2026-11-ciclo-01", AREA + "2026-11-ciclo-01/nfage-1.xml", HASH_VAZIO, "THUMB-ZIIP"));
        for (CicloHomologacao.Etapa e : CicloHomologacao.Etapa.values()) {
            ciclo.registrarEtapa(e);
        }
        ciclo.encerrar("diario: resultado do ciclo", T0.plusSeconds(7200));

        Instant dataExclusao = T0.plusSeconds(7260);
        AreaSeparadaHomologacao.RegistroExclusao registro = area.expurgar(dataExclusao);

        assertThat(registro.ciclo()).isEqualTo("2026-11-ciclo-01");
        assertThat(registro.dataExclusao()).isEqualTo(dataExclusao);
        assertThat(registro.quantidade()).isEqualTo(2);
        assertThat(registro.hashDoLote()).isEqualTo(HASH_LOTE_DOIS);
        // D7: o record carrega também a referência do diário — mesmo esquema de
        // 5 campos que o script de expurgo emite.
        assertThat(registro.resultadoDiario()).isEqualTo("diario: resultado do ciclo");
        assertThat(area.quantidade()).isZero();
        assertThat(ciclo.situacao()).isEqualTo(CicloHomologacao.Situacao.EXPURGADO);

        JsonNode json = Json.readTree(registro.paraJson());
        assertThat(json.get("ciclo").asString()).isEqualTo("2026-11-ciclo-01");
        assertThat(json.get("quantidade").asInt()).isEqualTo(2);
        assertThat(json.get("hashDoLote").asString()).isEqualTo(HASH_LOTE_DOIS);
        assertThat(json.get("resultadoDiario").asString()).isEqualTo("diario: resultado do ciclo");
    }

    @Test
    @DisplayName("expurgo com o ciclo ainda aberto é recusado")
    void expurgoSemEncerramentoRecusado() {
        CicloHomologacao ciclo = new CicloHomologacao("2026-11-ciclo-01", T0);
        AreaSeparadaHomologacao area = new AreaSeparadaHomologacao(ciclo, "THUMB-ZIIP");
        area.gravar(new AreaSeparadaHomologacao.XmlHomologacao(
                "2026-11-ciclo-01", AREA + "2026-11-ciclo-01/nfse-1.xml", HASH_ABC, "THUMB-ZIIP"));
        assertThatThrownBy(() -> area.expurgar(T0.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENCERRADO");
    }

    @Test
    @DisplayName("XML de outro ciclo e sobrescrita de URI são recusados")
    void xmlDeOutroCicloESobrescritaRecusados() {
        CicloHomologacao ciclo = new CicloHomologacao("2026-11-ciclo-01", T0);
        AreaSeparadaHomologacao area = new AreaSeparadaHomologacao(ciclo, "THUMB-ZIIP");
        assertThatThrownBy(() -> area.gravar(new AreaSeparadaHomologacao.XmlHomologacao(
                "outro-ciclo", AREA + "outro/doc.xml", HASH_ABC, "THUMB-ZIIP")))
                .isInstanceOf(IllegalArgumentException.class);
        area.gravar(new AreaSeparadaHomologacao.XmlHomologacao(
                "2026-11-ciclo-01", AREA + "2026-11-ciclo-01/doc.xml", HASH_ABC, "THUMB-ZIIP"));
        assertThatThrownBy(() -> area.gravar(new AreaSeparadaHomologacao.XmlHomologacao(
                "2026-11-ciclo-01", AREA + "2026-11-ciclo-01/doc.xml", HASH_VAZIO, "THUMB-ZIIP")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("D3: XML com thumbprint diverso do esperado (certificado de cliente) é recusado na GRAVAÇÃO")
    void xmlDeClienteRecusadoNaGravacao() {
        CicloHomologacao ciclo = new CicloHomologacao("2026-11-ciclo-01", T0);
        AreaSeparadaHomologacao area = new AreaSeparadaHomologacao(ciclo, "THUMB-ZIIP");
        assertThatThrownBy(() -> area.gravar(new AreaSeparadaHomologacao.XmlHomologacao(
                "2026-11-ciclo-01", AREA + "2026-11-ciclo-01/cliente.xml", HASH_ABC, "THUMB-CLIENTE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cliente");
        assertThat(area.quantidade()).isZero();
    }

    @Test
    @DisplayName("área vazia no encerramento não gera exclusão registrada de mentira")
    void areaVaziaNaoExpurga() {
        AreaSeparadaHomologacao area = new AreaSeparadaHomologacao(cicloEncerrado(), "THUMB-ZIIP");
        assertThatThrownBy(() -> area.expurgar(T0.plusSeconds(7300)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vazia");
    }
}
