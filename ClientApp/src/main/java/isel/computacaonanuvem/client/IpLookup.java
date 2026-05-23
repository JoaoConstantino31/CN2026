package isel.computacaonanuvem.client;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IpLookup {
    private static final String ipLookupFunctionURL = "https://grpc-server-lookup-260130992637.europe-west6.run.app";

    public static List<String> getExternalIps(String projectId, String zone, String instanceGroup) throws IOException {
        String urlText = ipLookupFunctionURL
                + "?project=" + projectId
                + "&zone=" + zone
                + "&instanceGroup=" + instanceGroup;

        URL url = new URL(urlText);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000); // 10s Timeout
        connection.setReadTimeout(10000);

        int statusCode = connection.getResponseCode();
        if(statusCode != 200){
            throw new RuntimeException("Erro na ligação ao Cloud Run Lookup. Código HTTP: " + statusCode);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;

        while((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();
        connection.disconnect();

        return parseExternalIps(response.toString());
    }

    public static List<String> parseExternalIps(String response) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> result = gson.fromJson(response, type);

        List<String> ips = new ArrayList<>();
        List<Map<String, Object>> instances = (List<Map<String, Object>>) result.get("instances");

        if(instances == null) return ips;

        for(Map<String, Object> instance : instances) {
            Object externalIp = instance.get("externalIp");
            if(externalIp != null && !(externalIp.toString().equals("N/A"))) {
                ips.add(externalIp.toString());
            }
        }
        return ips;
    }
}
