package io.github.hexafraction.morsel.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;

public class UserConnection {
    private volatile ChatRoom room;
    private Socket sock;
    private Thread reader;
    private volatile boolean valid;
    private DataInputStream dis;
    private DataOutputStream dos;

    private Server sv;
    volatile long lastHeard;

    public boolean isValid() {
        return valid;
    }

    public UserConnection(Socket accept, Server sv) throws IOException {
        this.sv = sv;
        sock = accept;
        sock.setKeepAlive(true);
        sock.setTcpNoDelay(true);
        dis = new DataInputStream(sock.getInputStream());
        dos = new DataOutputStream(sock.getOutputStream());
        dos.writeInt(0x64607012);
        dos.flush();
        int cMagic = dis.readInt();
        if (cMagic != 0x77345466) {
            sock.close();

            return;
        }
        valid = true;
        lastHeard = System.currentTimeMillis();
        reader = new Thread(new NetworkReader(), "netreader-" + sock.getRemoteSocketAddress().toString());
        reader.start();
    }

    ChatRoom getRoom() {
        return room;
    }

    public void stop() {
        try {
            System.out.println("Closing connection for "+(sock==null?"null":sock.getRemoteSocketAddress().toString()));
            dos.close();
            dis.close();
            sock.close();

        } catch (IOException e) {
            // pass
            sv.bugsnagClient.notify(e);
        }
    }

    private class NetworkReader implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    char c = dis.readChar();
                    System.out.println((sock==null?"null":sock.getRemoteSocketAddress().toString())+"@"+room+":"+c);
                    lastHeard = System.currentTimeMillis();
                    switch (c) {
                        case 'l':
                            synchronized (sv.state) {
                                Set<String> keys = sv.state.rooms.keySet();
                                synchronized (dos) {
                                    dos.writeInt(keys.size());
                                    for (String s : keys) {

                                        dos.writeUTF(s);

                                    }
                                }
                            }
                            break;
                        case 'j':
                            room = sv.state.getRoom(dis.readUTF());
                            synchronized (dos) {
                                for (char c2 : room.chatHistory.toString().toCharArray()) {
                                    dos.writeChar(c2);
                                }
                            }
                            break;
                        case 'p':
                            break;
                        case 'd':
                            if (room != null) {

                                if(room.delLast(c)) {
                                    try {
                                        sv.connectionManagerLock.readLock().lock();
                                        sv.getOnChannel(room).stream().forEach((uc) -> uc.sendChar('d'));
                                    } finally {
                                        sv.connectionManagerLock.readLock().unlock();
                                    }
                                }

                            }
                            break;
                        case 'c':
                            if (room != null) {
                                try {
                                    sv.connectionManagerLock.readLock().lock();
                                    sv.getOnChannel(room).stream().forEach((uc) -> uc.sendChar('c'));
                                } finally {
                                    sv.connectionManagerLock.readLock().unlock();
                                }

                                room.delAll();


                            }
                            break;
                        case 'x':
                            if (room != null) {

                                synchronized (sv.state) {
                                    sv.state.rooms.remove(room.name);
                                }
                                try {
                                    sv.connectionManagerLock.readLock().lock();
                                    sv.getOnChannel(room).stream().forEach((uc) -> {uc.sendChar('x'); uc.room=null;});
                                } finally {
                                    sv.connectionManagerLock.readLock().unlock();
                                }

                            }
                            break;
                        default:
                            if (room != null) {

                                room.appendToHistory(c);
                                try {
                                    sv.connectionManagerLock.readLock().lock();
                                    sv.getOnChannel(room).stream().forEach((uc) -> uc.sendChar(c));
                                } finally {
                                    sv.connectionManagerLock.readLock().unlock();
                                }
                            }
                    }
                } catch (IOException e) {
                    valid = false;
                    return;
                }
            }
        }
    }

    void sendChar(char c) {
        synchronized (dos) {
            try {
                dos.writeChar(c);
            } catch (IOException e) {
                valid = false;
            }
        }
    }
}
