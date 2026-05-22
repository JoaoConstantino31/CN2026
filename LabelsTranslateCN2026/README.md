>Este ficheiro README tem por objetivo documentar informações sobre configurações e funções de pastas/ficheiros , pressupostos de execução e testes, para uma melhor compreensão e accesibilidade do utilizador

# Estrutura do Projeto

## server/

Responsável pela execução do servidor gRPC e exposição dos serviços.

- **GrpcServer**  
  Inicializa e arranca o servidor.

- **SFServiceImpl**  
  Implementa operações principais:
  - Upload de imagens  
  - Obtenção de labels  
  - Pesquisa

- **SGServiceImpl**  
  Responsável por lógica de escalabilidade.

---

## client/

Contém os clientes responsáveis por interagir com o servidor.

Funções:
- Envio de imagens
- Pedido de resultados ao sistema

---

## storage/

Integração com sistema de armazenamento (Cloud Storage).

Funções:
- Upload de imagens
- Download de imagens

---

## pubsub/

Responsável pela comunicação assíncrona entre componentes.

- **Publisher**  
  Envia mensagens para o sistema.

- **Subscriber**  
  Recebe mensagens e ativa o processamento (workers).

---

## firestore/

Base de dados do sistema.

Funções:
- Armazenamento de resultados
- Execução de queries (por label, data, etc.)

---

## vision/

Integração com a API de visão computacional.

Função:
- Deteção de labels em imagens (Google Vision API)

---

## translate/

Responsável pela tradução de labels.

Função:
- Tradução de inglês para português

---

## worker/

Camada de processamento assíncrono.

Fluxo:
1. Consome mensagens do Pub/Sub  
2. Processa imagens  
3. Invoca serviços de Vision e Translate  
4. Guarda resultados no Firestore  

---

## model/

Define as estruturas de dados internas do sistema.

Notas:
- Não exposto via gRPC  
- Utilizado para representação e manipulação interna de informação