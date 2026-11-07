---
code: "nfse:E0046"
title: "NFS-e cancelada não pode ser substituída"
suggest_ticket: false
---

# NFS-e cancelada não pode ser substituída

Uma NFS-e cancelada não pode ser substituída: a substituição foi pedida informando a chave de uma nota que já está cancelada. Para corrigir a operação, é preciso emitir uma nota nova, não substituir a cancelada.

## Causas prováveis

- A chave informada na substituição é a de uma NFS-e já cancelada.
- A nota foi cancelada enquanto a correção era preparada.

## O que fazer

- Emita uma NFS-e nova para a operação, com numeração própria.
- Confira o estado atual da nota pela chave antes de qualquer substituição.
