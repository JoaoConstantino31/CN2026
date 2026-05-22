package isel.computacaonanuvem.server;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import isel.computacaonanuvem.FileList;
import isel.computacaonanuvem.ImageBlock;
import isel.computacaonanuvem.ImageId;
import isel.computacaonanuvem.ImageResult;
import isel.computacaonanuvem.LabelDetail;
import isel.computacaonanuvem.SFServiceGrpc;
import isel.computacaonanuvem.SearchRequest;
import isel.computacaonanuvem.firestore.FirestoreOperations;
import isel.computacaonanuvem.pubsub.PubSubOperations;
import isel.computacaonanuvem.storage.StorageOperations;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SFServiceImpl extends SFServiceGrpc.SFServiceImplBase {

    private final String projectId = config("GCP_PROJECT_ID", "cn2526-t3-g01");
    private final String bucketName = config("LABELS_BUCKET", "lab3-leirt-g1");
    private final String topicId = config("LABELS_TOPIC", "image-processing-topic");

    @Override
    public StreamObserver<ImageBlock> uploadImage(StreamObserver<ImageId> responseObserver) {
        return new StreamObserver<>() {
            private String filename;
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void onNext(ImageBlock block) {
                if (filename == null || filename.isBlank()) {
                    filename = block.getFilename();
                }
                try {
                    buffer.write(block.getData().toByteArray());
                } catch (Exception e) {
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("Erro ao receber bloco da imagem")
                            .withCause(e)
                            .asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                String id = UUID.randomUUID().toString();
                String originalFilename = filename == null || filename.isBlank() ? "image" : filename;
                String blobName = id + "/" + originalFilename;
                try {
                    StorageOperations.uploadBlob(bucketName, blobName, buffer.toByteArray(), contentType(originalFilename));
                    PubSubOperations.publishImageNotification(projectId, topicId,
                            id + ";" + bucketName + ";" + blobName + ";" + originalFilename);

                    responseObserver.onNext(ImageId.newBuilder().setId(id).build());
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    responseObserver.onError(internalError("Erro ao guardar imagem ou publicar pedido", e));
                }
            }
        };
    }

    @Override
    public void getLabels(ImageId imageId, StreamObserver<ImageResult> responseObserver) {
        try {
            DocumentSnapshot doc = FirestoreOperations.getResult(imageId.getId());
            if (!doc.exists()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Imagem ainda sem resultado ou ID inexistente")
                        .asRuntimeException());
                return;
            }

            ImageResult.Builder result = ImageResult.newBuilder().setId(doc.getId());
            Date processingDate = doc.getDate("processingDate");
            if (processingDate != null) {
                result.setProcessedDate(Timestamps.fromMillis(processingDate.getTime()));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> labels = (List<Map<String, Object>>) doc.get("labels");
            if (labels != null) {
                for (Map<String, Object> label : labels) {
                    result.addLabels(LabelDetail.newBuilder()
                            .setEnglishLabel(String.valueOf(label.getOrDefault("englishLabel", "")))
                            .setPortugueseLabel(String.valueOf(label.getOrDefault("portugueseLabel", "")))
                            .setConfidence(toFloat(label.get("confidence")))
                            .build());
                }
            }

            responseObserver.onNext(result.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Erro ao consultar resultado")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void searchImages(SearchRequest searchRequest, StreamObserver<FileList> responseObserver) {
        try {
            String label = searchRequest.getLabels().trim().toLowerCase();
            if (label.isBlank()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("A label de pesquisa e obrigatoria")
                        .asRuntimeException());
                return;
            }

            Date startDate = toDateOrDefault(searchRequest.getStartDate(), new Date(0));
            Date endDate = toDateOrDefault(searchRequest.getEndDate(), new Date(253402300799000L));

            FileList.Builder files = FileList.newBuilder();
            for (QueryDocumentSnapshot doc : FirestoreOperations.searchByLabelAndDate(label, startDate, endDate)) {
                String fileName = doc.getString("fileName");
                files.addFileNames(fileName != null ? fileName : doc.getString("blobName"));
            }

            responseObserver.onNext(files.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Erro ao pesquisar imagens")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private static String config(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static float toFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static Date toDateOrDefault(Timestamp timestamp, Date fallback) {
        if (timestamp.getSeconds() == 0 && timestamp.getNanos() == 0) {
            return fallback;
        }
        return new Date(Timestamps.toMillis(timestamp));
    }

    private static RuntimeException internalError(String message, Exception e) {
        e.printStackTrace();
        String detail = e.getMessage();
        return Status.INTERNAL
                .withDescription(detail == null || detail.isBlank() ? message : message + ": " + detail)
                .withCause(e)
                .asRuntimeException();
    }
}
