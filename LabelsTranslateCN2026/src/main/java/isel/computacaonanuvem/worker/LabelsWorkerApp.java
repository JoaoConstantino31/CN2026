package isel.computacaonanuvem.worker;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.auth.oauth2.ServiceAccountCredentials;

import static com.google.api.AnnotationsProto.http;

public class LabelsWorkerApp {

    public static void main(String[] args) {
        String projectId = config("GCP_PROJECT_ID", args.length > 0 ? args[0] : "cn2526-t3-g01");
        String subscriptionId = config("LABELS_SUBSCRIPTION", args.length > 1 ? args[1] : "image-processing-topic-sub");

        System.out.println("--- DIAGNÓSTICO ---");
        System.out.println("Projeto: " + System.getenv("GCP_PROJECT_ID"));
        System.out.println("Credenciais: " + System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
        System.out.println("-------------------");

        if (projectId.isBlank()) {
            System.err.println("Configura GCP_PROJECT_ID ou passa o projectId como primeiro argumento.");
            System.exit(1);
        }

        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);
        MessageReceiver receiver = LabelsWorkerApp::receiveMessage;
        Subscriber subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("A terminar worker...");
            subscriber.stopAsync();
        }));

        subscriber.startAsync().awaitRunning();
        System.out.println("Worker a escutar a subscricao " + subscriptionId);
        subscriber.awaitTerminated();
    }

    private static void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
        String messageText = message.getData().toStringUtf8();
        try {
            MessageProcessor.process(messageText);
            consumer.ack();
            System.out.println("Processado pedido: " + messageText);
        } catch (Exception e) {
            e.printStackTrace();
            consumer.nack();
        }
    }

    private static String config(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
