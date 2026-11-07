package br.com.ziipsan.commercial.fiscal.homologacao;

import br.com.ziipsan.commercial.fiscal.nfage.NfageProductionGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fluxo completo do ciclo pelo orquestrador: abrir → etapas/XMLs → encerrar →
 * expurgar. Desde a correção D3, o serviço nasce sob a
 * {@link HomologacaoFiscalGuard} e a área confere o thumbprint de cada XML.
 */
class HomologacaoCicloServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");
    private static final String HASH_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String HASH_LOTE_UM = "620a3df236da0af638c2a61c86951463731998f91dbb3ed629f47b9fe00ad118";
    private static final String CNPJ_ZIIP = "TESTE-CNPJ-ZIIP-TGP";
    private static final String THUMB_ZIIP = "TESTE-THUMBPRINT-ZIIP-0001";

    private static HomologacaoFiscalGuard.ContextoHomologacao contextoHomologacao() {
        return new HomologacaoFiscalGuard.ContextoHomologacao(
                new NfageProductionGuard.ContextoAmbiente(
                        NfageProductionGuard.Ambiente.HOMOLOGATION, 2, false, false),
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP,
                CNPJ_ZIIP, CNPJ_ZIIP, THUMB_ZIIP, THUMB_ZIIP);
    }

    private static HomologacaoCicloService novoServico(String cicloId) {
        return new HomologacaoCicloService(cicloId, T0, contextoHomologacao());
    }

    @Test
    @DisplayName("ciclo completo encerra e expurga com o registro (hash de lote de 1, literal conferido fora)")
    void cicloCompletoPontaAPonta() {
        HomologacaoCicloService servico = novoServico("2026-11-ciclo-01");
        servico.gravarXmlAssinado(new AreaSeparadaHomologacao.XmlHomologacao("2026-11-ciclo-01",
                AreaSeparadaHomologacao.ESQUEMA_AREA + "2026-11-ciclo-01/nfse-emissao.xml",
                HASH_ABC, "TESTE-THUMBPRINT-ZIIP-0001"));
        for (CicloHomologacao.Etapa etapa : CicloHomologacao.Etapa.values()) {
            servico.registrarEtapaExecutada(etapa);
        }
        AreaSeparadaHomologacao.RegistroExclusao registro = servico.encerrarEExpurgar(
                "diario: resultado do ciclo 2026-11-ciclo-01", T0.plusSeconds(7200));
        assertThat(registro.quantidade()).isEqualTo(1);
        assertThat(registro.hashDoLote()).isEqualTo(HASH_LOTE_UM);
        assertThat(servico.ciclo().situacao()).isEqualTo(CicloHomologacao.Situacao.EXPURGADO);
        assertThat(servico.area().quantidade()).isZero();
    }

    @Test
    @DisplayName("fechamento sem nenhum XML gravado é recusado sem encerrar o ciclo")
    void fechamentoSemXmlRecusado() {
        HomologacaoCicloService servico = novoServico("2026-11-ciclo-02");
        for (CicloHomologacao.Etapa etapa : CicloHomologacao.Etapa.values()) {
            servico.registrarEtapaExecutada(etapa);
        }
        assertThatThrownBy(() -> servico.encerrarEExpurgar("diario: x", T0.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem nenhum XML");
        assertThat(servico.ciclo().situacao()).isEqualTo(CicloHomologacao.Situacao.ABERTO);
    }

    @Test
    @DisplayName("gravação depois do expurgo é recusada (área fechada com o ciclo)")
    void gravacaoAposExpurgoRecusada() {
        HomologacaoCicloService servico = novoServico("2026-11-ciclo-01");
        servico.gravarXmlAssinado(new AreaSeparadaHomologacao.XmlHomologacao("2026-11-ciclo-01",
                AreaSeparadaHomologacao.ESQUEMA_AREA + "2026-11-ciclo-01/a.xml",
                HASH_ABC, "TESTE-THUMBPRINT-ZIIP-0001"));
        for (CicloHomologacao.Etapa etapa : CicloHomologacao.Etapa.values()) {
            servico.registrarEtapaExecutada(etapa);
        }
        servico.encerrarEExpurgar("diario: resultado", T0.plusSeconds(7200));
        assertThatThrownBy(() -> servico.gravarXmlAssinado(new AreaSeparadaHomologacao.XmlHomologacao(
                "2026-11-ciclo-01", AreaSeparadaHomologacao.ESQUEMA_AREA + "2026-11-ciclo-01/b.xml",
                HASH_ABC, "TESTE-THUMBPRINT-ZIIP-0001")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("D3: abertura do serviço com contexto de PRODUÇÃO é recusada pela guarda, antes de nascer o ciclo")
    void aberturaForaDaHomologacaoRecusada() {
        var contextoProducao = new HomologacaoFiscalGuard.ContextoHomologacao(
                new NfageProductionGuard.ContextoAmbiente(
                        NfageProductionGuard.Ambiente.PRODUCTION, 1, true, false),
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP,
                CNPJ_ZIIP, CNPJ_ZIIP, THUMB_ZIIP, THUMB_ZIIP);
        assertThatThrownBy(() -> new HomologacaoCicloService("2026-11-ciclo-09", T0, contextoProducao))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("D3: XML de certificado de CLIENTE é recusado na gravação pelo serviço (thumbprint conferido)")
    void xmlDeClienteRecusadoNaGravacao() {
        HomologacaoCicloService servico = novoServico("2026-11-ciclo-01");
        assertThatThrownBy(() -> servico.gravarXmlAssinado(new AreaSeparadaHomologacao.XmlHomologacao(
                "2026-11-ciclo-01", AreaSeparadaHomologacao.ESQUEMA_AREA + "2026-11-ciclo-01/cliente.xml",
                HASH_ABC, "THUMBPRINT-DE-CLIENTE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cliente");
        assertThat(servico.area().quantidade()).isZero();
    }
}
