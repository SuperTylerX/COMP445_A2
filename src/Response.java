import java.util.HashMap;

public class Response {

    String status;
    HashMap<String, String> headers = new HashMap<>();
    String body;

    public Response(String status, HashMap<String, String> headers, String body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
