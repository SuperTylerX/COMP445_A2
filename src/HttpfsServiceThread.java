import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;
import java.util.Scanner;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpfsServiceThread extends Thread {

    private String directory;
    private Path path;
    private File file;
    private Request request;
    private Response response;
    private Socket socket;
    private boolean isDebug;


    public HttpfsServiceThread(HttpfsService hfs, Socket socket) throws IOException {
        this.socket = socket;
        this.directory = hfs.getDirectory();
        this.isDebug = hfs.isDebug();
        this.init();
    }

    public void init() throws IOException {
        String requestString = getRequestString();

        if (isDebug) {
            System.out.println("\n>>>>>>>>>>>>>>>>>>>>>>>");
            System.out.println(requestString);
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>\n");
        }

        this.request = new Request(requestString);
        String filePath = request.getPath();
        this.path = Paths.get(directory + filePath);
        this.file = new File(path.toString());

    }

    public String getRequestString() throws IOException {

        InputStream inputStream = socket.getInputStream();
        // Create request reader
        StringBuilder requestString = new StringBuilder();
        int data = inputStream.read();
        StringBuilder line = new StringBuilder();

        boolean isBody = false;
        int content_length = 0;
        while (data != -1) {
            requestString.append((char) data);
            line.append((char) data);

            if (isBody) {
                if (line.length() == content_length) {
                    break;
                }
            } else {
                if (line.toString().contains("\r\n")) {
                    if (line.toString().toLowerCase().contains("content-length")) {
                        Pattern pattern = Pattern.compile("content-length:\\s*(\\d+)");
                        Matcher matcher = pattern.matcher(line.toString().toLowerCase());
                        if (matcher.find()) {
                            content_length = Integer.parseInt(matcher.group(1));
                        }
                    } else if (line.toString().equals("\r\n")) {
                        isBody = true;
                        if (content_length == 0) {
                            break;
                        }
                    }
                    line = new StringBuilder();
                }
            }

            data = inputStream.read();
        }

        return requestString.toString();
    }

    public void run() {

        if (isDebug) {
            System.out.println("[INFO] " + Thread.currentThread().getName() + " is created for processing the request");
        }

        if (!isInsideFolder()) {
            noPermissionResponseHandler();
        } else {
            if (request.getMethod().equals("GET")) {
                if (file.exists()) {
                    if (file.isDirectory()) {
                        readDirectoryHandler();
                    } else if (file.isFile()) {
                        readFileHandler();
                    }
                } else {
                    fileNotExistResponseHandler();
                }

            } else if (request.getMethod().equals("POST")) {
                if (file.exists() && file.isDirectory()) {
                    directoryAlreadyExistResponseHandler();
                } else {
                    writeFileHandler();
                }

            } else {
                methodNotAllowedResponseHandler();
            }
        }

        if (isDebug) {
            System.out.println("\n<<<<<<<<<<<<<<<<<<<<<<<<");
            System.out.println(this.response.toString());
            System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<\r\n");
        }

        try {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(response.toString().getBytes());
            outputStream.flush();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (isDebug) {
            System.out.println(Thread.currentThread().getName() + " finished");
        }

    }

    public void readDirectoryHandler() {

        String status = "200 OK";
        String body = "";
        HashMap headers = new HashMap<>();

        File[] tempFileList = file.listFiles();
        for (int i = 0; i < Objects.requireNonNull(tempFileList).length; i++) {
            if (tempFileList[i].isFile()) {
                body = body.concat("File: " + tempFileList[i].getName() + "\r\n");
            } else if (tempFileList[i].isDirectory()) {
                body = body.concat("Directory: " + tempFileList[i].getName() + "\r\n");
            }
        }
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void readFileHandler() {

        RandomAccessFile raf = null;
        FileChannel fc = null;
        FileLock fl = null;

        try {
            raf = new RandomAccessFile(this.file, "r");
            fc = raf.getChannel();
            while (true) {
                try {
                    fl = fc.tryLock(0, Long.MAX_VALUE, true);
                    if (fl != null) {
                        if (isDebug) {
//                            System.out.println("[INFO] File lock isShared: " + fl.isShared());
                            System.out.println("[INFO] " + Thread.currentThread().getName() + " get " + file.getName() + " shared read lock");
                        }
                    }

                    // Slow down the read file speed
                    try {
                        sleep(3000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }

                    break;
                } catch (Exception e) {
                    if (isDebug) {
                        sleep(1000);
                        System.out.println("[INFO] " + Thread.currentThread().getName() + " is waiting for " + file.getName() + " read lock");
                    }
                }
            }

            // Create Response
            String status = "200 OK";
            String body = "";
            HashMap headers = new HashMap<>();

            // Read the target file
            String fileContent = "";
            Scanner myReader = null;
            try {
                myReader = new Scanner(this.file);
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    fileContent = fileContent.concat(data);
                }
                myReader.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            body = fileContent;
            if (isDebug) {
                System.out.println("[INFO] " + Thread.currentThread().getName() + " reads successfully!");
            }

            //release lock
            fl.release();
            if (isDebug) {
                System.out.println("[INFO] " + Thread.currentThread().getName() + " releases file lock");
            }
            fc.close();
            raf.close();

            // Add header
            if (getMIME(path) != null) {
                headers.put("content-type", getMIME(path));
            }
            headers.put("content-disposition", "attachment; filename=" + file.getName());
            this.response = new Response(status, headers, body);

        } catch (Exception e) {
            e.printStackTrace();
            serverInternalErrorResponseHandler();
        } finally {
            if (fl != null && fl.isValid()) {
                try {
                    fl.release();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void writeFileHandler() {

        // If the parent folder does not exist, create the parent folder
        if (!file.exists()) {
            File parentFolder = new File(file.getParent());
            System.out.println(parentFolder);
            if (!parentFolder.exists()) {
                try {
                    boolean s = parentFolder.mkdirs();
                    System.out.println(s);
                } catch (SecurityException e) {
                    serverInternalErrorResponseHandler();
                    return;
                }
            }
        }

        RandomAccessFile raf = null;
        FileChannel fc = null;
        FileLock fl = null;
        InputStream in = null;

        try {
            raf = new RandomAccessFile(this.file, "rw");
            fc = raf.getChannel();
            try {
                fl = fc.tryLock();
                if (fl != null) {
                    if (isDebug) {
//                        System.out.println("[INFO] File lock isShared: " + fl.isShared());
                        System.out.println("[INFO] " + Thread.currentThread().getName() + " gets the " + file.getName() + " write lock");
                    }
                }
            } catch (Exception e) {
                if (isDebug) {
                    System.out.println("[INFO] " + Thread.currentThread().getName() + " is block");
                }
                fileIsLockResponseHandler();
                return;
            }

            if (isDebug) {
                System.out.println("[INFO] " + Thread.currentThread().getName() + " is writing " + file.getName());
            }

            // clear the file
            PrintWriter writer = new PrintWriter(file);
            writer.print("");
            writer.close();

            // write new content to the file
            in = new ByteArrayInputStream(this.request.getBody().getBytes());
            byte[] b = new byte[1024];
            int len = 0;
            ByteBuffer bb = ByteBuffer.allocate(1024);
            while ((len = in.read(b)) != -1) {
                bb.clear();
                bb.put(b, 0, len);
                bb.flip();
                fc.write(bb);
            }

            // Slow down the write speed
            Thread.sleep(5000);

            //release lock
            fl.release();
            fc.close();
            raf.close();

            if (isDebug) {
                System.out.println("[INFO] " + Thread.currentThread().getName() + " successfully wrote " + file.getName());
            }

            if (isDebug) {
                System.out.println("[INFO] " + Thread.currentThread().getName() + " releases lock");
            }
            String status = "200 OK";
            HashMap headers = new HashMap<>();
            headers.put("content-type", "text/plain");
            headers.put("content-disposition", "inline");
            String body = "Successfully written to file " + file.getName();
            this.response = new Response(status, headers, body);

        } catch (Exception e) {
            e.printStackTrace();
            serverInternalErrorResponseHandler();
        } finally {
            if (fl != null && fl.isValid()) {
                try {
                    fl.release();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void fileNotExistResponseHandler() {
        String status = "404 Not Found";
        String body = "404 File does not exist!";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);
    }

    public void noPermissionResponseHandler() {

        String status = "403 Forbidden";
        String body = "Forbidden";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);
    }

    public void directoryAlreadyExistResponseHandler() {
        String status = "403 Forbidden";
        String body = "The file could not be created because there is a folder with the same name";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void fileIsLockResponseHandler() {

        String status = "409 Conflict";
        String body = "Other thread is processing the file";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void methodNotAllowedResponseHandler() {

        String status = "405 Method Not Allowed";
        String body = "Method Not Allowed";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void serverInternalErrorResponseHandler() {

        String status = "500 Internal Server Error";
        String body = "Internal Server Error";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public static String getMIME(Path path) throws IOException {
        return Files.probeContentType(path);
    }

    public boolean isInsideFolder() {

        File directoryFile = new File(this.directory);
        File requestFile = this.file;

        try {
            String directoryFileCanonicalPath = directoryFile.getCanonicalPath();
            String requestFileCanonicalPath = requestFile.getCanonicalPath();
            return requestFileCanonicalPath.contains(directoryFileCanonicalPath);

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

    }

}
