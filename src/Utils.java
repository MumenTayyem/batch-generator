import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Utils {
    public static TaskResult<String> request(String endpoint){
        TaskResult<String> taskResult = new TaskResult<>();

        try{
            URL urlObject = new URL(endpoint);

            HttpURLConnection httpURLConnection = (HttpURLConnection) urlObject.openConnection();

            httpURLConnection.setRequestProperty("Authorization","Basic dGVzdEBsaWZlcmF5LmNvbTp0ZXN0MQo=");
            httpURLConnection.setRequestProperty("Accept","application/json");

            BufferedReader in = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));

            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            taskResult.setResult(response.toString());
        }catch (Exception ex){
            System.out.println(ex.getMessage());
            taskResult.setException(ex);
        }

        return taskResult;
    }
}
