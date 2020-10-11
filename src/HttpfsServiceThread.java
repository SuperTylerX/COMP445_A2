import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

public class HttpfsServiceThread implements Runnable {

    private String directory;
    private String filePath;
    private Path path;
    private File file;
    private Request request;
    private Response response;


    public HttpfsServiceThread(HttpfsService hfs, Request request) {
        this.directory = hfs.getDirectory();
        this.filePath = request.getPath();
        this.path = Paths.get(directory + filePath);
        this.file = new File(path.toString());
        this.request = request;
        this.response = new Response();
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
            if (file.exists()) {
                if (file.isDirectory()) {
                    fileNotExistHandler();
                } else {
                    writeFileHandler();
                }
            } else {
                writeFileHandler();
            }

        } else {
            //todo make the request 405
            methodNotAllowedHandler();
        }
        System.out.println(this.response.toString());

    }

    public void readDirectoryHandler() {

        String status = "200 OK";
        String body = "";
        HashMap headers = new HashMap<>();

        System.out.println("Directory!");
        File[] tempFileList = file.listFiles();
        for (int i = 0; i < Objects.requireNonNull(tempFileList).length; i++) {
            if (tempFileList[i].isFile()) {
                body = body.concat("File: " + tempFileList[i].getName() + "\r\n");
                System.out.println("File: " + tempFileList[i].getName());
            } else if (tempFileList[i].isDirectory()) {
                body = body.concat("Directory: " + tempFileList[i].getName());

                System.out.println("Directory: " + tempFileList[i].getName());
            }
        }
        headers.put("content-type", "text/plain");
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);
    }


    public void readFileHandler() {
        String status = "200 OK";
        String body = "";
        HashMap headers = new HashMap<>();

        System.out.println("File!");
        // This is a sample of getting the MIME type for content-Type Header
        try {
            System.out.println(getMIME(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
//        TODO: Read a File
        String fileContent="";
        Scanner myReader = null;
        try {
            myReader = new Scanner(this.file);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                fileContent=fileContent.concat(data);

                System.out.println(data);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        body=fileContent;
        headers.put("content-type", "text/plain");
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "attachment; filename=\"new_file.txt\"");
        this.response=new Response(status,headers,body);

    }

    public void writeFileHandler() {
        System.out.println("Writing a file!");
        // TODO: Write to file
        try {
            FileWriter fr = new FileWriter(this.file, true);
            fr.write(this.request.getBody());
            fr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String status = "200 OK";
        HashMap headers = new HashMap<>();
        String body = "Write succesful";

        this.response = new Response(status, headers, body);

    }

    public void fileNotExistHandler() {
        System.out.println("File does not exist!");
        // TODO: Create a 404 Response
        String status = "404 Not Found";
        String body = "404 File does not exist!";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);
    }

    public static String getMIME(Path path) throws IOException {
        return Files.probeContentType(path);
    }

    public boolean isInsideFolder() {
        // TODO:
        return false;
    }

    public void noPermissionHandler() {
//        TODO: Create a 403 Response
        String status = "403 Forbidden";
        String body = "403 Forbidden";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void methodNotAllowedHandler() {
//        TODO: Create a 405 Response
        String status = "405 Method Not Allowed";
        String body = "405 Method Not Allowed";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "inline");

       this.response = new Response(status, headers, body);

    }
}
