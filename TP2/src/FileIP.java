import java.io.File;

public class FileIP {
    File file;
    String ip;

    public FileIP(File file, String ip){
        this.file = file;
        this.ip = ip;
    }

    public File getFile() {
        return file;
    }

    public String getIp() {
        return ip;
    }
}
