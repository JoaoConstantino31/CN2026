package isel.computacaonanuvem.worker;

import com.google.cloud.vision.v1.EntityAnnotation;
import isel.computacaonanuvem.firestore.FirestoreOperations;
import isel.computacaonanuvem.storage.StorageOperations;
import isel.computacaonanuvem.translate.TranslateOperations;
import isel.computacaonanuvem.vision.VisionOperations;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessageProcessor {

    public static void process(String messageText) throws Exception {
        String[] parts = messageText.split(";", 4);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Mensagem Pub/Sub invalida: " + messageText);
        }

        String requestId = parts[0];
        String bucketName = parts[1];
        String blobName = parts[2];
        String fileName = parts.length == 4 ? parts[3] : blobName;

        byte[] imageBytes = StorageOperations.downloadBlob(bucketName, blobName);
        if (imageBytes == null) {
            throw new IllegalStateException("Imagem nao encontrada no Storage: " + bucketName + "/" + blobName);
        }

        // 1. Obtemos todas as caraterísticas da Vision API de uma só vez
        List<EntityAnnotation> annotations = VisionOperations.detectLabels(imageBytes);

        // 2. Criamos uma lista temporária apenas com os textos em Inglês
        List<String> englishTexts = new ArrayList<>();
        for (EntityAnnotation annotation : annotations) {
            englishTexts.add(annotation.getDescription());
        }

        // 3. Enviamos TODAS as palavras para tradução numa ÚNICA chamada ao Google Translate
        List<String> portugueseTexts = new ArrayList<>();
        if (!englishTexts.isEmpty()) {
            portugueseTexts = TranslateOperations.translateListToPT(englishTexts);
        }

        // 4. Agora estruturamos os dados combinando o Inglês com o Português pelo índice (i)
        List<Map<String, Object>> labels = new ArrayList<>();
        List<String> labelsList = new ArrayList<>();

        for (int i = 0; i < annotations.size(); i++) {
            EntityAnnotation annotation = annotations.get(i);
            String englishLabel = annotation.getDescription();

            // Vai buscar a tradução correspondente à mesma posição do índice
            String portugueseLabel = (i < portugueseTexts.size()) ? portugueseTexts.get(i) : englishLabel;

            Map<String, Object> label = new HashMap<>();
            label.put("englishLabel", englishLabel);
            label.put("portugueseLabel", portugueseLabel);
            label.put("confidence", annotation.getScore());
            labels.add(label);

            addSearchLabel(labelsList, englishLabel);
            addSearchLabel(labelsList, portugueseLabel);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("bucketName", bucketName);
        data.put("blobName", blobName);
        data.put("fileName", fileName);
        data.put("processingDate", new Date());
        data.put("labels", labels);
        data.put("labelsList", labelsList);

        // 5. Guarda o mapa final no Firestore
        FirestoreOperations.storeResult(requestId, data);
    }

    private static void addSearchLabel(List<String> labelsList, String label) {
        if (label == null || label.isBlank()) {
            return;
        }
        String normalized = label.toLowerCase(Locale.ROOT);
        if (!labelsList.contains(normalized)) {
            labelsList.add(normalized);
        }
    }
}