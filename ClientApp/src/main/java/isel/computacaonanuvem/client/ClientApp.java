package isel.computacaonanuvem.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import isel.computacaonanuvem.FileList;
import isel.computacaonanuvem.ImageBlock;
import isel.computacaonanuvem.ImageId;
import isel.computacaonanuvem.ImageResult;
import isel.computacaonanuvem.LabelDetail;
import isel.computacaonanuvem.SFServiceGrpc;
import isel.computacaonanuvem.SGServiceGrpc;
import isel.computacaonanuvem.ScaleRequest;
import isel.computacaonanuvem.ScaleResponse;
import isel.computacaonanuvem.SearchRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ClientApp {

    private static final int BLOCK_SIZE = 64 * 1024;

    public static void main(String[] args) throws Exception {
        int svcPort = args.length > 1 ? Integer.parseInt(args[1]) : 7500;
        String svcIP = null;

        Scanner sc = new Scanner(System.in);

        System.out.println("=========================================================");
        System.out.println(" INICIALIZAÇÃO: Processo de obtenção dinâmica de IPs gRPC");
        System.out.println("=========================================================");

        ManagedChannel channel = null;
        boolean connected = false;

        // Repete até conseguir uma ligação e esteja funcional com uma VM
        while (!connected) {
            try {
                // 1. Invoca o Cloud Run passando os identificadores do teu laboratório
                List<String> ips = IpLookup.getExternalIps("cn2526-t3-g01", "europe-west6-a", "grcp-server-mig");
                // ips.add("127.0.0.1"); // Para teste local
                if (ips.isEmpty()) {
                    System.err.println("\nERRO CRÍTICO: Nenhum servidor gRPC está ativo de momento no grupo 'grcp-server-mig'!");
                    System.err.println("Garante que escalaste o grupo para tamanho >= 1 na Cloud Shell antes de correr o cliente.");
                    System.out.println("Premir ENTER para repetir o lookup ou introduza 'sair' ...");
                    String res = sc.nextLine().trim();
                    if (res.equalsIgnoreCase("sair")) {
                        return; // Aborta o programa
                    }
                    continue; // Volta ao topo do loop para repetir o lookup
                }

                // Mostra o menu de servidores encontrados dinamicamente
                System.out.println("\nServidores gRPC ativos detetados na Google Cloud:");
                for (int i = 0; i < ips.size(); i++) {
                    System.out.println("  [" + (i + 1) + "] -> Endereço IP: " + ips.get(i));
                }
                System.out.println("  [" + (ips.size() + 1) + "] -> Atualizar Lista");

                int option = 0;
                // Valida a escolha numérica do utilizador
                while (option < 1 || option > ips.size() + 1) {
                    System.out.print("\nSelecione o número do servidor gRPC ou atualize a lista: ");
                    try {
                        option = Integer.parseInt(sc.nextLine().trim());
                        if (option < 1 || option > ips.size() + 1) {
                            System.out.println("Opção inválida. Escolha um número entre 1 e " + (ips.size() + 1));
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Por favor, introduza um número válido.");
                    }
                }

                // Se o utilizador escolheu "Atualizar Lista", fazemos skip e voltamos ao topo do loop
                if (option == ips.size() + 1) {
                    System.out.println("A atualizar a lista...");
                    continue;
                }

                // Define o IP com base na escolha numérica do utilizador
                svcIP = ips.get(option - 1);
                System.out.println("\nA tentar estabelecer ligação de rede com: " + svcIP + ":" + svcPort + " ...");

                // Criação do Objeto de Canal gRPC (Lazy)
                channel = ManagedChannelBuilder.forAddress(svcIP, svcPort)
                        .usePlaintext()
                        .build();

                // FORÇAR A LIGAÇÃO FÍSICA IMEDIATA (Para cumprir o enunciado!)
                channel.getState(true); // Diz ao gRPC para ligar o Socket na rede já

                boolean isReady = false;
                long timeout = System.currentTimeMillis() + 4000; // Espera no máximo 4 segundos

                while (System.currentTimeMillis() < timeout) {
                    if (channel.getState(false) == io.grpc.ConnectivityState.READY) {
                        isReady = true;
                        break;
                    }
                    Thread.sleep(200); // Verifica a cada 200ms
                }

                if (isReady) {
                    System.out.println("Link estabelecido com sucesso");
                    connected = true; // Força a saída do loop
                } else {
                    System.err.println("\n FALHA: O servidor no IP " + svcIP + " não respondeu!");
                    System.err.println("A máquina pode ter sido interrompida. Vamos recomeçar o processo.");
                    channel.shutdownNow(); // Desliga o canal morto
                }

            } catch (Exception e) {
                System.err.println("\nFalha ao comunicar com o serviço de IP Lookup: " + e.getMessage());
                System.out.println("Premir [ENTER] para tentar novamente...");
                sc.nextLine();
            }
        }

        // Inicialização dos Stubs
        SFServiceGrpc.SFServiceBlockingStub sfBlockingStub = SFServiceGrpc.newBlockingStub(channel);
        SFServiceGrpc.SFServiceStub sfAsyncStub = SFServiceGrpc.newStub(channel);
        SGServiceGrpc.SGServiceBlockingStub sgBlockingStub = SGServiceGrpc.newBlockingStub(channel);

        System.out.println("\nIntroduza o seu nome de utilizador:");
        String username = sc.nextLine();

        try {
            while (true) {
                System.out.println("\nMENU " + username);
                System.out.println("1 - Enviar Imagem (Upload)");
                System.out.println("2 - Consultar Labels de uma Imagem");
                System.out.println("3 - Pesquisar Imagens por Label");
                System.out.println("4 - Escalar Servidores (SG)");
                System.out.println("5 - Escalar Workers (SG)");
                System.out.println("99 - Sair");

                String input = sc.nextLine();
                if (input.isBlank()) continue;

                try {
                    switch (Integer.parseInt(input)) {
                        case 1 -> uploadImage(sc, sfAsyncStub);
                        case 2 -> getLabels(sc, sfBlockingStub);
                        case 3 -> searchImages(sc, sfBlockingStub);
                        case 4 -> scale(sc, sgBlockingStub, true);
                        case 5 -> scale(sc, sgBlockingStub, false);
                        case 99 -> {
                            channel.shutdown();
                            return;
                        }
                        default -> System.out.println("Opcao invalida.");
                    }
                } catch (Exception e) {
                    System.err.println("Erro: " + e.getMessage());
                }
            }
        } finally {
            channel.shutdownNow();
        }
    }

    private static void uploadImage(Scanner sc, SFServiceGrpc.SFServiceStub sfAsyncStub) throws Exception {
        System.out.println("Caminho da imagem:");
        Path imagePath = Path.of(sc.nextLine().trim());
        byte[] content = Files.readAllBytes(imagePath);
        String filename = imagePath.getFileName().toString();

        CountDownLatch finishLatch = new CountDownLatch(1);
        AtomicReference<ImageId> response = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        StreamObserver<ImageId> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(ImageId imageId) {
                response.set(imageId);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        };

        StreamObserver<ImageBlock> requestObserver = sfAsyncStub.uploadImage(responseObserver);
        for (int offset = 0; offset < content.length; offset += BLOCK_SIZE) {
            int length = Math.min(BLOCK_SIZE, content.length - offset);
            requestObserver.onNext(ImageBlock.newBuilder()
                    .setFilename(filename)
                    .setData(ByteString.copyFrom(content, offset, length))
                    .build());
        }
        requestObserver.onCompleted();

        if (!finishLatch.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout no upload.");
        }
        if (error.get() != null) {
            throw new IllegalStateException(error.get().getMessage(), error.get());
        }

        System.out.println("Imagem enviada. ID: " + response.get().getId());
    }

    private static void getLabels(Scanner sc, SFServiceGrpc.SFServiceBlockingStub sfBlockingStub) {
        System.out.println("ID da imagem:");
        ImageResult result = sfBlockingStub.getLabels(ImageId.newBuilder().setId(sc.nextLine().trim()).build());

        System.out.println("Resultado para " + result.getId());
        if (result.hasProcessedDate()) {
            System.out.println("Processada em: " + Timestamps.toString(result.getProcessedDate()));
        }
        for (LabelDetail label : result.getLabelsList()) {
            System.out.printf("- %s / %s (%.2f)%n",
                    label.getEnglishLabel(),
                    label.getPortugueseLabel(),
                    label.getConfidence());
        }
    }

    private static void searchImages(Scanner sc, SFServiceGrpc.SFServiceBlockingStub sfBlockingStub) {
        System.out.println("Label:");
        String label = sc.nextLine().trim();
        System.out.println("Data inicial (yyyy-MM-dd, vazio para sem limite):");
        Timestamp startDate = parseDateOrDefault(sc.nextLine(), Timestamp.getDefaultInstance());
        System.out.println("Data final (yyyy-MM-dd, vazio para sem limite):");
        Timestamp endDate = parseDateOrDefault(sc.nextLine(), Timestamp.getDefaultInstance());

        FileList files = sfBlockingStub.searchImages(SearchRequest.newBuilder()
                .setLabels(label)
                .setStartDate(startDate)
                .setEndDate(endDate)
                .build());

        if (files.getFileNamesCount() == 0) {
            System.out.println("Sem imagens encontradas.");
            return;
        }
        files.getFileNamesList().forEach(fileName -> System.out.println("- " + fileName));
    }

    private static void scale(Scanner sc, SGServiceGrpc.SGServiceBlockingStub sgBlockingStub, boolean servers) {
        System.out.println("Numero alvo de instancias:");
        int targetInstances = Integer.parseInt(sc.nextLine().trim());
        ScaleResponse response = servers
                ? sgBlockingStub.scaleServers(ScaleRequest.newBuilder().setTargetInstances(targetInstances).build())
                : sgBlockingStub.scaleWorkers(ScaleRequest.newBuilder().setTargetInstances(targetInstances).build());
        System.out.println(response.getStatusMessage());
    }

    private static Timestamp parseDateOrDefault(String text, Timestamp defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        long millis = LocalDate.parse(text.trim())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return Timestamps.fromMillis(millis);
    }
}
