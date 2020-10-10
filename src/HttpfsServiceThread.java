import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class HttpfsServiceThread implements Runnable {

    private String directory;
    private String filePath;
    private Path path;
    private File file;
    private Request request;

    public HttpfsServiceThread(HttpfsService hfs, Request request) {
        this.directory = hfs.getDirectory();
        this.filePath = request.getPath();
        this.path = Paths.get(directory + filePath);
        this.file = new File(path.toString());
        this.request = request;
    }

    public void run() {

        if (request.getMethod().equals("GET")) {
            if (file.exists()) {
                if (file.isDirectory()) {
                    readDirectoryHandler();
                } else {
                    readFileHandler();
                }
            } else {
                fileNotExistHandler();
            }

        } else if (request.getMethod().equals("POST")) {
            writeFileHandler();
        }

    }

    public void readDirectoryHandler() {
        System.out.println("Directory!");
        File[] tempFileList = file.listFiles();
        for (int i = 0; i < Objects.requireNonNull(tempFileList).length; i++) {
            if (tempFileList[i].isFile()) {
                System.out.println("File: " + tempFileList[i].getName());
            } else if (tempFileList[i].isDirectory()) {
                System.out.println("Directory: " + tempFileList[i].getName());
            }
        }
    }


    public void readFileHandler() {
        System.out.println("File!");

        // This is a sample of getting the MIME type for Content-Type Header
        try {
            System.out.println(getMIME(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
//        TODO: Read a File
    }

    public void writeFileHandler() {
        System.out.println("Writing a file!");
        // TODO: Write to file
    }

    public void fileNotExistHandler() {
        System.out.println("File does not exist!");
        // TODO: Create a 404 Response
    }

    public static String getMIME(Path path) throws IOException {
        return Files.probeContentType(path);
    }

    public boolean isInsideFolder(){
        // TODO:
        return false;
    }

    public void noPermissionHandler(){
//        TODO: Create a 404 Response
    }
}
