package isel.computacaonanuvem.pubsub;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;

public class PubSubOperations {
    public static void publishImageNotification(String projectId, String topicId, String messageText) throws Exception {
        TopicName topicName = TopicName.of(projectId, topicId);
        Publisher publisher = Publisher.newBuilder(topicName).build();
        try {
            ByteString data = ByteString.copyFromUtf8(messageText);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();
            // Publica e aguarda a confirmação
            publisher.publish(pubsubMessage).get();
        } finally {
            publisher.shutdown();
        }
    }
}
