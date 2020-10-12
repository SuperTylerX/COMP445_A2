import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import static java.lang.Thread.sleep;

public class HttpfsServiceThread extends Thread {

    private String directory;
    private String filePath;
    private Path path;
    private File file;
    private Request request;
    private Response response;

    private boolean isDebug;


    public HttpfsServiceThread(HttpfsService hfs, Request request) {
        this.directory = hfs.getDirectory();
        this.filePath = request.getPath();
//        this.path = Paths.get(filePath);
        this.path = Paths.get(directory + filePath);
        this.file = new File(path.toString());
        this.request = request;
        this.response = new Response();

        this.isDebug = hfs.isDebug();

    }

    public void run() {
        //muti thread part
        RandomAccessFile raf = null;
        FileChannel fc = null;
        FileLock fl = null;

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
        if (isDebug) {
            System.out.println(this.response.toString());
        }

    }

    public void readDirectoryHandler() {
        if (isInsideFolder()) {
            String status = "200 OK";
            String body = "";
            HashMap headers = new HashMap<>();
            if (isDebug) {
                System.out.println("Directory!");
            }
            File[] tempFileList = file.listFiles();
            for (int i = 0; i < Objects.requireNonNull(tempFileList).length; i++) {
                if (tempFileList[i].isFile()) {
                    body = body.concat("File: " + tempFileList[i].getName() + "\r\n");
                    if (isDebug) {
                        System.out.println("File: " + tempFileList[i].getName());
                    }
                } else if (tempFileList[i].isDirectory()) {
                    body = body.concat("Directory: " + tempFileList[i].getName());
                    if (isDebug) {
                        System.out.println("Directory: " + tempFileList[i].getName());
                    }
                }
            }
            headers.put("content-type", "text/plain");
            headers.put("content-length", String.valueOf(body.length()));
            headers.put("content-disposition", "inline");

            this.response = new Response(status, headers, body);
        } else {
            noPermissionHandler();
        }
    }


    public void readFileHandler() {

        if (!isInsideFolder()) {
            noPermissionHandler();
            return;
        }
        RandomAccessFile raf = null;
        FileChannel fc = null;
        FileLock fl = null;

        try {
            raf = new RandomAccessFile(this.file, "rw");
            fc = raf.getChannel();
            //此处主要是针对多线程获取文件锁时轮询锁的状态。如果只是单纯获得锁的话，直接fl = fc.tryLock();即可
            while(true) {
                try {
                    //无参独占锁
                    //fl = fc.tryLock();
                    //采用共享锁
                    fl = fc.tryLock(0, Long.MAX_VALUE, true);
                    if (fl != null) {
                        if (isDebug) {
                            System.out.println(fl.isShared());
                            System.out.println("get the shared lock");
                        }
                    }
                    try {
                        sleep(1000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    break;
                } catch (Exception e) {
                    //如果是同一进程的多线程，重复请求tryLock()会抛出OverlappingFileLockException异常
                    if (isDebug) {
                        sleep(1000);
                        System.out.println("current thread is waiting");
                    }
                }
            }
            //获得文件锁权限后，进行相应的操作
            String status = "200 OK";
            String body = "";
            HashMap headers = new HashMap<>();
            if (isDebug) {
                System.out.println("File!");
            }
            // This is a sample of getting the MIME type for content-Type Header
            try {
                if (isDebug) {
                    System.out.println(getMIME(path));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Read a File
            String fileContent = "";
            Scanner myReader = null;
            try {
                myReader = new Scanner(this.file);
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    fileContent = fileContent.concat(data);
                    if (isDebug) {
                        System.out.println(data);
                    }
                }
                myReader.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            body = fileContent;
            headers.put("content-type", "text/plain");
            headers.put("content-length", String.valueOf(body.length()));
            headers.put("content-disposition", "attachment; filename=\"new_file.txt\"");
            this.response = new Response(status, headers, body);
            if (isDebug) {
//                System.out.println(this.threadName+" : read success!");
                System.out.println("read success!");
            }
            //release lock
            fl.release();
            if (isDebug) {
//                System.out.println(this.threadName+" : release lock");
                System.out.println("release lock");
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

    public void writeFileHandler() {
        if (!isInsideFolder()) {
            noPermissionHandler();
            return;
        }
        RandomAccessFile raf = null;
        FileChannel fc = null;
        FileLock fl = null;
        InputStream in = null;

        try {
            raf = new RandomAccessFile(this.file, "rw");
            fc = raf.getChannel();
            try {
                //无参独占锁
                fl = fc.tryLock();
                if (fl != null) {
                    if (isDebug) {
                        System.out.println("Is shared: " + fl.isShared());
                        System.out.println("get the lock");
                    }
                }
            } catch (Exception e) {
                //如果是同一进程的多线程，重复请求tryLock()会抛出OverlappingFileLockException异常
                if (isDebug) {
                    System.out.println("current thread is block");
                }
                fileIsLockHandler();
                return;
            }

            //获得文件锁权限后，进行相应的操作
            if (isDebug) {
                System.out.println("Writing a file!");
            }
            //  Write to file
            // clear the file
            PrintWriter writer = new PrintWriter(file);
            writer.print("");
            writer.close();

            in = new ByteArrayInputStream(this.request.getBody().getBytes());

            byte[] b = new byte[1024];
            int len = 0;
            ByteBuffer bb = ByteBuffer.allocate(1024);
            while((len=in.read(b))!=-1){
                bb.clear();
                bb.put(b, 0, len);
                bb.flip();
                fc.write(bb);

            }
            Thread.sleep(5000);
            String status = "200 OK";
            HashMap headers = new HashMap<>();
            String body = "Write succesful";
            this.response = new Response(status, headers, body);
            if (isDebug) {
//                System.out.println(this.threadName+" : read success!");
                System.out.println("write success!");
            }
            //release lock
            fl.release();
            if (isDebug) {
//                System.out.println(this.threadName+" : release lock");
                System.out.println("release lock");
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

    public void fileNotExistHandler() {
        if (isDebug) {
            System.out.println("File does not exist!");
        }
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

        return true;
//        if (isDebug) {
//            System.out.println("valid path: " + this.directory);
//            System.out.println("filepath: " + this.filePath);
//            System.out.println(this.file);
//        }
//        if (this.directory.equals(HttpfsService.DEFAULT_DIRECTORY)) {
//            return true;
//        }
//        if (this.filePath.contains(this.directory)) {
//            return true;
//        } else {
//            return false;
//        }

    }

    public void noPermissionHandler() {
//        TODO: Create a 403 file forbidden Response
        String status = "403 file path control: Forbidden";
        String body = "403 file path control: Forbidden";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-length", String.valueOf(body.length()));
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void fileIsLockHandler() {
        String status = "403 Forbidden: other thread is processing the file";
        String body = "403 Forbidden: other thread is processing the file";

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
