package isel.computacaonanuvem.firestore;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class FirestoreOperations {
    private static final String COLLECTION = "labels_analysis";
    private static final String DATABASE_ID = "lab4-leirt-g1";

    private static final Firestore db = FirestoreOptions.newBuilder()
            .setDatabaseId(DATABASE_ID)
            .build()
            .getService();


    // O Servidor gRPC usa isto para ler o resultado
    public static DocumentSnapshot getResult(String requestId) throws Exception {
        return db.collection(COLLECTION).document(requestId).get().get();
    }

    // O Worker (Labels App) usa isto para guardar o resultado final
    public static void storeResult(String requestId, Map<String, Object> data) throws Exception {
        db.collection(COLLECTION).document(requestId).set(data).get();
    }

    public static List<QueryDocumentSnapshot> searchByLabelAndDate(String label, Date startDate, Date endDate) throws Exception {
        return db.collection(COLLECTION)
                .whereArrayContains("labelsList", label.toLowerCase())
                .whereGreaterThanOrEqualTo("processingDate", startDate)
                .whereLessThanOrEqualTo("processingDate", endDate)
                .get()
                .get()
                .getDocuments();
    }
}
