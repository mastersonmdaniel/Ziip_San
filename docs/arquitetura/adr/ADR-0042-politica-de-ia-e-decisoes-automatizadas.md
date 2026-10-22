# ADR-0042 — Política de IA e decisões automatizadas

- **Status:** Aceito, 22/10/2026
- **Decisor:** dono do produto (aprovação da recomendação dos levantamentos 22c e 22d em 22/10/2026)
- **Afeta:** ADR-0028 (item c, limites da IA); ADR-0033 (território nacional); ADR-0038 e ADR-0039; ED-43 (assistente virtual); plano v2.18; levantamentos `docs/levantamento/lacunas/22c` (PLT-18, PLT-38) e `22d` (GES-08, GES-24 a GES-26)

## Contexto

O plano prevê assistente virtual (ED-43) e os levantamentos 22b, 22c e 22d propõem outras funções com IA: previsão de inadimplência, analítica de fraude e perdas aparentes, explicação da conta, detecção de anomalia, leitura por foto, assistência por voz. Nenhuma tarefa define como o produto opera modelos, protege dado pessoal ou limita o efeito de uma decisão automatizada sobre o usuário. As vedações de corte (Lei 14.015/2020) e a tarifa social (Lei 14.898/2024) são federais, e a LGPD (art. 20) assegura a revisão de decisão tomada unicamente por tratamento automatizado.

## Decisão

1. **Nenhum modelo entra em produção antes desta política.** Toda função com IA cita este ADR e cumpre os itens abaixo.
2. **Humano no circuito.** A IA apoia, prioriza e explica; quem decide é pessoa. Nenhuma decisão automatizada produz efeito sobre o usuário sem revisão humana registrada.
3. **Vedações.** A IA não decide, sozinha, corte, religação, tarifa social, negativação, protesto, cobrança, parcelamento nem qualquer escore de acesso a serviço essencial. Pode ordenar filas e sugerir ação, sempre com revisão humana e registro (classe a, travado).
4. **Registro de uso.** Cada função tem ficha com finalidade, dados usados, base legal, versão do modelo, métrica de qualidade e responsável. A conversa ou a decisão fica em trilha de auditoria com a versão do modelo.
5. **Proteção de dado pessoal.** Mascaramento antes de enviar a provedor externo; o provedor não treina com dado do tenant; processamento em território nacional (ADR-0033).
6. **Desligamento.** Cada função liga e desliga por tenant (`ai.<função>.enabled`, padrão desligada, classe b com aceite) e há desligamento global pelo operador da plataforma.
7. **Avaliação antes de cada versão.** Conjunto de teste, limite de erro aceito e comparação com a versão anterior; viés avaliado por categoria e por localidade; resultado arquivado.
8. **Explicabilidade.** O usuário e o atendente veem o motivo da sugestão em linguagem simples; sugestão sem explicação não é exibida.
9. **Acompanhamento normativo.** O projeto de lei de IA em tramitação (PL 2.338/2023) é acompanhado; mudança normativa reabre este ADR.

## Consequências

### Positivas

- Limite claro do que a IA pode fazer, coerente com as vedações federais.
- Base única para ED-43, ED-94, ED-95 e as demais funções com IA.

### Negativas

- Mais trabalho antes do primeiro modelo: ficha, avaliação, auditoria e desligamento.
- Funções com IA ficam mais lentas de lançar.

## Alternativas consideradas

- Tratar cada função de IA em sua própria tarefa, sem política comum: rejeitada; repetiria decisões de proteção de dado e de efeito sobre o usuário.
- Permitir decisão automatizada para cobrança e priorização de corte: rejeitada; conflita com as vedações federais e com o direito de revisão.

## Tarefas derivadas

- ED-43 ajustada (PLT-18). ED-94 e ED-95 citam este ADR. Parâmetros `ai.*` no catálogo do tenant. Perfil de IA no Central Ziip: pendência de detalhamento na implementação.
