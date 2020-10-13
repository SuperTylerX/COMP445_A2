import java.io.*;
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

import static java.lang.Thread.sleep;

public class HttpfsServiceThread extends Thread {

    private String directory;
    private Path path;
    private File file;
    private Request request;
    private Response response;

    private boolean isDebug;


    public HttpfsServiceThread(HttpfsService hfs, Request request) {
        this.directory = hfs.getDirectory();
        String filePath = request.getPath();
        this.path = Paths.get(directory + filePath);
        this.file = new File(path.toString());
        this.request = request;
        this.response = new Response();

        this.isDebug = hfs.isDebug();

    }

    public void run() {

        if (!isInsideFolder()) {
            noPermissionResponseHandler();
        } else {
            if (request.getMethod().equals("GET")) {
                if (file.exists()) {
                    if (file.isDirectory()) {
                        readDirectoryHandler();
                    } else {
                        readFileHandler();
                    }
                } else {
                    fileNotExistResponseHandler();
                }

            } else if (request.getMethod().equals("POST")) {
                if (file.exists()) {
                    if (file.isDirectory()) {
                        fileNotExistResponseHandler();
                    } else {
                        writeFileHandler();
                    }
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
            //此处主要是针对多线程获取文件锁时轮询锁的状态。如果只是单纯获得锁的话，直接fl = fc.tryLock();即可
            while (true) {
                try {
                    //无参独占锁
                    //fl = fc.tryLock();
                    //采用共享锁
                    fl = fc.tryLock(0, Long.MAX_VALUE, true);
                    if (fl != null) {
                        if (isDebug) {
                            System.out.println("[INFO] File lock isShared: " + fl.isShared());
                            System.out.println("[INFO] " + Thread.currentThread().getName() + " get the shared read lock");
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
                    //如果是同一进程的多线程，重复请求tryLock()会抛出OverlappingFileLockException异常
                    if (isDebug) {
                        sleep(1000);
                        System.out.println("[INFO] " + Thread.currentThread().getName() + " is waiting");
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
            headers.put("content-type", getMIME(path));
            headers.put("content-disposition", "attachment; filename=" + file.getName());
            this.response = new Response(status, headers, body);

        } catch (Exception e) {
            e.printStackTrace();
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
                        System.out.println("[INFO] File lock isShared: " + fl.isShared());
                        System.out.println("[INFO]" + Thread.currentThread().getName() + " gets the write lock");
                    }
                }
            } catch (Exception e) {
                //如果是同一进程的多线程，重复请求tryLock()会抛出OverlappingFileLockException异常
                if (isDebug) {
                    System.out.println("[INFO] " + Thread.currentThread().getName() + " is block");
                }
                fileIsLockResponseHandler();
                return;
            }

            //获得文件锁权限后，进行相应的操作
            if (isDebug) {
                System.out.println("[INFO] " + Thread.currentThread().getName() + " is writing a file!");
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
            if (isDebug) {
//                System.out.println(this.threadName+" : release lock");
                System.out.println("[INFO]" + Thread.currentThread().getName() + " releases lock");
            }

            String status = "200 OK";
            HashMap headers = new HashMap<>();
            String body = "Successfully written to file " + file.getName();
            this.response = new Response(status, headers, body);

            if (isDebug) {
                System.out.println("[INFO]" + Thread.currentThread().getName() + "Successfully written to file " + file.getName());
            }

            fc.close();
            raf.close();
        } catch (Exception e) {
            e.printStackTrace();
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
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);
    }

    public void noPermissionResponseHandler() {

        String status = "403 Forbidden";
        String body = "Forbidden";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);
    }

    public void fileIsLockResponseHandler() {

        String status = "403 Forbidden";
        String body = "Other thread is processing the file";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void methodNotAllowedResponseHandler() {

        String status = "405 Method Not Allowed";
        String body = "Method Not Allowed";

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
        return !this.path.toString().contains("..");
    }

}
