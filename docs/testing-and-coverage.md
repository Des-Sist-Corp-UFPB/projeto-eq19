# Testes e cobertura

Resultados da execução atual do backend e do frontend. Os percentuais abaixo vêm diretamente dos relatórios gerados na execução atual de `jacoco.xml` e `coverage-summary.json`.

## Backend

Comando:

```bash
cd backend
mvn clean test jacoco:report
```

Resultado:

- 163 testes executados e aprovados;
- 0 falhas, 0 erros e 0 ignorados;
- instruções: 88%;
- branches: 66%;
- linhas: aproximadamente 89,7%;
- methods: aproximadamente 93,0%;
- classes: aproximadamente 97,4%;
- 114 classes analisadas;
- resultado: BUILD SUCCESS;
- JaCoCo confirmou: "All coverage checks have been met."

Relatórios versionados:

- [JaCoCo HTML](../cobertura/backend/jacoco/index.html)
- [JaCoCo XML](../cobertura/backend/jacoco/jacoco.xml)

Os testes cobrem controllers, autenticação, persistência e sincronização relacional,
repositórios, auditoria, cliente LiteLLM, geração e refinamento de rascunhos,
limites de consumo, retry e captura de tokens. A suíte de eventos usa PostgreSQL
real via Testcontainers para validar transações, fila, concorrência, rollback,
auditoria e a proteção contra sobrescrita por `/state`. Os testes de IA usam
clientes simulados ou servidores HTTP locais e não fazem chamadas reais à LiteLLM.

O teste `AuditLogPostgreSqlPrivilegesTest` utiliza Testcontainers para validar privilégios reais no PostgreSQL. Nesta execução o Docker estava disponível e o teste foi executado. Quando o Docker não está disponível, o JUnit o ignora automaticamente; nesse caso o total de ignorados aumenta em um, sem representar uma falha funcional.

## Frontend

Comando:

```bash
npm run test:coverage
```

Resultado:

- 15 arquivos de teste;
- 63 testes aprovados;
- 0 falhas;
- statements: 86,12%;
- branches: 77,35%;
- functions: 76,79%;
- lines: 88,30%.

Relatórios versionados:

- [Vitest/V8 HTML](../cobertura/frontend/index.html)
- [Resumo JSON](../cobertura/frontend/coverage-summary.json)

Os testes incluem contextos de autenticação e banco, páginas principais,
auditoria, carregamento e mutações de eventos pelos clientes específicos, além
dos fluxos de IA no formulário. A cobertura não implica validação de serviços
externos reais: PostgreSQL em container é usado nas suítes relacionais, e a
LiteLLM é sempre simulada.

Os limiares automatizados são 85% de statements e linhas no frontend e 85% de instruções no backend. Eles foram definidos somente após a suíte atingir esses valores com testes de comportamento.

## Validação rápida

```bash
cd backend
mvn test
cd ..
npm test
npm run lint
npm run build
git diff --check
```

Os relatórios gerados fora de `cobertura/`, como `backend/target/` e `coverage/`, são artefatos locais e não devem ser versionados.

## Migração relacional de partidas

Os testes PostgreSQL usam Testcontainers sem `disabledWithoutDocker` e cobrem
criação, consulta, listagem, autenticação, identidade do organizador,
participantes, vencedor, autorização de exclusão, auditoria e proteção contra
preservação dos dados relacionais durante upgrades. Os testes frontend verificam
o cliente de sessões e as operações específicas do domínio.
