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
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ClientApp {

    private static final int BLOCK_SIZE = 64 * 1024;

    public static void main(String[] args) throws Exception {
        String svcIP = args.length > 0 ? args[0] : "localhost";
        int svcPort = args.length > 1 ? Integer.parseInt(args[1]) : 7500;

        ManagedChannel channel = ManagedChannelBuilder.forAddress(svcIP, svcPort)
                .usePlaintext()
                .build();

        SFServiceGrpc.SFServiceBlockingStub sfBlockingStub = SFServiceGrpc.newBlockingStub(channel);
        SFServiceGrpc.SFServiceStub sfAsyncStub = SFServiceGrpc.newStub(channel);
        SGServiceGrpc.SGServiceBlockingStub sgBlockingStub = SGServiceGrpc.newBlockingStub(channel);

        Scanner sc = new Scanner(System.in);
        System.out.println("Introduza o seu nome de utilizador:");
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
