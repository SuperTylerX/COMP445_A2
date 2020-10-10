public class Httpfs {

    public static void main(String[] args) {
        HttpfsService hfs = new HttpfsService(args);
        hfs.serve();
    }
}
