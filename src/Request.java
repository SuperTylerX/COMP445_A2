import java.util.HashMap;

public class Request {

    String method;
    HashMap<String, String> headers;
    String body;
    String path;

    public Request(String method, HashMap<String, String> headers, String body, String path) {
        this.method = method;
        this.headers = headers;
        this.body = body;
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public String getPath() {
        return path;
    }
}
