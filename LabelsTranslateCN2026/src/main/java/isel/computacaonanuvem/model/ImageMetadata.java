package isel.computacaonanuvem.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ImageMetadata {
    private String requestId;
    private String bucketName;
    private String blobName;
    private Date processingDate;
    private List<Map<String, Object>> labels; // Lista de labels (original, traduzida, confiança)
    private List<String> labelsList; // Apenas as labels traduzidas para pesquisa

    // Construtor vazio necessário para o Firestore
    public ImageMetadata() {}

    public ImageMetadata(String requestId, String bucketName, String blobName) {
        this.requestId = requestId;
        this.bucketName = bucketName;
        this.blobName = blobName;
    }

    // Getters e Setters (Essenciais para o Firestore conseguir ler/escrever)
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getBlobName() { return blobName; }
    public void setBlobName(String blobName) { this.blobName = blobName; }

    public Date getProcessingDate() { return processingDate; }
    public void setProcessingDate(Date processingDate) { this.processingDate = processingDate; }

    public List<Map<String, Object>> getLabels() { return labels; }
    public void setLabels(List<Map<String, Object>> labels) { this.labels = labels; }

    public List<String> getLabelsList() { return labelsList; }
    public void setLabelsList(List<String> labelsList) { this.labelsList = labelsList; }
}
