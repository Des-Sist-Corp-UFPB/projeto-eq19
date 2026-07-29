# Instrumentação OpenTelemetry Local (Zero-Code)

Este documento descreve como configurar, executar e validar a instrumentação OpenTelemetry em modo **zero-code** no ambiente de desenvolvimento local usando a imagem do Grafana `otel-lgtm` (que unifica OpenTelemetry Collector, Loki, Prometheus e Tempo).

---

## 1. Visão Geral

A equipe **dsc-eq19** utiliza instrumentação automática (zero-code) via Java Agent do OpenTelemetry. O backend exporta dados de telemetria (Traces, Metrics e Logs) via **OTLP HTTP/protobuf** para o coletor local.

No ambiente local, a configuração foi desenhada de modo a **não alterar o comportamento padrão de produção** e permitir que a aplicação continue funcionando normalmente sem depender do OpenTelemetry.

---

## 2. Inicialização com Docker Compose (Recomendado)

Esta é a forma mais fácil de rodar o ambiente completo com banco de dados, frontend, backend instrumentado e a stack de observabilidade.

### Passo 1: Subir o ambiente com Observabilidade
Execute o comando a seguir no PowerShell a partir da raiz do projeto para construir a imagem customizada e iniciar os containers:

```powershell
docker compose -f docker/docker-compose.local.yml -f docker/docker-compose.observability.yml up --build -d
```

Este comando:
1. Inicia o banco de dados PostgreSQL.
2. Inicia o contêiner `otel-lgtm` (Grafana, Tempo, etc.) exposto nas portas:
   - `3000`: Grafana UI
   - `4317`: OTLP gRPC
   - `4318`: OTLP HTTP
3. Compila a aplicação usando `docker/Dockerfile` que baixa o OpenTelemetry Java Agent de forma isolada.
4. Passa a variável `JAVA_TOOL_OPTIONS` com `-javaagent:/app/opentelemetry-javaagent.jar` e as configurações do OTLP para o contêiner `tabula`.

### Passo 2: Confirmar o funcionamento do contêiner
Para validar que o serviço de observabilidade está ativado e as portas estão corretas:

```powershell
docker compose -f docker/docker-compose.local.yml -f docker/docker-compose.observability.yml ps
```

---

## 3. Inicialização Local Manual (Fora do Docker / IDE)

Se você preferir executar o backend Java localmente fora do Docker (por exemplo, na sua IDE ou pelo terminal Java), siga os passos abaixo.

### Passo 1: Iniciar apenas a Stack de Observabilidade e o Banco
Inicie apenas o PostgreSQL e o contêiner de observabilidade:

```powershell
docker compose -f docker/docker-compose.local.yml -f docker/docker-compose.observability.yml up -d postgres otel-lgtm
```

### Passo 2: Baixar o OpenTelemetry Java Agent localmente
Baixe o jar do agente na raiz da pasta `backend`:

```powershell
Invoke-WebRequest -Uri "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.5.0/opentelemetry-javaagent.jar" -OutFile "backend/opentelemetry-javaagent.jar"
```
*(O arquivo `opentelemetry-javaagent.jar` está configurado no `.gitignore` para garantir que nunca seja commitado.)*

### Passo 3: Configurar Variáveis de Ambiente no PowerShell
Antes de iniciar a aplicação, configure as variáveis necessárias no terminal do PowerShell:

```powershell
$env:JAVA_TOOL_OPTIONS="-javaagent:opentelemetry-javaagent.jar"
$env:OTEL_SERVICE_NAME="dsc-eq19"
$env:OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4318"
$env:OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf"
$env:OTEL_TRACES_EXPORTER="otlp"
$env:OTEL_METRICS_EXPORTER="otlp"
$env:OTEL_LOGS_EXPORTER="otlp"
```

### Passo 4: Rodar o Backend Java
Com as variáveis de ambiente setadas no terminal, execute o build do backend e rode-o:

```powershell
cd backend
mvn clean package -DskipTests
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

---

## 4. Geração de Tráfego e Validação

Uma vez que a aplicação e o `otel-lgtm` estejam rodando:

1. **Gerar tráfego na API**:
   Faça requisições HTTP para os endpoints da aplicação:
   - Navegue pelo frontend acessando `http://localhost:8119` (se estiver no Docker) ou rodando o frontend via Vite (`http://localhost:5173`).
   - Ou envie requisições de teste diretamente usando o PowerShell:
     ```powershell
     Invoke-RestMethod -Uri "http://localhost:8119/api/live" -Method Get
     Invoke-RestMethod -Uri "http://localhost:8119/api/ping" -Method Get
     Invoke-RestMethod -Uri "http://localhost:8119/api/state" -Method Get
     ```

2. **Acessar o Grafana**:
   Abra o seu navegador e acesse:
   [http://localhost:3000](http://localhost:3000)

3. **Pesquisar Traces**:
   - Vá no menu lateral e selecione **Explore** (ícone de bússola).
   - No dropdown de Data Source (canto superior esquerdo), escolha **Tempo**.
   - Na aba de buscas, selecione a opção de pesquisa por tags/campos e insira ou selecione:
     `service.name = dsc-eq19`
   - Clique em **Run query** para ver a lista de spans e requisições capturadas automaticamente (ex: requisições HTTP recebidas pelo Javalin, conexões JDBC, etc.).

---

## 5. Rodando o Projeto Sem Observabilidade (Modo Normal)

Para iniciar o projeto normalmente sem ativar a instrumentação e sem rodar a stack otel-lgtm, execute apenas o compose local padrão:

```powershell
docker compose -f docker/docker-compose.local.yml up --build
```
A aplicação iniciará e funcionará normalmente, garantindo que não há dependências obrigatórias com o OpenTelemetry para execução do sistema.

---

## 6. Boas Práticas e Segurança

> [!CAUTION]
> **REGRAS DE SEGURANÇA E COMIT:**
> 1. **Não adicione o token do professor** nem configure endpoints remotos como `https://otel.dsc.rodrigor.com` nesta etapa.
> 2. **Não versionar arquivos `.env`**, senhas locais ou tokens de autenticação.
> 3. O arquivo `opentelemetry-javaagent.jar` **não deve ser commitado** se baixado localmente.
