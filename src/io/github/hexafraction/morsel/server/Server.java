package io.github.hexafraction.morsel.server;

import com.bugsnag.Client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class Server {
    static Client bugsnagClient;
    final ReentrantReadWriteLock connectionManagerLock;
    public Server() {
        Path serPath = FileSystems.getDefault().getPath("morsel-store.ser");
        File serFile = serPath.toFile();
        SerializedServerState st = null;
        if (serFile.exists() && serFile.canRead() && serFile.canWrite()) {
            try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(serFile))) {

                st = (SerializedServerState) ois.readObject();

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                bugsnagClient.notify(e);
                st = new SerializedServerState();
            } finally{

            }
        }
        if (st != null) {
            this.state = st;
        } else {
            this.state = new SerializedServerState();
        }

        connectionManagerLock = new ReentrantReadWriteLock(true);
    }

    public static void main(String[] args) throws IOException {

        bugsnagClient = new Client("8458911bab24e848943560e73845130d");
        new Server().run();
    }

    public void run()  {
        ServerSocket srv = null;
        try {
            srv = new ServerSocket(18718, 8);
            srv.setReuseAddress(true);
        } catch (IOException e) {
            bugsnagClient.notify(e);
            e.printStackTrace();
            System.exit(4);
        }
        Thread cleanupAndPing = new Thread(new KeepaliveRunnable(), "keepalive");
        cleanupAndPing.start();
        Runtime.getRuntime().addShutdownHook(new Thread(Server.this::saveData));

        while (true) {
            UserConnection uc = null;
            try {
                uc = new UserConnection(srv.accept(), this);
            } catch (IOException e) {
                e.printStackTrace();
                bugsnagClient.notify(e);
            }

            try {
                connectionManagerLock.writeLock().lock();
                connections.add(uc);
            } finally {
                connectionManagerLock.writeLock().unlock();

            }
        }
    }

    private void saveData() {
        System.out.println("SAVING");
        try {
            Path serPathNew = FileSystems.getDefault().getPath("morsel-store.sernew-"+(System.currentTimeMillis()%1000000));
            File serFileNew = serPathNew.toFile();
            Path serPath = FileSystems.getDefault().getPath("morsel-store.ser");

            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(serFileNew));
            oos.writeObject(Server.this.state);
            oos.flush();
            oos.close();
            Files.move(serPathNew, serPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.out.println("Warning: Failed to save. History may be unavailable.");
            bugsnagClient.notify(e);
            e.printStackTrace();
        }
    }

    final SerializedServerState state;
    HashSet<UserConnection> connections = new HashSet<>();

    Set<UserConnection> getOnChannel(ChatRoom cr) {

        return connections.stream().filter((conn) -> conn!=null && conn.getRoom() == cr).collect(Collectors.toSet());
    }

    private class KeepaliveRunnable implements Runnable {

        @Override
        public void run() {
            int counts = 0;
            outer: while (true) {
                Iterator<UserConnection> uci = connections.iterator();
                while (uci.hasNext()) {
                    UserConnection next = uci.next();
                    if(next==null) continue outer;
                    if (!next.isValid() || System.currentTimeMillis() - next.lastHeard > 30000) {
                        next.stop();
                        uci.remove();
                    } else {
                        next.sendChar('p');
                    }
                }

                counts = (counts + 1) % 10;
                if (counts == 0) {
                    saveData();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    @Deprecated
    private static class HandlerRunnable implements Runnable {
        Socket sc;

        public HandlerRunnable(Socket sc) throws SocketException {
            this.sc = sc;
            sc.setTcpNoDelay(true);
            sc.setKeepAlive(true);
        }

        @Override
        public void run() {
            try {
                System.out.println("Hello!");
                DataInputStream dis = new DataInputStream(sc.getInputStream());
                DataOutputStream dos = new DataOutputStream(sc.getOutputStream());
                dos.writeInt(0x64607012);
                dos.flush();
                int cMagic = dis.readInt();
                if (cMagic != 0x77345466) {
                    sc.close();
                    System.out.println("Closing.");
                    return;
                }
                System.out.println("Got magic.");
                dos.writeInt(21);
                dos.writeUTF("chan-alpha");
                dos.writeUTF("chan-beta");
                dos.writeUTF("chan-gamma");
                dos.writeUTF("chan-alpha");
                dos.writeUTF("chan-beta");
                dos.writeUTF("chan-gamma");
                dos.writeUTF("chan-alpha");
                dos.writeUTF("chan-beta");
                dos.writeUTF("chan-gamma");
                dos.writeUTF("chan-alpha");
                dos.writeUTF("chan-beta");
                dos.writeUTF("chan-gamma");
                dos.writeUTF("chan-alpha");
                dos.writeUTF("chan-beta");
                dos.writeUTF("chan-gamma");
                dos.writeUTF("chan-alpha");
                dos.writeUTF("chan-beta");
                dos.writeUTF("chan-gamma");
                dos.writeUTF("chan-alpha");
                dos.writeUTF("chan-beta");
                dos.writeUTF("chan-gamma");
                dos.flush();
                while (true) {
                    char c = dis.readChar();
                    System.out.print(c);
                    if (c == ' ') c = 'n';
                    if (c == 'j') System.out.println(String.format("\nAttached data: %s", dis.readUTF()));
                    dos.writeChar(c);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
