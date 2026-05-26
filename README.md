>Este ficheiro README tem por objetivo documentar informações sobre configurações e funções de pastas/ficheiros , pressupostos de execução e testes.

# CN2026Labels

Sistema para deteção e tradução de labels em imagens utilizando Google Cloud Platform (GCP).

> Trabalho Final de Computação na Nuvem (ISEL, 2025/2026)

---

## Descrição

O CN2026Labels processa imagens de forma distribuída e assíncrona, permitindo:

- Deteção automática de labels em imagens
- Tradução de labels (EN → PT)
- Escalabilidade (workers + servidores)
- Processamento baseado em eventos (Pub/Sub)
- Comunicação via gRPC

---

## Arquitetura

Fluxo simplificado:

1. Cliente envia imagem (gRPC)
2. Imagem é armazenada em Cloud Storage
3. Mensagem publicada em Pub/Sub
4. Worker processa imagem (Vision API)
5. Labels são traduzidos (Translation API)
6. Resultados guardados em Firestore
7. Cliente consulta resultados (gRPC)

---

```text
CN2026/
├── ClientApp/                 # Cliente gRPC
├── grpcServer/                # Servidor principal
├── LabelsTranslateCN2026/     # Serviço de tradução e Servuço Vision
├── Proto/                     # Contrato Proto
└── README.md
```
---

## Componentes

### grpcServer

Expõe dois serviços:

#### Funcional (SF)

| Método | Descrição |
|--------|----------|
| uploadImage | Envia imagem |
| getLabels | Obtém labels |
| searchImages | Pesquisa por label/data |

#### Gestão (SG)

| Método | Descrição |
|--------|----------|
| scaleWorkers | Aumenta/Diminui workers |
| scaleServers | Aumenta/Diminui servers |

---

### ClientApp

- Descobre servidores via Lookup Function
- Envia imagens
- Consulta resultados
- Pesquisa imagens

---

### LabelsTranslateCN2026
- Tradução de labels (EN → PT)
- Integra Google Translation API
---

### Cloud Functions

**Lookup Function (HTTP)**
- Retorna IPs dos servidores gRPC
- Baseado em Instance Groups

**Logging Function (opcional)**
- Registo de pedidos no Firestore

---

## Requisitos

### Software

- Java 25
- Maven
- Conta no GCP

### Conta GCP

Projeto ativo com permissões:

- Storage
- Firestore
- Pub/Sub
- Compute Engine
- Cloud Functions
- Vision API
- Translation API

---

## Configuração

Variáveis de ambiente:
- export GOOGLE_APPLICATION_CREDENTIALS="/caminho/key.json"
- export GCP_PROJECT_ID="cn2526-t3-g01"
- export GCP_REGION="europe-west6"


---

## Execução

Ordem obrigatória:

1. Workers (LabelsWorkersApp)
2. Servidor gRPC (grpcServer)
3. Cliente (ClientApp)

---

## Testes

Para validar o sistema:

1. Submeter imagem via cliente
2. Confirmar criação no Cloud Storage
3. Verificar mensagem no Pub/Sub
4. Confirmar processamento no Firestore
5. Consultar resultado via GetLabels
6. Escalar e Descalar workers/servers
