import jdk.swing.interop.SwingInterOpUtils;

import java.io.*;
import java.net.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.sql.SQLOutput;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReceiveHandler implements Runnable {

    private Lock l = new ReentrantLock();
    private DatagramPacket inPacket;
    private DatagramSocket socket;
    private String folderPath;
    private Map<Integer, TranferState> tfs;
    private List<FileIP> allFiles;
    private List<Boolean> syncronized;
    private int port;
    private final static int MTU = 5000;
    private List<Boolean> receivedFiles;
    private RecentlyUpdated recentlyUpdated;
    private boolean watch;
    private SendHandler sh;
    private List<Integer> seq;

    public ReceiveHandler(DatagramPacket inPacket, String folderPath, Map<Integer, TranferState> tfs,
            List<FileIP> allFiles, List<Boolean> syncronized, int port, List<Boolean> receivedFiles,
            RecentlyUpdated recentlyUpdated, boolean watch, List<Integer> seq) {

        this.inPacket = inPacket;

        this.port = port;

        this.syncronized = syncronized;

        this.allFiles = allFiles;

        try {
            this.socket = new DatagramSocket();

        } catch (Exception e) {
            e.printStackTrace();
        }

        this.tfs = tfs;

        this.folderPath = folderPath;

        this.receivedFiles = receivedFiles;

        this.recentlyUpdated = recentlyUpdated;

        this.watch = watch;

        this.sh = new SendHandler(this.socket);

        this.seq = seq;

    }

    public ReceiveHandler() {
        try {
            this.socket = new DatagramSocket();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getSeq() {
        this.l.lock();
        try {

            return this.seq.get(0);

        } finally {
            this.l.unlock();
        }
    }

    public void increaseSeq() {
        this.l.lock();
        try {

            int s = this.seq.get(0);
            this.seq.add(0, s + 1);

        } finally {
            this.l.unlock();
        }
    }

    public void getSyn(ByteBuffer bb) {

        int id = 0;

        int seq = bb.getInt();

        int length = bb.getInt();

        byte[] data = new byte[length];

        bb.get(data, 0, length);

        byte[] hmac = new byte[20];

        bb.get(hmac, 0, 20);

        // refactor the msg

        ByteBuffer msg = ByteBuffer.allocate(12 + length)
                .putInt(id)
                .putInt(seq)
                .putInt(length)
                .put(data);

        try {
            if (!Hmac.verifyHMAC(msg.array(), hmac))
                return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {

            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data));
            File[] files = (File[]) in.readObject();
            in.close();

            this.l.lock();

            for (int i = 0; i < files.length; i++) {

                FileIP fi = new FileIP(files[i], this.inPacket.getAddress().toString().substring(1));
                this.allFiles.add(fi);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.l.unlock();
        }

        this.sh.sendACK(this.inPacket.getAddress(), this.inPacket.getPort(), seq, 0);
        this.l.lock();
        try {
            this.syncronized.add(true);
        } finally {
            this.l.unlock();
        }

    }

    public void getRead(ByteBuffer bb) throws IOException {

        final int id = 4;
        int seq = bb.getInt();
        int length = bb.getInt();

        byte[] fileNameBytes = new byte[length];
        bb.get(fileNameBytes, 0, length);

        String fileName = new String(fileNameBytes);

        byte[] hmac = new byte[20];
        bb.get(hmac, 0, 20);

        // refactor the msg

        ByteBuffer msg = ByteBuffer.allocate(12 + fileName.length()).putInt(id).putInt(seq)
                .putInt(fileName.length())
                .put(fileNameBytes);

        try {
            if (!Hmac.verifyHMAC(msg.array(), hmac))
                return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.sh.sendACK(this.inPacket.getAddress(), this.inPacket.getPort(), seq, 0);

        File folder = new File(folderPath);

        String file = null;

        if (fileName.contains(folder.getName())) {

            file = folder.getParent() + "/" + fileName;
            File f = new File(file.trim());
            this.sh.sendFile(this.inPacket.getAddress().toString().substring(1), this.port, seq, f, false);

        } else {

            file = folderPath + "/" + fileName;
            File f = new File(file.trim());
            this.sh.sendFile(this.inPacket.getAddress().toString().substring(1), this.port, seq, f, true);
        }

        this.increaseSeq();

    }

    public void getWrite(ByteBuffer bb) {

        final int ident = 2;
        int seq = bb.getInt();
        int blocks = bb.getInt();
        int length = bb.getInt();

        byte[] fileNameBytes = new byte[length];
        bb.get(fileNameBytes, 0, length);

        byte[] hmac = new byte[20];
        bb.get(hmac, 0, 20);

        String fileName = new String(fileNameBytes);

        // refactor msg

        ByteBuffer msg = ByteBuffer.allocate(16 + fileName.length())
                .putInt(ident)
                .putInt(seq)
                .putInt(blocks)
                .putInt(length)
                .put(fileNameBytes);

        try {
            if (!Hmac.verifyHMAC(msg.array(), hmac))
                return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        TranferState tf = null;

        if (fileName.contains("/")) {
            String[] strings = fileName.split("/");
            tf = new TranferState(strings[1], blocks, strings[0]);
        } else {
            tf = new TranferState(fileName, blocks, null);
        }

        if (this.watch == true) {

            if (fileName.contains("/")) {
                String[] strings = fileName.split("/");
                this.recentlyUpdated.addFile(strings[1].trim());
            } else {
                this.recentlyUpdated.addFile(fileName.trim());
            }

        }

        this.l.lock();
        try {

            if (this.tfs.containsKey(seq)) {

                return;

            } else {
                this.tfs.put(seq, tf);
                this.sh.sendACK(this.inPacket.getAddress(), this.inPacket.getPort(), seq, 0);

            }

        } finally {
            this.l.unlock();
        }

        // receive all packets from the file

        while (!tf.isFinished()) {

            byte[] inBuffer = new byte[MTU];
            DatagramPacket packet = new DatagramPacket(inBuffer, inBuffer.length);

            try {

                this.socket.setSoTimeout(2000);
                this.socket.receive(packet);

            } catch (SocketTimeoutException e) {
                this.tfs.remove(seq);
                return;

            } catch (IOException e) {
                e.printStackTrace();
            }

            ByteBuffer bb2 = ByteBuffer.wrap(inBuffer);

            int id = bb2.getInt();

            if (id == 3)
                getData(bb2, packet.getAddress(), packet.getPort());

        }

        // registry the completed transfer

        this.l.lock();

        try

        {

            this.receivedFiles.add(true);

        } finally {
            this.l.unlock();
        }

    }

    public void getData(ByteBuffer bb, InetAddress ip, int port) {

        int id = 3;
        int seq = bb.getInt();
        int block = bb.getInt();
        int length = bb.getInt();

        byte[] data = new byte[length];

        bb.get(data, 0, length);

        byte[] hmac = new byte[20];

        bb.get(hmac, 0, 20);

        // refactor msg

        ByteBuffer msg = ByteBuffer.allocate(16 + data.length)
                .putInt(id)
                .putInt(seq)
                .putInt(block)
                .putInt(length)
                .put(data);

        try {
            if (!Hmac.verifyHMAC(msg.array(), hmac))
                return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.sh.sendACK(ip, port, seq, block);

        TranferState tf = null;

        this.l.lock();

        try {

            tf = this.tfs.get(seq);

            int expectedBlock = tf.getActualBlocks();

            if (block == expectedBlock) {

                tf.addBytes(data);
                tf.increaseBlocks();
            }

        } finally {

            this.l.unlock();
        }

        if (tf.isFinished()) {

            String path = this.folderPath + '/' + tf.getFileName();

            System.out.println("write on " + path);

            try {

                if (tf.existSubfolder()) {

                    String pathFolder = this.folderPath + "/" + tf.getSubFolder();

                    File f = new File(pathFolder.trim());
                    try {
                        f.mkdir();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                File outputFile = new File(path.trim());
                FileOutputStream outputStream = new FileOutputStream(outputFile);
                outputStream.write(tf.getBytes());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void run() {

        byte[] inBuffer = this.inPacket.getData();
        ByteBuffer bb = ByteBuffer.wrap(inBuffer);

        try {

            int identifier = bb.getInt();

            switch (identifier) {
                case 0:
                    getSyn(bb);
                    break;

                case 2:
                    getWrite(bb);
                    break;

                case 4:
                    try {
                        getRead(bb);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    System.out.println("NO MATCH");
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

}
