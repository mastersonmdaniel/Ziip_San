package br.com.ziipsan.core.transparency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.transparency.OpenDataExport.Dataset;
import br.com.ziipsan.core.transparency.OpenDataExport.Format;
import java.util.List;
import java.util.Map;
class OpenDataExportTest {
    private static Dataset tariffs() {
        return new Dataset("tarifas", List.of("categoria", "valor_m3"),
                List.of(Map.of("categoria", "RESIDENCIAL", "valor_m3", "5.10"),
                        Map.of("categoria", "COMERCIAL", "valor_m3", "7.25")));
    }
    @Test @DisplayName("exportacao legivel por maquina (CSV e JSON) com pub.open_data_enabled ligado")
    void machineReadable() {
        assertThat(OpenDataExport.export(tariffs(), Format.CSV, true, true))
            .isEqualTo("categoria;valor_m3\nRESIDENCIAL;5.10\nCOMERCIAL;7.25\n");
        assertThat(OpenDataExport.export(tariffs(), Format.JSON, true, true))
            .isEqualTo("[{\"categoria\":\"RESIDENCIAL\",\"valor_m3\":\"5.10\"},"
                    + "{\"categoria\":\"COMERCIAL\",\"valor_m3\":\"7.25\"}]");
    }
    @Test @DisplayName("sem pub.open_data_enabled (ou modulo desligado) a exportacao e recusada")
    void disabledRefused() {
        assertThatThrownBy(() -> OpenDataExport.export(tariffs(), Format.CSV, true, false))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OpenDataExport.export(tariffs(), Format.CSV, true, null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OpenDataExport.export(tariffs(), Format.CSV, false, true))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("SEM DADO PESSOAL: campo pessoal vedado recusa o conjunto inteiro (cpf, nome, e-mail, telefone, endereco...)")
    void personalFieldRefused() {
        for (String field : List.of("cpf", "CPF", "nome_solicitante", "requester_name", "e-mail",
                "telefone", "endereco", "data_nascimento")) {
            assertThatThrownBy(() -> new Dataset("x", List.of("categoria", field), List.of()))
                .as(field).isInstanceOf(IllegalArgumentException.class);
        }
        // campo agregado/publico passa
        assertThatCode(() -> new Dataset("x", List.of("categoria", "valor_m3", "cnpj_prestador"), List.of()))
            .doesNotThrowAnyException();
    }
    @Test @DisplayName("CORRECAO D1 (revisao ED-99): corpus de furos do revisor — plural, concatenacao e sufixo numerico do MESMO campo vedado recusam")
    void reviewerBypassCorpusRefused() {
        // sonda do revisor (REVISAO-ED-99 §3/D1): cada variacao era ACEITA antes da correcao
        for (String field : List.of(
                "emails", "nomes", "telefones", "cpfs",
                "emailaddress", "phonenumber", "cpfsolicitante",
                "cpf2", "nome2")) {
            assertThatThrownBy(() -> new Dataset("x", List.of("categoria", field), List.of()))
                .as(field).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test @DisplayName("CORRECAO D1 (revisao ED-99): sinonimos obvios fora da lista original recusam (nascimento, genero, solicitante, requerente, contato, documento, data_nasc)")
    void reviewerSynonymCorpusRefused() {
        for (String field : List.of(
                "nascimento", "genero", "solicitante", "requerente",
                "contato", "documento", "data_nasc")) {
            assertThatThrownBy(() -> new Dataset("x", List.of("categoria", field), List.of()))
                .as(field).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test @DisplayName("CORRECAO D1 (revisao ED-99): corpus obvio da sonda continua recusado (sem regressao da guarda original)")
    void reviewerObviousCorpusStillRefused() {
        for (String field : List.of("cpf", "nome", "email", "telefone", "endereco",
                "data_nascimento", "nome_solicitante", "e-mail", "requester_name",
                "CPF", "endereço", "mãe", "cpf_cliente", "email_pessoal",
                "customer_name", "nome_completo", "birth_date", "senha_hash",
                "nome_categoria")) {
            assertThatThrownBy(() -> new Dataset("x", List.of("categoria", field), List.of()))
                .as(field).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test @DisplayName("CORRECAO D1: campos claramente NAO pessoais continuam aceitos (sem falso-positivo da substring/canonica)")
    void legitimateFieldsStillAccepted() {
        for (String field : List.of("categoria", "valor_m3", "cnpj_prestador",
                "mes_referencia", "ano", "total_atendimentos", "quantidade",
                "valor_total", "codigo_servico", "descricao_servico", "unidade",
                "periodo", "municipio", "data_referencia", "tipo_servico",
                "recepcao", "filename")) {
            assertThatCode(() -> new Dataset("x", List.of(field), List.of()))
                .as(field).doesNotThrowAnyException();
        }
    }
}
