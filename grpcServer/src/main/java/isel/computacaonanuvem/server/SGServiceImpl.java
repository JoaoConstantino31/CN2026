package isel.computacaonanuvem.server;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import isel.computacaonanuvem.SGServiceGrpc;
import isel.computacaonanuvem.ScaleRequest;
import isel.computacaonanuvem.ScaleResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SGServiceImpl extends SGServiceGrpc.SGServiceImplBase {

    private final String projectId = config("GCP_PROJECT_ID", "cn2526-t3-g01");
    private final String zone = config("GCP_ZONE", "europe-west6-a");
    private final String serverGroup = config("SERVER_INSTANCE_GROUP", "grcp-server-mig");
    private final String workerGroup = config("WORKER_INSTANCE_GROUP", "labels-worker-mig");

    @Override
    public void scaleServers(ScaleRequest scaleRequest, StreamObserver<ScaleResponse> responseObserver) {
        scaleManagedInstanceGroup("servidores", serverGroup, scaleRequest, responseObserver);
    }

    @Override
    public void scaleWorkers(ScaleRequest scaleRequest, StreamObserver<ScaleResponse> responseObserver) {
        scaleManagedInstanceGroup("workers", workerGroup, scaleRequest, responseObserver);
    }

    private void scaleManagedInstanceGroup(
            String kind,
            String instanceGroup,
            ScaleRequest request,
            StreamObserver<ScaleResponse> responseObserver
    ) {
        int targetInstances = request.getTargetInstances();
        if (targetInstances < 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("O numero de instancias nao pode ser negativo")
                    .asRuntimeException());
            return;
        }

        if (projectId.isBlank() || zone.isBlank() || instanceGroup.isBlank()) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("Configura GCP_PROJECT_ID, GCP_ZONE e o instance group correspondente")
                    .asRuntimeException());
            return;
        }

        try {
            List<String> command = new ArrayList<>();
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                command.add("gcloud.cmd");
            } else {
                command.add("gcloud");
            }
            command.add("compute");
            command.add("instance-groups");
            command.add("managed");
            command.add("resize");
            command.add(instanceGroup);
            command.add("--size=" + targetInstances);
            command.add("--zone=" + zone);
            command.add("--project=" + projectId);
            command.add("--quiet");

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = readOutput(process);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Falha ao escalar " + kind + ": " + output)
                        .asRuntimeException());
                return;
            }

            responseObserver.onNext(ScaleResponse.newBuilder()
                    .setStatusMessage(kind + " escalados para " + targetInstances + " instancia(s)")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Erro ao escalar " + kind)
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private static String readOutput(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        return output.toString().trim();
    }

    private static String config(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
