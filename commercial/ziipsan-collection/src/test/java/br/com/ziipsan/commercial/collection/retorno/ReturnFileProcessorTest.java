package br.com.ziipsan.commercial.collection.retorno;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.1 - ingestao do retorno: idempotencia por NSA + hash, CNPJ alfanumerico (P1.39)
 * e mes de arrecadacao. O store falso conta gravacoes: reprocessar nao pode gravar
 * de novo (e a prova de que a baixa nao duplica).
 */
class ReturnFileProcessorTest {

    /** CNPJ alfanumerico do exemplo oficial da P1.39 (12.ABC.345/01DE-35). */
    private static final String CNPJ_ALFA = "12ABC34501DE35";
    private static final UUID TENANT = UUID.randomUUID();

    static final class FakeStore implements ReturnFileProcessor.ReturnFileStore {
        final Map<String, UUID> byKey = new HashMap<>();
        int saves;

        @Override
        public Optional<UUID> findFileIdByNsaAndHash(UUID tenantId, String nsa, String fileHash) {
            return Optional.ofNullable(byKey.get(tenantId + "|" + nsa + "|" + fileHash));
        }

        @Override
        public UUID saveNewFile(UUID tenantId, ReturnFileProcessor.ReturnFileCommand command, String fileHash) {
            saves++;
            UUID id = UUID.randomUUID();
            byKey.put(tenantId + "|" + command.nsa() + "|" + fileHash, id);
            return id;
        }
    }

    private static ReturnFileProcessor.ReturnLine line(String payerDocument) {
        return new ReturnFileProcessor.ReturnLine(1, "23793381286000000000300000000000000000000000",
                "LOTERICA", "BOLETO", "1234", "AUTH-0001", payerDocument,
                new BigDecimal("100.00"), LocalDate.of(2026, 11, 5));
    }

    private static ReturnFileProcessor.ReturnFileCommand command(String nsa, String content, String receiver) {
        return new ReturnFileProcessor.ReturnFileCommand(nsa, "retorno-" + nsa + ".ret",
                ReturnFileProcessor.utf8(content), receiver, LocalDate.of(2026, 11, 6),
                List.of(line(CNPJ_ALFA)));
    }

    @Test
    void arquivoNovoEProcessadoComCnpjAlfanumerico() {
        FakeStore store = new FakeStore();
        ReturnFileProcessor processor = new ReturnFileProcessor(store);
        var outcome = processor.process(TENANT, command("101", "conteudo-a", CNPJ_ALFA));
        assertThat(outcome.resultado()).isEqualTo(ReturnFileProcessor.Resultado.PROCESSADO);
        assertThat(outcome.detailCount()).isEqualTo(1);
        assertThat(outcome.fileHash()).hasSize(64);
        assertThat(store.saves).isEqualTo(1);
    }

    @Test
    void reprocessamentoNaoDuplicaBaixa() {
        FakeStore store = new FakeStore();
        ReturnFileProcessor processor = new ReturnFileProcessor(store);
        var first = processor.process(TENANT, command("102", "conteudo-b", CNPJ_ALFA));
        var second = processor.process(TENANT, command("102", "conteudo-b", CNPJ_ALFA));
        assertThat(second.resultado()).isEqualTo(ReturnFileProcessor.Resultado.REPROCESSADO_SEM_EFEITO);
        assertThat(second.fileId()).isEqualTo(first.fileId());
        assertThat(store.saves).as("segunda gravacao duplicaria arquivo, detalhes e baixa").isEqualTo(1);
    }

    @Test
    void mesmoNsaComConteudoDiferenteEArquivoNovo() {
        FakeStore store = new FakeStore();
        ReturnFileProcessor processor = new ReturnFileProcessor(store);
        processor.process(TENANT, command("103", "conteudo-c", CNPJ_ALFA));
        var retificado = processor.process(TENANT, command("103", "conteudo-c-retificado", CNPJ_ALFA));
        assertThat(retificado.resultado()).isEqualTo(ReturnFileProcessor.Resultado.PROCESSADO);
        assertThat(store.saves).isEqualTo(2);
    }

    @Test
    void cnpjDoRecebedorInvalidoRecusaOArquivoInteiro() {
        ReturnFileProcessor processor = new ReturnFileProcessor(new FakeStore());
        // 11.222.333/0001-81 seria valido; o -82 tem o segundo DV errado de proposito.
        assertThatThrownBy(() -> processor.process(TENANT, command("104", "x", "11222333000182")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recebedor");
    }

    @Test
    void cnpjDoPagadorInvalidoRecusaOArquivoInteiro() {
        ReturnFileProcessor processor = new ReturnFileProcessor(new FakeStore());
        var cmd = new ReturnFileProcessor.ReturnFileCommand("105", "r.ret",
                ReturnFileProcessor.utf8("y"), CNPJ_ALFA, LocalDate.of(2026, 11, 6),
                List.of(line("00000000000000")));
        assertThatThrownBy(() -> processor.process(TENANT, cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linha 1");
    }

    @Test
    void mesDeArrecadacaoEDiaUmIndependenteDaCompetencia() {
        assertThat(ReturnFileProcessor.collectionMonthOf(LocalDate.of(2026, 11, 5)))
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(ReturnFileProcessor.collectionMonthOf(LocalDate.of(2027, 1, 31)))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
