# ED-43 — Chatbot e IA por modelo de linguagem de terceiro

Camada comum de IA sobre a conversa do omnichannel (ED-42) e as funções fechadas do portal (ED-38):

- **Porta de modelo sem provedor de fábrica:** provedor/modelo são configuração do tenant (`AiParameters.modelId`); credencial só pela referência do cofre (`vault://...`), nunca por valor — padrão da ED-42. Sem configuração, a IA não opera (fail-closed).
- **ADR-0042:** humano no circuito (transbordo sempre alcançável), auditoria mascarada da conversa, desligamento por tenant e por função (`ai.enabled_features`, classe b, padrão desligado), avaliação de modelo/prompt versionados e **nenhuma decisão exclusivamente automatizada** sobre corte, negativação ou tarifa social (GES-08) — tema detectado transborda e a revisão humana é travada (classe a).
- **OWASP LLM Top 10 2025:** injeção de instrução (guarda + transbordo), vazamento de dado sensível (máscara na entrada SG-700 e descarte na saída), agência excessiva (só funções fechadas, com a permissão do consumidor) e consumo ilimitado (cota de tokens + teto de custo por tenant; sem cota/teto configurados, sem chamada).
- **LGPD/P3.9 (RAS-20):** o provedor de LLM é suboperador — uso só com registro no DPA e no RIPD, vedação de treino com dado de tenant atestada e, se estrangeiro, cláusulas-padrão ANPD incorporadas (Res. CD/ANPD 19/2024); sem isso, o `AiProviderGate` bloqueia. Nenhum provedor/modelo/preço é nomeado nesta entrega.
- **Medição:** cada chamada grava `AI_TOKENS` no `UsageStore` da ED-41, módulo `ED-43`.
- **Conta (GES-26):** a explicação da variação é a função determinística da ED-38; a IA generativa só redige sobre ela, com `bill.explain_enabled`. Copiloto do atendente resume o histórico do protocolo com a mesma máscara. **OPE-40:** ditado (`assist.voice_enabled`/`assist.provider`, sem default) e rascunhos de parecer de OS (máx. 400 caracteres) e de resumo de turno, sempre revisados pelo executor.
- **Meta/WhatsApp:** bot restrito ao atendimento do prestador, sem assistente de propósito geral (política vigente desde 15/01/2026).
