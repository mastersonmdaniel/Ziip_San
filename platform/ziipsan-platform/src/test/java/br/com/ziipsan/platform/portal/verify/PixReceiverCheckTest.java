package br.com.ziipsan.platform.portal.verify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixReceiverCheckTest {

    private static final PixReceiverCheck.TenantLegalIdentity LEGAL =
            new PixReceiverCheck.TenantLegalIdentity("Servico Autonomo de Agua e Esgoto Exemplo", "12.345.678/0001-90");

    @Test
    void recebedorDoPspIgualAoCadastroDoTenantConforme() {
        var result = PixReceiverCheck.check(LEGAL, new PixReceiverCheck.PspReceiver(
                "SERVICO AUTONOMO DE AGUA E ESGOTO EXEMPLO", "12345678000190"));
        assertThat(result.conforming()).isTrue();
        assertThat(result.verdict()).isEqualTo(PixReceiverCheck.Verdict.CONFORMING);
    }

    @Test
    void nomeOuCnpjDivergenteENaoConformidadeNomeada() {
        var nome = PixReceiverCheck.check(LEGAL, new PixReceiverCheck.PspReceiver(
                "Terceirizada de Cobranca Ltda", "12.345.678/0001-90"));
        assertThat(nome.verdict()).isEqualTo(PixReceiverCheck.Verdict.NAME_DIVERGENT);
        var cnpj = PixReceiverCheck.check(LEGAL, new PixReceiverCheck.PspReceiver(
                "Servico Autonomo de Agua e Esgoto Exemplo", "98.765.432/0001-10"));
        assertThat(cnpj.verdict()).isEqualTo(PixReceiverCheck.Verdict.TAX_ID_DIVERGENT);
        var ambos = PixReceiverCheck.check(LEGAL, new PixReceiverCheck.PspReceiver("Outro", "00.000.000/0001-00"));
        assertThat(ambos.verdict()).isEqualTo(PixReceiverCheck.Verdict.BOTH_DIVERGENT);
        assertThat(ambos.conforming()).isFalse();
    }

    @Test
    void pontuacaoDeSufixoSocietarioNaoDiverge() {
        // Correcao D7: normalizeName prometia remover a pontuacao solta do
        // sufixo societario e nao removia — "LTDA." x "LTDA" divergiam.
        var result = PixReceiverCheck.check(
                new PixReceiverCheck.TenantLegalIdentity("Empresa Exemplo Ltda.", "12.345.678/0001-90"),
                new PixReceiverCheck.PspReceiver("EMPRESA EXEMPLO LTDA", "12345678000190"));
        assertThat(result.conforming()).isTrue();
        var sa = PixReceiverCheck.check(
                new PixReceiverCheck.TenantLegalIdentity("Empresa Exemplo S.A.", "12.345.678/0001-90"),
                new PixReceiverCheck.PspReceiver("EMPRESA EXEMPLO SA", "12345678000190"));
        assertThat(sa.conforming()).isTrue();
    }

    @Test
    void nomeTruncadoPeloPspDivergeNoSentidoSeguro() {
        // Declarado no javadoc (D7): truncamento pelo PSP = nao-conforme
        // conservador, nunca conforme indevido.
        var result = PixReceiverCheck.check(LEGAL, new PixReceiverCheck.PspReceiver(
                "SERVICO AUTONOMO DE AGUA E ESGOTO EXEMP", "12345678000190"));
        assertThat(result.verdict()).isEqualTo(PixReceiverCheck.Verdict.NAME_DIVERGENT);
        assertThat(result.conforming()).isFalse();
    }

    @Test
    void recebedorSemNomeOuSemCnpjNaoEConferivel() {
        assertThatThrownBy(() -> PixReceiverCheck.check(LEGAL, new PixReceiverCheck.PspReceiver("", "123")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
