package isel.computacaonanuvem.server;


import io.grpc.ServerBuilder;
import java.io.IOException;

public class LabelServer {
    private static int svcPort = 7500;

    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length > 0) svcPort = Integer.parseInt(args[0]);
        io.grpc.Server svc = ServerBuilder.forPort(svcPort)
                // Add one or more services.
                // The Server can host many services in same TCP/IP port
                .addService(new SFServiceImpl())
                .addService(new SGServiceImpl())
                .build();
        svc.start();
        System.out.println("Server started on port " + svcPort);
        // Java virtual machine shutdown hook
        // to capture normal or abnormal exits
        Runtime.getRuntime().addShutdownHook(new ShutdownHook(svc));
        // Waits for the server to become terminated
        svc.awaitTermination();
    }
}
