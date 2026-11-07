package br.com.ziipsan.commercial.billing.printing;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Registro e leitura das contas nao entregues (P2.8, JOR-35). A leitura e a
 * API que a qualidade cadastral da P3.32 consome: ela nao escreve nestes
 * registros (correcao cadastral e por OS, regra dela) — so le os sinais.
 */
public interface UndeliveredBillService {

    /** Registra um retorno ja validado (motivo, data e origem completos). */
    void register(UndeliveredBill undelivered);

    /**
     * Importa o arquivo de retorno inteiro: ou entram todos os retornos ou
     * nenhum (o {@link ReturnFileParser} recusa o arquivo com a linha do erro).
     */
    List<UndeliveredBill> importReturnFile(String content);

    /** Historico de nao entrega da unidade, mais recente primeiro. */
    List<UndeliveredBill> returnsForUnit(UUID unitId);

    /** Retornos da janela, para a varredura da P3.32. */
    List<UndeliveredBill> returnsBetween(LocalDate from, LocalDate to);
}
