package br.com.ziipsan.platform.ai;

import java.util.List;

/** Cobertura declarada: POC-71..74/76 e SG-685..SG-701. Tipo de evidencia HONESTO: NUCLEO = provado
 * localmente sobre portas/duble; POC/SG integrais sao ensaio externo (pendencia B no relatorio). */
public final class AiCoverage {
    public record Line(String code, String piece, String evidence) {}
    public static List<Line> lines() {
        return List.of(
            new Line("POC-71", "Chatbot sobre a conversa ED-42 com funcoes fechadas", "NUCLEO; ensaio POC = pendencia B"),
            new Line("POC-72", "Mascaramento e texto ao modelo sem CPF/CNPJ/nome/endereco (SG-700)", "NUCLEO (PiiMasker)"),
            new Line("POC-73", "Transbordo humano sempre alcancavel (P4.8+ED-35)", "NUCLEO (HumanHandover)"),
            new Line("POC-74", "Explicacao da conta deterministica ED-38 + redacao (GES-26)", "NUCLEO (BillExplainFunction)"),
            new Line("POC-76", "Copiloto do atendente e rascunhos OPE-40 revisados", "NUCLEO; ensaio POC = pendencia B"),
            new Line("SG-685..SG-698", "Intencao/sentimento/busca semantica via funcao fechada HELP_SEARCH", "NUCLEO parcial; atribuicao item a item [SEM FONTE]"),
            new Line("SG-699", "Comportamento anomalo", "NUCLEO (SentimentAndIntent.anomalous)"),
            new Line("SG-700", "Mascaramento inteligente", "NUCLEO (PiiMasker)"),
            new Line("SG-701", "Auditoria da conversa (log mascarado, retencao propria)", "NUCLEO (AiConversationLog)"),
            new Line("PLT-18", "Registro modelo/prompt, regressao CI, OWASP LLM, cota/teto, desligamento, sem treino", "NUCLEO"),
            new Line("GES-08/ADR-0042", "Sem decisao automatizada corte/negativacao/tarifa social; humano no circuito", "NUCLEO (ForbiddenDecision)"),
            new Line("RAS-20", "Provedor no DPA/RIPD; Brasil ou Res. CD/ANPD 19/2024 via P3.9", "NUCLEO (AiProviderGate); registro real = pendencia"));
    }
    private AiCoverage() {}
}
