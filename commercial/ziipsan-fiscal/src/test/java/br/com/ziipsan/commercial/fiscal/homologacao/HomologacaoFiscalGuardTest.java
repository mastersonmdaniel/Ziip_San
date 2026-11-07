package br.com.ziipsan.commercial.fiscal.homologacao;

import br.com.ziipsan.commercial.fiscal.nfage.NfageProductionGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Trava da homologação local (P3.7): só tpAmb=2, só com o e-CNPJ da própria
 * Ziip/TGP. Os valores de certificado aqui são MARCADORES DE TESTE — o
 * certificado real nunca aparece em teste nem em código.
 */
class HomologacaoFiscalGuardTest {

    private static final String CNPJ_ZIIP = "TESTE-CNPJ-ZIIP-TGP";
    private static final String THUMB_ZIIP = "TESTE-THUMBPRINT-ZIIP-0001";

    private static HomologacaoFiscalGuard.ContextoHomologacao contexto(
            NfageProductionGuard.Ambiente ambiente, Integer tpAmb,
            HomologacaoFiscalGuard.TitularCertificado titular, String cnpj, String thumb) {
        return contextoCompleto(ambiente, tpAmb, false, false, titular, cnpj, CNPJ_ZIIP, thumb, THUMB_ZIIP);
    }

    private static HomologacaoFiscalGuard.ContextoHomologacao contextoCompleto(
            NfageProductionGuard.Ambiente ambiente, Integer tpAmb,
            boolean habilitacaoProducao, boolean somenteTeste,
            HomologacaoFiscalGuard.TitularCertificado titular, String cnpj, String cnpjEsperado,
            String thumb, String thumbEsperado) {
        return new HomologacaoFiscalGuard.ContextoHomologacao(
                new NfageProductionGuard.ContextoAmbiente(ambiente, tpAmb, habilitacaoProducao, somenteTeste),
                titular, cnpj, cnpjEsperado, thumb, thumbEsperado);
    }

    @Test
    @DisplayName("homologação tpAmb=2 com o e-CNPJ da Ziip/TGP passa")
    void homologacaoValidaPassa() {
        assertThatCode(() -> HomologacaoFiscalGuard.exigirHomologacao(contexto(
                NfageProductionGuard.Ambiente.HOMOLOGATION, 2,
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP, CNPJ_ZIIP, THUMB_ZIIP)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("produção tpAmb=1 é recusada neste pacote, sempre")
    void producaoRecusada() {
        assertThatThrownBy(() -> HomologacaoFiscalGuard.exigirHomologacao(contexto(
                NfageProductionGuard.Ambiente.PRODUCTION, 1,
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP, CNPJ_ZIIP, THUMB_ZIIP)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("D5: produção HABILITADA (trava tripla satisfeita) é recusada pela guarda DESTE pacote")
    void producaoHabilitadaRecusadaPelaGuardaDaHomologacao() {
        // Com habilitação explícita + tpAmb=1 + não-test-only, a
        // NfageProductionGuard SOZINHA permitiria a produção — quem recusa
        // aqui é a exigência ambiente==HOMOLOGATION da HomologacaoFiscalGuard.
        // (O caso producaoRecusada acima NÃO isola isso: lá a habilitação é
        // falsa e quem recusa primeiro é a trava do dicionário.)
        assertThatThrownBy(() -> HomologacaoFiscalGuard.exigirHomologacao(contextoCompleto(
                NfageProductionGuard.Ambiente.PRODUCTION, 1, true, false,
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP, CNPJ_ZIIP, CNPJ_ZIIP, THUMB_ZIIP, THUMB_ZIIP)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HOMOLOGATION");
    }

    @Test
    @DisplayName("D5: configuração esperada do cofre em branco (CNPJ ou thumbprint) é recusada")
    void configuracaoEsperadaEmBrancoRecusada() {
        assertThatThrownBy(() -> HomologacaoFiscalGuard.exigirHomologacao(contextoCompleto(
                NfageProductionGuard.Ambiente.HOMOLOGATION, 2, false, false,
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP, CNPJ_ZIIP, " ", THUMB_ZIIP, THUMB_ZIIP)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuração obrigatória");
        assertThatThrownBy(() -> HomologacaoFiscalGuard.exigirHomologacao(contextoCompleto(
                NfageProductionGuard.Ambiente.HOMOLOGATION, 2, false, false,
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP, CNPJ_ZIIP, CNPJ_ZIIP, THUMB_ZIIP, "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuração obrigatória");
    }

    @Test
    @DisplayName("certificado de cliente é recusado no ambiente local")
    void certificadoDeClienteRecusado() {
        assertThatThrownBy(() -> HomologacaoFiscalGuard.exigirHomologacao(contexto(
                NfageProductionGuard.Ambiente.HOMOLOGATION, 2,
                HomologacaoFiscalGuard.TitularCertificado.CLIENTE, CNPJ_ZIIP, THUMB_ZIIP)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cliente");
    }

    @Test
    @DisplayName("CNPJ ou thumbprint divergentes do e-CNPJ esperado são recusados")
    void certificadoDivergenteRecusado() {
        assertThatThrownBy(() -> HomologacaoFiscalGuard.exigirHomologacao(contexto(
                NfageProductionGuard.Ambiente.HOMOLOGATION, 2,
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP, "OUTRO-CNPJ", THUMB_ZIIP)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HomologacaoFiscalGuard.exigirHomologacao(contexto(
                NfageProductionGuard.Ambiente.HOMOLOGATION, 2,
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP, CNPJ_ZIIP, "OUTRO-THUMB")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("tpAmb incoerente com homologação é recusado pela trava do dicionário")
    void tpAmbIncoerenteRecusado() {
        assertThatThrownBy(() -> HomologacaoFiscalGuard.exigirHomologacao(contexto(
                NfageProductionGuard.Ambiente.HOMOLOGATION, 1,
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP, CNPJ_ZIIP, THUMB_ZIIP)))
                .isInstanceOf(IllegalStateException.class);
    }
}
