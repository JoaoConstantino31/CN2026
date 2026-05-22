package isel.computacaonanuvem.storage;

import com.google.cloud.storage.*;

public class StorageOperations {

    private static final Storage storage = StorageOptions.getDefaultInstance().getService();

    /**
     * Faz o upload de um array de bytes para o Cloud Storage.
     * Usado pelo Servidor gRPC no método submitImage.
     */
    public static void uploadBlob(String bucketName, String blobName, byte[] content) {
        uploadBlob(bucketName, blobName, content, "application/octet-stream");
    }

    public static void uploadBlob(String bucketName, String blobName, byte[] content, String contentType) {
        // Define o ID do blob (bucket + nome do ficheiro)
        BlobId blobId = BlobId.of(bucketName, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();

        // Cria o blob no bucket de uma só vez (apropriado para imagens de tamanho normal)
        storage.create(blobInfo, content);
        System.out.println("Upload concluído: " + blobName + " no bucket " + bucketName);
    }

    /**
     * Descarrega o conteúdo de um blob como um array de bytes.
     * Usado pelo Worker (Labels App) para processar a imagem.
     */
    public static byte[] downloadBlob(String bucketName, String blobName) {
        BlobId blobId = BlobId.of(bucketName, blobName);
        Blob blob = storage.get(blobId);

        if (blob == null) {
            System.err.println("Erro: O blob " + blobName + " não existe no bucket " + bucketName);
            return null;
        }

        // Lê todo o conteúdo para memória (usado para passar à Vision API)
        return blob.getContent();
    }

    /**
     * Torna um blob público (opcional, dependendo dos teus requisitos).
     */
    public static void makeBlobPublic(String bucketName, String blobName) {
        BlobId blobId = BlobId.of(bucketName, blobName);
        // Adiciona ACL para todos os utilizadores poderem ler
        storage.createAcl(blobId, Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
    }
}
