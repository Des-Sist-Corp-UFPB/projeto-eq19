# Testes e cobertura

Resultados gerados em 30 de julho de 2026 a partir do código atual. Os percentuais abaixo vêm diretamente de `jacoco.xml` e `coverage-summary.json`.

## Backend

Comando:

```bash
cd backend
mvn clean test jacoco:report
```

Resultado:

- 196 testes executados e aprovados;
- 0 falhas, 0 erros e 0 ignorados;
- 86,60% de instruções (13.520 de 15.612);
- 70,06% de branches (1.006 de 1.436);
- pacote `br.com.tabula.ai`: 90,65% de instruções (708 de 781) e 71,95% de branches (59 de 82).

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

- 14 arquivos de teste;
- 46 testes aprovados;
- statements: 87,01% (1.199 de 1.378);
- branches: 76,96% (508 de 660);
- functions: 76,98% (291 de 378);
- lines: 89,02% (1.119 de 1.257).

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
