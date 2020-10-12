import java.util.HashMap;

public class Test {
    public static void main(String[] args) {
//        Httpfs.main();
//        HttpfsServiceThread hfst = new HttpfsServiceThread(new HttpfsService("-v -p 8080 -d ./test".split(" ")), new Request("GET", new HashMap<>(), "", "/A.txt"));
//        HttpfsServiceThread hfst = new HttpfsServiceThread(new HttpfsService("-v -p 8080 -d ./test1".split(" ")), new Request("GET", new HashMap<>(), "", "/A.txt"));
//        HttpfsServiceThread hfst = new HttpfsServiceThread(new HttpfsService("-v -p 8080 -d ./test".split(" ")), new Request("POST", new HashMap<>(), "test write to A", "/C.txt"));
//        HttpfsServiceThread hfst2 = new HttpfsServiceThread(new HttpfsService("-v -p 8080 -d ./test".split(" ")), new Request("GET", new HashMap<>(), "", "/C.txt"));
//        HttpfsServiceThread hfst2 = new HttpfsServiceThread(new HttpfsService("-v -p 8080 -d ./test".split(" ")), new Request("POST", new HashMap<>(), "test write to A", "/C.txt"));
//
        HttpfsServiceThread hfst = new HttpfsServiceThread(new HttpfsService("-v -p 8080 -d ./test".split(" ")), new Request("POST", new HashMap<>(), "cccccccc", "/C.txt"));
//
        HttpfsServiceThread hfst2 = new HttpfsServiceThread(new HttpfsService("-v -p 8080 -d ./test".split(" ")), new Request("GET", new HashMap<>(), "", "/C.txt"));
//
        hfst.start();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("*********************************");
        hfst2.start();
    }
}
