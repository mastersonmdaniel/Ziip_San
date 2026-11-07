package br.com.ziipsan.commercial.fiscal.homologacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ciclo da P3.7: etapas completas + resultado no diário antes de encerrar/expurgar. */
class CicloHomologacaoTest {

    private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");

    private static CicloHomologacao cicloCompleto() {
        CicloHomologacao ciclo = new CicloHomologacao("2026-11-ciclo-01", T0);
        for (CicloHomologacao.Etapa etapa : CicloHomologacao.Etapa.values()) {
            ciclo.registrarEtapa(etapa);
        }
        return ciclo;
    }

    @Test
    @DisplayName("ciclo completo encerra com a referência do resultado no diário")
    void encerraComResultadoNoDiario() {
        CicloHomologacao ciclo = cicloCompleto();
        ciclo.encerrar("DIARIO-DE-BORDO.md: retomada 10/11/2026, ciclo 2026-11-ciclo-01", T0.plusSeconds(3600));
        assertThat(ciclo.situacao()).isEqualTo(CicloHomologacao.Situacao.ENCERRADO);
        assertThat(ciclo.completo()).isTrue();
    }

    @Test
    @DisplayName("ciclo parcial NÃO encerra como completo")
    void cicloParcialNaoEncerra() {
        CicloHomologacao ciclo = new CicloHomologacao("2026-11-ciclo-02", T0);
        ciclo.registrarEtapa(CicloHomologacao.Etapa.EMISSAO_NFSE);
        assertThatThrownBy(() -> ciclo.encerrar("diario", T0.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incompleto");
        assertThat(ciclo.etapasFaltantes()).hasSize(4);
    }

    @Test
    @DisplayName("encerramento sem resultado registrado no diário é recusado")
    void encerramentoSemDiarioRecusado() {
        CicloHomologacao ciclo = cicloCompleto();
        assertThatThrownBy(() -> ciclo.encerrar(" ", T0.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ciclo.situacao()).isEqualTo(CicloHomologacao.Situacao.ABERTO);
    }

    @Test
    @DisplayName("etapa não se registra depois do encerramento")
    void etapaAposEncerramentoRecusada() {
        CicloHomologacao ciclo = cicloCompleto();
        ciclo.encerrar("diario: resultado", T0.plusSeconds(60));
        assertThatThrownBy(() -> ciclo.registrarEtapa(CicloHomologacao.Etapa.EMISSAO_NFSE))
                .isInstanceOf(IllegalStateException.class);
    }
}
