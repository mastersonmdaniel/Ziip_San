package br.com.ziipsan.integration.esfinge;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Dados de origem da remessa e-Sfinge (ED-33) e a PORTA que os fornece.
 *
 * <p><b>Consistencia entre tarefas (nao duplicar):</b> a ED-33 nao tem
 * cadastro proprio. Os numeros tributarios vem das donas dos dados:
 * <ul>
 *   <li>divida ativa — ED-31 ({@code commercial/ziipsan-collection},
 *       pacote {@code ...collection.activedebt}: livro, inscricoes e saldo
 *       mensal congelado), resumida aqui em {@link ActiveDebtSnapshot};</li>
 *   <li>contabilidade — ED-32 ({@code commercial/ziipsan-accounting}:
 *       lancamentos por fato e balancete), resumida em
 *       {@link AccountingSnapshot} (totais do balancete REGULATORY do mes e
 *       a partida dobrada dele);</li>
 *   <li>os registros por tabela do leiaute chegam como valores textuais
 *       por codigo de campo do leiaute carregado — a ED-33 nao conhece
 *       tabela oficial nenhuma (ver EsfingeLayout); quem monta os
 *       registros a partir das fontes e a fiacao da integracao.</li>
 * </ul>
 * Cada valor carrega a referencia de origem (rastreabilidade, mesmo
 * principio da ED-32: {@code sourceReference} obrigatoria).
 */
public final class EsfingeSourceData {

    private EsfingeSourceData() {}

    /** Porta de dados: a fiacao liga ED-31/ED-32 (e o faturamento) a esta interface. */
    public interface EsfingeDataPort {
        RemittanceSourceData load(UUID tenantId, YearMonth competence);
    }

    /** Registro de uma tabela do leiaute: valores por codigo de campo (texto, como no arquivo). */
    public record DatasetRecord(String tableCode, Map<String, String> values, String sourceReference) {
        public DatasetRecord {
            if (tableCode == null || tableCode.isBlank()) throw new IllegalArgumentException("registro exige a tabela do leiaute");
            if (values == null) throw new IllegalArgumentException("registro exige os valores por campo");
            values = Map.copyOf(values);
            if (sourceReference == null || sourceReference.isBlank())
                throw new IllegalArgumentException("registro exige referencia de origem (rastreabilidade)");
        }
        public String value(String fieldCode) { return values.get(fieldCode); }
    }

    /**
     * Resumo da divida ativa no periodo, vindo da ED-31 (livro + saldo
     * mensal congelado de ActiveDebtMonthlyClosing). Valores monetarios
     * nao negativos; {@code frozen} diz se o saldo do mes esta congelado
     * na ED-31 — remessa sobre mes nao congelado e recusada na montagem
     * (a remessa declara numeros fechados, nao numeros em movimento).
     */
    public record ActiveDebtSnapshot(BigDecimal enrolledInPeriod, BigDecimal settledInPeriod,
                                     BigDecimal activeBalance, int enrollmentCount, boolean frozen) {
        public ActiveDebtSnapshot {
            for (BigDecimal v : new BigDecimal[]{enrolledInPeriod, settledInPeriod, activeBalance}) {
                Objects.requireNonNull(v, "componente do resumo de divida ativa e obrigatorio");
                if (v.signum() < 0) throw new IllegalArgumentException("componente do resumo de divida ativa nao pode ser negativo: " + v);
            }
            if (enrollmentCount < 0) throw new IllegalArgumentException("quantidade de inscricoes nao pode ser negativa");
        }
    }

    /**
     * Resumo contabil do periodo, vindo da ED-32 (balancete REGULATORY do
     * mes, TrialBalance). A partida dobrada (debito == credito) e regra da
     * ED-32; aqui ela e CONFERIDA de novo a partir dos totais, porque a
     * remessa nao pode declarar contabilidade desequilibrada.
     */
    public record AccountingSnapshot(BigDecimal totalDebit, BigDecimal totalCredit, int entryCount) {
        public AccountingSnapshot {
            Objects.requireNonNull(totalDebit, "total de debitos do balancete e obrigatorio");
            Objects.requireNonNull(totalCredit, "total de creditos do balancete e obrigatorio");
            if (totalDebit.signum() < 0 || totalCredit.signum() < 0)
                throw new IllegalArgumentException("totais do balancete nao podem ser negativos");
            if (entryCount < 0) throw new IllegalArgumentException("quantidade de lancamentos nao pode ser negativa");
        }
        public boolean balanced() { return totalDebit.compareTo(totalCredit) == 0; }
    }

    /** Pacote de origem de uma competencia: registros do leiaute + resumos das dependencias. */
    public record RemittanceSourceData(List<DatasetRecord> records, ActiveDebtSnapshot activeDebt,
                                       AccountingSnapshot accounting) {
        public RemittanceSourceData {
            if (records == null) throw new IllegalArgumentException("registros da remessa sao obrigatorios");
            records = List.copyOf(records);
            Objects.requireNonNull(activeDebt, "resumo da divida ativa (ED-31) e obrigatorio");
            Objects.requireNonNull(accounting, "resumo contabil (ED-32) e obrigatorio");
        }
        public List<DatasetRecord> recordsOf(String tableCode) {
            return records.stream().filter(r -> r.tableCode().equals(tableCode)).toList();
        }
    }
}
