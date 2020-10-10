import java.util.HashMap;

public class Test {
    public static void main(String[] args) {
//        Httpfs.main();
        HttpfsServiceThread hfst = new HttpfsServiceThread(new HttpfsService("-v -p 8080".split(" ")), new Request("POST", new HashMap<>(), "dasiodhsoaidhsoadh", "/a.txt"));
        hfst.run();
    }
}
