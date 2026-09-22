<!-- Generated: 2026-09-05 -->
# 00 · Architecture Overview

## 1. What we are building

MSB DigiBank is a mobile retail-banking app (React Native) whose key feature is **Mi**, an embedded assistant delivered in three levels: **Level 1** Q&A with RAG that guides customers through the app from CMS-managed documents; **Level 2** Chat Pay that *proposes* and, within user-granted permissions, *executes* banking actions (transfers, term deposits, bill payments, goal top-ups, card payments); **Level 3** a multi-agent assistant where a router hands off to domain agents (Loan, Card, Saving in the first milestone), all defined in the CMS. Every classic feature (transfer, saving, lending, cards, rewards) is also reachable through a conventional UI. The design has 32 screens; the prototype logic defines the rules those screens obey.

The backend described here is the **customer channel**. Systems of record (ledger, customers, deposits, loans, cards) are the bank's core banking and card systems, reached only through an integration wrapper with swappable adapters; draft mock systems stand in for them until go-live. The channel is a set of Spring Boot 4 services on Java 25, each packaged as a distroless container, exposed through one Spring Cloud Gateway, authenticated by Keycloak, persisted in PostgreSQL (with pgvector for Mi's retrieval store), and deployed as Custom Agent runtimes on GreenNode AgentBase. Mi's language model and embeddings are consumed from the GreenNode AI Platform OpenAI-compatible endpoint.
