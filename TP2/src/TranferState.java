import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TranferState {

    private Lock l = new ReentrantLock();
    private String fileName;
    private String subfolder;
    private int totalBlocks;
    private int actualBlocks;
    private byte[] bytes;

    public TranferState(String fileName, int totalBlocks, String subfolder) {
        this.fileName = fileName;
        this.totalBlocks = totalBlocks;
        this.bytes = new byte[0];
        this.subfolder = subfolder;
        this.actualBlocks = 0;
    }

    public String getFileName() {
        this.l.lock();
        try {
            String fileName = null;

            if (this.subfolder == null) {
                fileName = this.fileName;
            } else {
                fileName = this.subfolder + "/" + this.fileName;
            }

            return fileName.trim();

        } finally {
            this.l.unlock();
        }
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getActualBlocks() {
        return actualBlocks;
    }

    public String getSubFolder() {
        return this.subfolder;
    }

    public void increaseBlocks() {
        this.l.lock();
        try {
            this.actualBlocks++;
        } finally {
            this.l.unlock();
        }
    }

    public byte[] getBytes() {
        this.l.lock();
        try {
            return bytes;
        } finally {
            this.l.unlock();
        }
    }

    public void addBytes(byte[] bytes) {

        this.l.lock();
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(this.bytes);
            outputStream.write(bytes);
            byte res[] = outputStream.toByteArray();
            this.bytes = res;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.l.unlock();
        }
    }

    public boolean isFinished() {

        Boolean res;
        this.l.lock();

        try {
            res = this.actualBlocks >= this.totalBlocks + 1;
        } finally {

            this.l.unlock();
        }
        return res;
    }

    public boolean existSubfolder() {
        boolean res = false;

        if (this.subfolder != null)
            res = true;

        return res;
    }

    public String toString() {
        return this.fileName + ' ' + this.actualBlocks;
    }

}
