# Checklist de aceite da carga real — LC.10

- **Sessão produtora:** `1fb44147-00cd-4ed1-a19a-ac6f72c78874` — papel: **Produtor**
- Cada item aponta para a cláusula do aceite da LC.10 (fonte:
  `tarefas-GL-P4-LC-PEND-C.json`, registro `LC.10`) ou para a
  pré-condição que a sustenta. Marcar somente com evidência na
  execução real. **Nenhum item está cumprido hoje** — não há cliente
  nem dado real (item 34 do `DECISOES-DO-DONO.md`).

## A. Pré-condições (portão — qualquer "não" impede o início)

- [ ] **LC.3 de pé:** RDS Multi-AZ + PITR com RPO/RTO medidos dentro do
  aceite; marcador de restauração anterior à carga registrado.
- [ ] **LC.4 de pé:** KMS/Secrets Manager de produção ativos; nenhum
  segredo/chave do cofre local reaproveitado.
- [ ] **LC.5 de pé:** S3 com Object Lock provado (recusa de apagar/
  sobrescrever XML retido testada) — sustenta a retenção da P2.19.
- [ ] **LC.9 assinado:** contrato SaaS, DPA e acordo de tratamento
  assinados com o cliente **antes do primeiro dado real**.
- [ ] **GL.4 decidido:** decisão formal sobre GL.2/GL.3 registrada.
- [ ] Cliente e legado reais existentes, com proveniência de cada
  extração registrada (fator externo — hoje inexistente, item 34).
- [ ] Adaptador de retenção da P2.19 ligado (`LegacyXmlRetentionPort`
  → P2.18 com origem `LEGACY_IMPORT`) — pendência de integração
  herdada da P2.19.
- [ ] Mapeamento(s) do legado versionados e testados (regra PLT-16:
  legado novo = mapeamento + testes, sem importador novo).

## B. Importadores sobre o dado real, só na produção

- [ ] **P1.2** (`LegacyImportService`, `V14_1`) executado sobre o dado
  real **na produção**; ensaio antes da aplicação; OS interna de lote
  registrada; registo de execução preenchido.
- [ ] **P2.10** (`V1ImportService`, `V14_2`) executado sobre o dado
  real **na produção**; janela de 120 meses aplicada; prestações a
  vencer como débitos a cobrar com referência ao acordo; registo de
  execução preenchido.
- [ ] **P2.19** (`LegacyXmlImportService`, `V14_3`) executado sobre os
  XML autorizados reais **na produção**; registo de execução
  preenchido.
- [ ] Nenhuma execução de importador com dado real fora da produção
  (conferido nos registos de execução).

## C. Conciliação e aceites do cliente

- [ ] Conciliação do cadastro por setor, rota, categoria e saldo —
  totais por setor e rota iguais aos do legado (aceite da P1.2).
- [ ] Saldo por matrícula igual ao do legado, prestações incluídas
  (aceite da P2.10); matrículas divergentes sem débito e explicadas.
- [ ] Contagem de XML por competência conferida com a contagem
  declarada pelo legado (aceite da P2.19).
- [ ] Toda divergência com explicação e disposição registradas.
- [ ] **Relatório de conciliação ASSINADO pelo piloto/cliente.**
- [ ] **Relatório de recusas de XML ACEITO pelo piloto/cliente**
  (recusas não aceitas listadas e em tratamento — nenhuma apagada).

## D. Guarda dos ambientes sem dado real

- [ ] Homologação na nuvem: só massa sintética (ou anonimizada na
  origem onde o plano prevê — GL.1), conferida.
- [ ] Demonstração na nuvem: só massa sintética, conferida.
- [ ] **Teste de varredura da P0.45 VERDE** executado sobre as bases
  de homologação e de demonstração, com resultado registrado
  (ambiente, data, massa, resultado).
- [ ] Nenhuma cópia do dado real da carga fora da produção.

## E. Fechamento

- [ ] Registos de execução dos três importadores arquivados e
  referenciados neste checklist.
- [ ] Carga registrada no diário de bordo do projeto.
- [ ] Pendências remanescentes nomeadas (não varridas): `[A DEFINIR]`
