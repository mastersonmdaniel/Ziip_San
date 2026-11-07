package br.com.ziipsan.commercial.fiscal.homologacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Conferência do registro de acessos ao certificado no cofre local (P3.7/§2.4). */
class ConferenciaAcessosCofreTest {

    private static final String THUMB_ZIIP = "TESTE-THUMBPRINT-ZIIP-0001";
    private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");

    private final ConferenciaAcessosCofre conferencia = new ConferenciaAcessosCofre(THUMB_ZIIP);

    private static ConferenciaAcessosCofre.AcessoCertificado acessoAssinatura(String ciclo) {
        return new ConferenciaAcessosCofre.AcessoCertificado(T0,
                ConferenciaAcessosCofre.FinalidadeAcesso.ASSINATURA_HOMOLOGACAO, ciclo, THUMB_ZIIP);
    }

    @Test
    @DisplayName("registro completo (um acesso de assinatura por assinatura) confere")
    void registroCompletoConfere() {
        var acessos = List.of(acessoAssinatura("ciclo-01"), acessoAssinatura("ciclo-01"),
                new ConferenciaAcessosCofre.AcessoCertificado(T0,
                        ConferenciaAcessosCofre.FinalidadeAcesso.CONFERENCIA_CERTIFICADO, null, THUMB_ZIIP));
        var assinaturas = List.of(new ConferenciaAcessosCofre.AssinaturaDoCiclo("ciclo-01"),
                new ConferenciaAcessosCofre.AssinaturaDoCiclo("ciclo-01"));
        assertThat(conferencia.conferir(acessos, assinaturas).conferido()).isTrue();
        assertThatCode(() -> conferencia.exigirConferido(acessos, assinaturas)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assinatura sem acesso registrado no cofre NÃO confere")
    void assinaturaSemAcessoNaoConfere() {
        var acessos = List.of(acessoAssinatura("ciclo-01"));
        var assinaturas = List.of(new ConferenciaAcessosCofre.AssinaturaDoCiclo("ciclo-01"),
                new ConferenciaAcessosCofre.AssinaturaDoCiclo("ciclo-01"));
        var resultado = conferencia.conferir(acessos, assinaturas);
        assertThat(resultado.conferido()).isFalse();
        assertThat(resultado.problemas()).anyMatch(p -> p.contains("assinatura"));
        assertThatThrownBy(() -> conferencia.exigirConferido(acessos, assinaturas))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("acesso a certificado diverso do e-CNPJ da Ziip/TGP NÃO confere")
    void acessoACertificadoDeClienteNaoConfere() {
        var acessos = List.of(new ConferenciaAcessosCofre.AcessoCertificado(T0,
                ConferenciaAcessosCofre.FinalidadeAcesso.ASSINATURA_HOMOLOGACAO, "ciclo-01", "THUMB-CLIENTE"));
        var resultado = conferencia.conferir(acessos, List.of());
        assertThat(resultado.conferido()).isFalse();
        assertThat(resultado.problemas()).anyMatch(p -> p.contains("cliente"));
    }
}
