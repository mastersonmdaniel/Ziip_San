package br.com.ziipsan.commercial.fiscal.homologacao;

import br.com.ziipsan.commercial.fiscal.nfage.NfageProductionGuard;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ensaio de contingência contra DUBLÊ da SEFAZ (a porta não tem implementação
 * neste pacote): prova a sequência e as travas, NÃO prova a homologação real —
 * essa é aceite externo da P3.7 (ver relatório).
 */
class EnsaioContingenciaTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final String CHAVE = "42261211222333000181750010000001231876543214";

    /** Dublê com roteiro de desfechos, na ordem das chamadas. */
    private static final class SefazDuble implements EnsaioContingencia.SefazHomologacaoPort {
        private final Queue<EnsaioContingencia.DesfechoTransmissao> roteiro;

        SefazDuble(EnsaioContingencia.DesfechoTransmissao... desfechos) {
            this.roteiro = new ArrayDeque<>(java.util.List.of(desfechos));
        }

        @Override
        public EnsaioContingencia.DesfechoTransmissao transmitir(String accessKey, boolean emContingencia) {
            return roteiro.remove();
        }
    }

    private static HomologacaoFiscalGuard.ContextoHomologacao contextoHomologacao() {
        return new HomologacaoFiscalGuard.ContextoHomologacao(
                new NfageProductionGuard.ContextoAmbiente(
                        NfageProductionGuard.Ambiente.HOMOLOGATION, 2, false, false),
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP,
                "TESTE-CNPJ-ZIIP-TGP", "TESTE-CNPJ-ZIIP-TGP",
                "TESTE-THUMBPRINT-ZIIP-0001", "TESTE-THUMBPRINT-ZIIP-0001");
    }

    @Test
    @DisplayName("indisponível → contingência → retransmissão autorizada conclui o ensaio")
    void ensaioCompletoContraDuble() {
        var ensaio = new EnsaioContingencia(RELOGIO, new SefazDuble(
                EnsaioContingencia.DesfechoTransmissao.INDISPONIVEL,
                EnsaioContingencia.DesfechoTransmissao.AUTORIZADO,
                EnsaioContingencia.DesfechoTransmissao.AUTORIZADO));
        EnsaioContingencia.ResultadoEnsaio resultado = ensaio.executar(new EnsaioContingencia.ComandoEnsaio(
                CHAVE, "Indisponibilidade simulada da SEFAZ no ensaio (dublê)", contextoHomologacao()));
        assertThat(resultado.emitidoEmContingencia()).isTrue();
        assertThat(resultado.retransmitidoAposRetorno()).isTrue();
        JsonNode registro = Json.readTree(resultado.registroJson());
        assertThat(registro.get("tpAmb").asInt()).isEqualTo(2);
        assertThat(registro.get("motivoContingencia").asString()).contains("Indisponibilidade simulada");
    }

    @Test
    @DisplayName("sem indisponibilidade na tentativa normal, não há ensaio de contingência")
    void semIndisponibilidadeNaoHaEnsaio() {
        var ensaio = new EnsaioContingencia(RELOGIO, new SefazDuble(
                EnsaioContingencia.DesfechoTransmissao.AUTORIZADO));
        assertThatThrownBy(() -> ensaio.executar(new EnsaioContingencia.ComandoEnsaio(
                CHAVE, "motivo", contextoHomologacao())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponibilidade");
    }

    @Test
    @DisplayName("retransmissão não autorizada após o retorno reprova o ensaio")
    void retransmissaoFalhaReprova() {
        var ensaio = new EnsaioContingencia(RELOGIO, new SefazDuble(
                EnsaioContingencia.DesfechoTransmissao.INDISPONIVEL,
                EnsaioContingencia.DesfechoTransmissao.AUTORIZADO,
                EnsaioContingencia.DesfechoTransmissao.REJEITADO));
        assertThatThrownBy(() -> ensaio.executar(new EnsaioContingencia.ComandoEnsaio(
                CHAVE, "motivo", contextoHomologacao())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ensaio sem motivo de contingência é recusado antes de qualquer transmissão")
    void semMotivoRecusado() {
        var ensaio = new EnsaioContingencia(RELOGIO, new SefazDuble());
        assertThatThrownBy(() -> ensaio.executar(new EnsaioContingencia.ComandoEnsaio(
                CHAVE, " ", contextoHomologacao())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ensaio fora da homologação (contexto de produção) é recusado pela trava")
    void foraDaHomologacaoRecusado() {
        var contextoProducao = new HomologacaoFiscalGuard.ContextoHomologacao(
                new NfageProductionGuard.ContextoAmbiente(
                        NfageProductionGuard.Ambiente.PRODUCTION, 1, true, false),
                HomologacaoFiscalGuard.TitularCertificado.ZIIP_TGP,
                "TESTE-CNPJ-ZIIP-TGP", "TESTE-CNPJ-ZIIP-TGP",
                "TESTE-THUMBPRINT-ZIIP-0001", "TESTE-THUMBPRINT-ZIIP-0001");
        var ensaio = new EnsaioContingencia(RELOGIO, new SefazDuble());
        assertThatThrownBy(() -> ensaio.executar(new EnsaioContingencia.ComandoEnsaio(
                CHAVE, "motivo", contextoProducao)))
                .isInstanceOf(IllegalStateException.class);
    }
}
