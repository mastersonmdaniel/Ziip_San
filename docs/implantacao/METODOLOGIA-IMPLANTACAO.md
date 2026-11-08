# Metodologia de implantação — Ziip SAN (ED-09)

Documento publicado da metodologia. A jornada de implantação e os gates de
prontidão do console da plataforma (P1.6, PLT-21) seguem esta metodologia: as
chaves de passo são as de `platform.onboarding_step` e as do
`ImplantationPlan` do módulo da plataforma — documento e código usam o mesmo
vocabulário.

## Princípios

1. **Nada entra em produção sem aceite.** Parâmetros só valem aceitos e
   vigentes (P0.19); a produção é bloqueada enquanto houver passo
   obrigatório pendente (gate de prontidão).
2. **Migração é declarativa e conciliada.** Todo legado entra pelo framework
   declarativo da P1.2 (PLT-16): arquivo de mapeamento versionado, staging,
   ensaio sem gravar, conciliação por setor/rota/categoria/saldo e linhagem
   por registro. Legado novo = mapeamento novo + testes, nunca importador
   novo.
3. **Dado pessoal não circula em ambiente de ensaio.** Homologação e
   treinamento usam o clone mascarado (PLT-39) pelo roteiro de anonimização
   da P0.45, com varredura verde e avaliação de risco de reidentificação
   registrada no RIPD antes do primeiro clone.
4. **Tudo que atravessa ambiente viaja com prova.** Parâmetros entre
   homologação e produção viajam em pacote só de versões aceitas, com hash
   de conteúdo; divergência ou adulteração recusa a importação.

## Planos-modelo

Dois modelos, mesma sequência de passos obrigatórios (a divisão de dias
entre passos é o desenho do modelo; o plano concreto do tenant pode
redistribuir os dias dentro do total, validado contra o modelo):

| Passo (chave) | Gate | 60 dias | 90 dias |
|---|---|---|---|
| PARAMETERS_ACCEPTANCE — parâmetros pendentes de aceite | sim | 1–10 | 1–15 |
| LEGACY_IMPORT_AND_RECONCILIATION — importação do legado e conciliação | sim | 5–25 | 8–38 |
| MIGRATION_LINEAGE — linhagem da migração registrada | não | 20–27 | 30–40 |
| DATA_DICTIONARY — dicionário de dados gerado do schema | não | 25–30 | 38–45 |
| FISCAL_CERTIFICATE — certificado fiscal | sim | 10–20 | 15–30 |
| PSP_TEST — teste com o PSP | não | 20–35 | 30–52 |
| MFA_POLICY — política de MFA | sim | 30–40 | 45–60 |
| RESTORE_REHEARSAL — restauração ensaiada | não | 35–45 | 52–68 |
| DPO_NAMED — encarregado informado | não | 1–15 | 1–22 |
| DPA_SIGNED — DPA assinado | sim | 15–30 | 22–45 |
| TRAINING — treinamento | não | 40–60 | 60–90 |

## Etapas da metodologia

1. **Preparação** — parâmetros propostos e aceitos; encarregado informado;
   DPA assinado; política de MFA definida.
2. **Migração** — carga pelo framework declarativo (para o primeiro cliente
   de edital, o perfil GSAN da ED-09, se o legado for confirmado como GSAN),
   ensaio, conciliação limpa, aplicação por OS de lote (P1.31) e linhagem
   completa (nenhum registro aplicado sem linhagem, nenhuma linhagem órfã).
   O histórico de 120 meses é o da P2.10 e os documentos anteriores, os da
   P2.19 — esta metodologia os orquestra, não os reimplementa.
3. **Prontidão técnica** — certificado fiscal, teste com o PSP, restauração
   ensaiada e dicionário de dados gerado do schema no CI (CSV).
4. **Ambientes de ensaio** — clone mascarado para homologação/treinamento
   (gate do RIPD + anonimização P0.45 + varredura verde), parâmetros aceitos
   transferidos de homologação para produção pelo pacote com hash.
5. **Go-live** — gates de prontidão todos verdes no console, treinamento
   concluído e implantação simulada com tenant sintético já concluída dentro
   do plano-modelo de 60 dias.

## Clone mascarado (PLT-39)

- Nasce só da produção, para homologação ou treinamento.
- Exige avaliação do risco de reidentificação registrada no RIPD do tenant,
   anterior ao pedido e com conclusão viável.
- Anonimização (P0.45/ADR-0033): sem nome, documento, endereço nem contato;
  código substituto no lugar da matrícula (a correspondência fica só no
  piloto, fora do clone); cadastro técnico real; coordenada deslocada dentro
  da quadra sem coincidir com o lote de origem, ou agregada no centroide da
  quadra, com o método registrado por camada; tudo marcado como sintético.
- A varredura da P0.45 roda sobre o clone pronto: qualquer resíduo de dado
  pessoal reprova o clone.
