import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RecentlyUpdated {

    private List<String> recentlyUpdated;
    private Lock l = new ReentrantLock();

    public RecentlyUpdated() {
        this.recentlyUpdated = new ArrayList<>();
    }

    public void addFile(String s) {

        this.l.lock();

        try {
            this.recentlyUpdated.add(s);
        }

        finally {
            l.unlock();
        }

    }

    public void removeFile(String s) {

        this.l.lock();

        try {
            this.recentlyUpdated.remove(s);
        }

        finally {
            l.unlock();
        }

    }

    public Boolean containsFile(String s) {

        this.l.lock();

        try {
            return this.recentlyUpdated.contains(s);
        }

        finally {
            l.unlock();
        }

    }

}