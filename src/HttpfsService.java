import java.util.HashMap;

public class HttpfsService {

    private static final int DEFAULT_PORT = 8080;
    private static final boolean DEFAULT_IS_DEBUG = false;
    public static final String DEFAULT_DIRECTORY = ".";

    private boolean isDebug;
    private int port;
    private String directory;
    private String[] args;

    public HttpfsService(String[] args) {
        this.isDebug = DEFAULT_IS_DEBUG;
        this.port = DEFAULT_PORT;
        this.directory = DEFAULT_DIRECTORY;
        this.args = args;
        this.initService();
    }

    public void initService() {
        for (int i = 0; i < this.args.length; i++) {
            if (this.args[i].equals("-v")) {
                this.isDebug = true;
            } else if (this.args[i].equals("-p")) {
                this.port = Integer.parseInt(this.args[++i]);
            } else if (this.args[i].equals("-d")) {
                this.directory = this.args[++i];
            }
        }
    }

    public void serve(){
        this.listening();
    }

    public void listening(){
        // Listen to the port
//       TODO: Write a HTTP listener
        // If the request comes, new a HttpfsServiceThread
        new HttpfsServiceThread(this, new Request("GET",new HashMap<>(), "",""));
    }

    public boolean isDebug() {
        return isDebug;
    }

    public int getPort() {
        return port;
    }

    public String getDirectory() {
        return directory;
    }
}

