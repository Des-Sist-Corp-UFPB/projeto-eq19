# Instrumentação OpenTelemetry Local (Zero-Code)

Este documento descreve como executar o Tabula com o Java Agent do OpenTelemetry e a imagem `grafana/otel-lgtm`, que reúne Collector, Grafana, Tempo, Loki e Prometheus.

## 1. Visão geral

A equipe usa `service.name=dsc-eq19` em todos os ambientes. Local e produção podem exportar para a mesma infraestrutura, sendo diferenciados por resource attributes:

```text
deployment.environment.name=local
service.version=<SHA local ou development>
```

O modo sem observabilidade continua funcionando sem depender do Collector.

## 2. Docker Compose com observabilidade

No PowerShell, a partir da raiz, informe o SHA atual e suba os containers:

```powershell
$env:SERVICE_VERSION = git rev-parse HEAD
docker compose -f docker/docker-compose.local.yml -f docker/docker-compose.observability.yml up --build -d
```

Se `SERVICE_VERSION` não for informado, o compose usa `development`. O override:

Quando o valor é obtido com `git rev-parse HEAD`, `service.version` identifica o último
commit Git. Alterações locais ainda não commitadas podem, portanto, estar sendo
executadas com o SHA desse commit.

1. inicia `otel-lgtm` nas portas `3000`, `4317` e `4318`;
2. constrói a aplicação com o Java Agent do `docker/Dockerfile`;
3. mantém `OTEL_SERVICE_NAME=dsc-eq19`;
4. usa `deployment.environment.name=local`;
5. passa o SHA informado como `service.version`.

Confira os containers:

```powershell
docker compose -f docker/docker-compose.local.yml -f docker/docker-compose.observability.yml ps
```

## 3. Execução manual fora do Docker

Inicie apenas PostgreSQL e LGTM:

```powershell
docker compose -f docker/docker-compose.local.yml -f docker/docker-compose.observability.yml up -d postgres otel-lgtm
```

Baixe o agente, que permanece ignorado pelo Git:

```powershell
Invoke-WebRequest -Uri "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.5.0/opentelemetry-javaagent.jar" -OutFile "backend/opentelemetry-javaagent.jar"
```

Configure o processo:

```powershell
$env:JAVA_TOOL_OPTIONS="-javaagent:opentelemetry-javaagent.jar"
$env:OTEL_SERVICE_NAME="dsc-eq19"
$env:OTEL_RESOURCE_ATTRIBUTES="deployment.environment.name=local,service.version=$(git rev-parse HEAD)"
$env:OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4318"
$env:OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf"
$env:OTEL_TRACES_EXPORTER="otlp"
$env:OTEL_METRICS_EXPORTER="otlp"
$env:OTEL_LOGS_EXPORTER="otlp"
cd backend
mvn clean package -DskipTests
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

## 4. Validação no Grafana/Tempo

Gere tráfego real, por exemplo:

```powershell
Invoke-RestMethod -Uri "http://localhost:8119/api/ping" -Method Get
Invoke-RestMethod -Uri "http://localhost:8119/api/state" -Method Get
```

Abra `http://localhost:3000`, selecione Explore e Tempo e filtre:

```text
service.name = dsc-eq19
deployment.environment.name = local
```

Inspecione o resource do trace para confirmar também `service.version`. O `traceId`
e os spans HTTP continuam correlacionados porque os novos valores são atributos
do resource, não novos identificadores.

## 5. Execução sem observabilidade

```powershell
docker compose -f docker/docker-compose.local.yml up --build
```

## 6. Segurança

- Não versionar `.env`, tokens, senhas ou credenciais.
- Não apontar o ambiente local para o endpoint remoto usando credenciais do professor.
- Não versionar `opentelemetry-javaagent.jar`.
- `deployment.environment.name` e o SHA de `service.version` não são sensíveis.
