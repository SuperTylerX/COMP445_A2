import java.util.HashMap;

public class Test {
    public static void main(String[] args) {
//        Httpfs.main();
        HttpfsServiceThread hfst = new HttpfsServiceThread(new HttpfsService("-v -p 8080 -d ./test".split(" ")), new Request("GET", new HashMap<>(), "", "./test/A.txt"));
        hfst.run();
    }
}
