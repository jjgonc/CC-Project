import java.awt.event.InputEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class FFSync {

    ReentrantLock lock = new ReentrantLock();
    private int port = 8888;
    private String folderPath;
    private List<String> ips;
    private List<FileIP> allFiles;
    private List<Integer> seq;
    private List<Boolean> syncronized;
    private List<Boolean> receivedFiles;
    private List<Boolean> watching;

    public FFSync(String folderPath, List<String> ips) {

        String currentPath = null;
        this.allFiles = new ArrayList<>();
        try {
            currentPath = new File(".").getCanonicalPath();
            this.folderPath = currentPath + "/" + folderPath;
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.seq = new ArrayList<>();
        this.seq.add(0);

        this.ips = ips;

        this.syncronized = new ArrayList<>();

        this.receivedFiles = new ArrayList<>();

        this.watching = new ArrayList<>();

    }

    public int getSeq() {
        this.lock.lock();
        try {

            return this.seq.get(0);

        } finally {
            this.lock.unlock();
        }
    }

    public void increaseSeq() {
        this.lock.lock();
        try {

            int s = this.seq.get(0);
            this.seq.add(0, s + 1);

        } finally {
            this.lock.unlock();
        }
    }

    public File[] getFilesWithoutFolder(String folderPath) {

        File folder = new File(folderPath);
        List<File> files = new ArrayList<>();

        for (File f : folder.listFiles()) {
            if (f.isDirectory()) {
                List<File> newFiles = Arrays.asList(getFilesWithFolder(f.getPath()));
                files.addAll(newFiles);
            } else {
                files.add(f);
            }
        }

        File[] res = new File[files.size()];
        files.toArray(res);

        return res;
    }

    public File[] getFilesWithFolder(String folderPath) {

        File folder = new File(folderPath);
        List<File> files = new ArrayList<>();

        for (File f : folder.listFiles()) {
            if (f.isDirectory()) {
                List<File> newFiles = Arrays.asList(getFilesWithoutFolder(f.getPath()));
                files.addAll(newFiles);
                files.add(f);
            } else {
                files.add(f);
            }
        }

        File[] res = new File[files.size()];
        files.toArray(res);

        return res;
    }

    public boolean isSync() {

        return this.ips.size() == this.syncronized.size();
    }

    public int containsFile(File f, List<File> files) {

        int i = 0;

        for (File file : files) {

            if (file.getName().equals(f.getName()))
                return i;

            i++;
        }

        return -1;

    }

    // calculate the files missing

    public List<FileIP> neededFilesCalculator(String folderPath) {

        List<FileIP> needed = new ArrayList<>();
        List<File> myFiles = Arrays.asList(this.getFilesWithoutFolder(folderPath));
        List<FileIP> res = new ArrayList<>();

        for (FileIP fi : this.allFiles) {

            List<String> aux = needed.stream().map(f -> f.getFile().getName()).collect(Collectors.toList());

            if (!aux.contains(fi.getFile().getName())) {

                // get where is the newer file

                FileIP newer = fi;

                for (FileIP file : this.allFiles) {

                    if (file.getFile().getName().equals(fi.getFile().getName())) {

                        if (file.getFile().lastModified() < fi.getFile().lastModified()) {
                            newer = file;
                        }
                    }
                }

                needed.add(newer);

            }
        }

        for (int i = 0; i < needed.size(); i++) {

            FileIP fip = needed.get(i);

            File file = fip.getFile();

            int index = containsFile(file, myFiles);

            if (index != -1) {
                File myFile = myFiles.get(index);
                if (myFile.lastModified() > file.lastModified()) {

                    res.add(fip);
                }
            } else {
                res.add(fip);

            }

        }

        return res;
    }

    // passar a lista de ficheiros em formato de uma lista de strings para ser
    // disposto no servidor HTTP

    public String[] fileArrayToStringArray(File[] files) {
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = "<b>" + files[i].getName() + "</b><br>";
        }
        return names;
    }

    public void createLogFile() {
        try {
            File logsFile = new File("logs.txt");
            if (logsFile.exists()) {
                System.out.println("logs.txt already exists...");
                System.out.println("Do you want to delete the existing one and create a new logs.txt? [y/n]");
                Scanner sc = new Scanner(System.in);
                String input = sc.nextLine();

                if (input.equals("y")) {
                    logsFile.delete();
                    logsFile.createNewFile();
                } else {
                    System.out.println("logs.txt not created. Using the existing one (may cause problems)");
                }

            } else {
                logsFile.createNewFile();
                System.out.println("logs.txt created successfully!!!");
            }
        } catch (IOException e) {
            System.out.println("An error ocurred while creating LogFile :(");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        // parse arguments
        String folder = args[0];
        List<String> ips = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            ips.add(args[i]);
        }
        FFSync ffSync = new FFSync(folder, ips);

        ffSync.createLogFile();

        // HTTP
        try {
            System.out.println("Starting HTTP server connection on localhost: 8080");

            File folderHTTP = new File(ffSync.folderPath);
            HttpServer httpServer = new HttpServer(8080, folderHTTP);
            Thread t1 = new Thread(httpServer);
            t1.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        RecentlyUpdated ru = new RecentlyUpdated();

        try {

            DatagramSocket requestSocket = new DatagramSocket(ffSync.port);

            FTR ftr = new FTR(requestSocket, ffSync.folderPath, ffSync.allFiles, ffSync.syncronized, ffSync.port,
                    ffSync.receivedFiles, ru, ffSync.watching, ffSync.ips, ffSync.seq);

            Thread t = new Thread(ftr);
            t.start();

            // Synchronize with all peers

            for (int i = 0; i < ffSync.ips.size(); i++) {

                SendHandler sh = new SendHandler(1, InetAddress.getByName(ffSync.ips.get(i)), ffSync.port,
                        ffSync.getSeq(),
                        ffSync.getFilesWithoutFolder(ffSync.folderPath));

                Thread syn = new Thread(sh);
                syn.start();
                ffSync.increaseSeq();

            }

            // waits for synchronization

            while (!ffSync.isSync()) {
                Thread.sleep(100);
            }
            List<FileIP> neededFiles = ffSync.neededFilesCalculator(ffSync.folderPath);

            for (FileIP fi : neededFiles) {

                String file = fi.getFile().getParentFile().getName() + "/" + fi.getFile().getName();

                System.out.println(file);

                SendHandler sh = new SendHandler(2, fi.getIp(), ffSync.port, ffSync.getSeq(),
                        file.trim());

                Thread read = new Thread(sh);
                read.start();
                ffSync.increaseSeq();
                System.out.println("Reading file:" + fi.getFile().getName());
            }

            // waits for the reception of all files

            while (ffSync.receivedFiles.size() != neededFiles.size()) {
                Thread.sleep(100);
            }

            ffSync.watching.add(true);

            System.out.println("\nInitial tranfer finished\n");

            // starts watching for modifications in the main folder

            MainWatch mw = new MainWatch(ffSync.folderPath, ffSync.ips, ffSync.port, ru, false, ffSync.seq);
            Thread mf = new Thread(mw);
            mf.start();

            // starts watching the subfolders

            for (File f : Arrays.asList(ffSync.getFilesWithFolder(ffSync.folderPath))) {

                if (f.isDirectory()) {

                    MainWatch sw = new MainWatch(f.getPath(), ffSync.ips, ffSync.port, ru, true, ffSync.seq);
                    Thread sf = new Thread(sw);
                    sf.start();
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
