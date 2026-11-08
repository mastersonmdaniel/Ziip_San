package br.com.ziipsan.commercial.collection.activedebt;

import java.util.List;

/**
 * Cobertura do item 2.15 do SAMAE no recorte da ED-31 (divida ativa). O
 * item 2.15 ("Cobranca") se reparte entre P4.1, ED-29, ED-30 e ED-31; este
 * registro cobre SOMENTE a fatia da ED-31, ligando cada exigencia do
 * aceite a funcionalidade deste pacote.
 *
 * <p>MARCADOR HONESTO: os codigos SG individuais da fatia de divida ativa
 * NAO foram confirmados nas fontes disponiveis nesta entrega (a ED-29
 * confirmou os seus 12 SG no seu recorte; os da ED-31 nao constam dos
 * pacotes locais consultados). Nenhum codigo SG e inventado aqui: as
 * linhas usam as exigencias do aceite da ED-31, e a atribuicao de codigos
 * SG fica PENDENTE de conferencia contra o roteiro do SAMAE.
 */
public final class Samae215DaCoverage {

    private Samae215DaCoverage() {}

    public record Entry(String requirement, String feature) {}

    public static final List<Entry> ED31_SLICE = List.of(
            new Entry("Inscricao em divida ativa individual e periodica (POC-34)", "ActiveDebtEnrollment (enrollIndividual/enrollPeriodic + Book)"),
            new Entry("Livro de divida ativa (POC-34/35)", "ActiveDebtEnrollment.Book (termos sequenciais, somente acrescimo)"),
            new Entry("CDA impressa pelo motor de modelos da P2.30 (POC-35)", "CdaDocument via DunningPorts.TemplatePort, reimpressao congelada"),
            new Entry("Parcelamento de divida ativa (POC-36)", "ActiveDebtInstallment (da.max_installments/da.min_installment)"),
            new Entry("Saldo mensal congelado", "ActiveDebtMonthlyClosing + recusa de inscricao em mes congelado"),
            new Entry("Arquivo e peticao-modelo para a procuradoria", "ProsecutionHandoff (modelos da P2.30)"),
            new Entry("Registro do ajuizamento sem conduzir o processo (ADR-0028 d)", "FilingRecord (numero, vara, fase registrada)"),
            new Entry("Valor minimo para ajuizar pela lei do ente", "da.min_filing_amount (obrigatorio, sem valor inventado) + FilingRecord.assertEligible"),
            new Entry("Alerta e bloqueio de prescricao (regra da P4.1, teste com relogio)", "PrescriptionGate com Clock injetado"),
            new Entry("Protesto de CDA sob parecer (PD-10), desligado por padrao", "ProtestControl (da.protest_enabled=false + parecer + prescricao)"));

    public static List<String> requirements() { return ED31_SLICE.stream().map(Entry::requirement).toList(); }
}
