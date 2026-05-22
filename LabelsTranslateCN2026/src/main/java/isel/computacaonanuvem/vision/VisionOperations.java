package isel.computacaonanuvem.vision;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;

public class VisionOperations {
    public static List<EntityAnnotation> detectLabels(byte[] imageBytes) throws Exception {
        List<AnnotateImageRequest> requests = new ArrayList<>();
        ByteString imgBytes = ByteString.copyFrom(imageBytes);

        Image img = Image.newBuilder().setContent(imgBytes).build();
        Feature feat = Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).build();
        AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
        requests.add(request);

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            return response.getResponsesList().get(0).getLabelAnnotationsList();
        }
    }
}
