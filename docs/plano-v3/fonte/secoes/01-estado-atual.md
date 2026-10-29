## 1. Estado atual (12/10/2026)

- Fundações E0–E6 concluídas em 12/10/2026 (alvo era 27/11): 973 testes Java e 134 do frontend verdes.
- Isolamento por tenant provado no PostgreSQL real: roles separadas, função da RLS de `sigsan_owner`, RLS FORCE, GUC
  definida e limpa pela conexão, tenant só do JWT.
- Auditoria sem dado pessoal; CPF/CNPJ cifrados com índice cego no cadastro e no MTR.
- Aplicação executável (Spring Boot 4.1.1, Keycloak 26.8.0, PKCE, API do cadastro), aceita de ponta a ponta.
- Cadastro de cliente pela tela bloqueado até o R2 (tipo de cliente, perfil de imóvel, endereço).
- Faturamento, medição e arrecadação: motores puros, sem serviço, API nem lote.
- Chave só de desenvolvimento; nenhum CPF real antes do R1.
- Só ambiente local: sem Dockerfile, CI remoto, backup nem observabilidade.
- GitHub privado `mastersonmdaniel/Ziip_San` (push manual até `fb2b53d`); código ainda editado no Google Drive.
- Sem cliente piloto definido. Hospedagem decidida em 12/10/2026: AWS `sa-east-1` gerenciada (ADR-0011), implantada só
  no lançamento comercial; até lá, desenvolvimento, homologação e demonstração locais, sem dado pessoal real (ADR-0033).
- Editais analisados (consolidado 18): dos 2.663 requisitos distintos da VISAN e do SAMAE, 3% atendidos, 21% em parte
  e 75% não atendidos; o produto hoje reprova nas duas provas de conceito.

**Histórico.** A v1 consolidou o PDCA de 12/10 (C1–C5) e o roadmap R0–R9 do `15c` em fundações E0–E6, com mapa Flyway e
portões de decisão. As etapas foram executadas no mesmo dia, um commit por etapa (diário §18). A revisão 17, sobre
`fb2b53d`, concluiu que o código está correto mas ainda não é serviço operável, o que motivou esta versão.

---

