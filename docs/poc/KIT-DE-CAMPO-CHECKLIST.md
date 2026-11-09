# ED-01 — Kit de campo: checklist de manutenção (pendência externa)

**Sessão produtora (Produtor):** `002855fb-1e26-4fcb-86e7-4b568c15a6eb`
**Data:** 09/11/2026

## 1. O que o aceite pede

Aceite da ED-01 (fonte única): "kit de campo mantido (Android, iPhone,
impressora Zebra série ZQ, bobinas, 4G)".

## 2. Checklist (itens do aceite × lastro de software local × estado físico)

| Item do kit (texto do aceite) | Lastro de software nas fontes locais | Estado físico |
|---|---|---|
| Aparelho **Android** | App de campo só Android por APK (plano §2, ADR-0026/ADR-0030); ED-18 (app de campo: autenticação, leitura, OS offline); ED-19 (emissão simultânea e impressão) | **[A DEFINIR]** — nenhum artefato local comprova existência, modelo ou guarda de aparelho físico |
| Aparelho **iPhone** | App do cliente Android **e iOS** nas lojas (ED-39; roteiro POC-55/SAMAE 2.24 exige 1 Android e 1 iOS reais) | **[A DEFINIR]** — idem |
| Impressora **Zebra série ZQ** | ED-19: "impressão em Zebra série ZQ por Bluetooth com leiaute térmico (ZPL e CPCL) do motor de modelos da P2.30… teste de cabeçote e bateria" | **[A DEFINIR]** — nenhum artefato local comprova aquisição/guarda da impressora |
| **Bobinas** (consumível térmico) | Leiaute térmico da ED-19 pressupõe bobina; quantidade/formato **[SEM FONTE]** local | **[A DEFINIR]** — aquisição e estoque são ato físico externo |
| Conectividade **4G** | Operação de campo online/offline (ED-18/ED-20); plano de dados/operadora **[SEM FONTE]** local | **[A DEFINIR]** — chip/linha é ato externo |

## 3. Natureza da pendência — P-ED01-01 (EXTERNA)

O kit é **físico**: aquisição, guarda, carga de bateria, estoque de
bobinas e linha 4G não se produzem nem se provam por pacote de
software, e **nenhuma fonte local** registra compra, inventário ou
custódia. Este produtor **não** declara o kit como existente nem como
mantido — declara o checklist pronto para a conferência física.

**Conferência física mínima antes de qualquer ensaio da ED-01**
(registrar no `REGISTRO-ENSAIO-ED-01.template.md`):

- [ ] Android presente, com o app de campo da versão do ensaio
      instalado e login de campo funcionando;
- [ ] iPhone presente, com o app do cliente da versão do ensaio
      instalado (roteiro da ED-39);
- [ ] Zebra série ZQ presente, pareada por Bluetooth com o Android,
      teste de cabeçote e bateria executado (roteiro da ED-19);
- [ ] Bobinas em quantidade suficiente para o ensaio completo
      (quantidade **[A DEFINIR]** por quem guardar o kit);
- [ ] 4G ativo nos aparelhos (ou indisponibilidade registrada como
      desvio do ensaio);
- [ ] Responsável pela guarda do kit nomeado: **[A DEFINIR]**.
