package io.github.hexafraction.morsel.server;

import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;


public class SerializedServerState implements Serializable {
    HashMap<String, ChatRoom> rooms;

    public SerializedServerState() {
        this.rooms = new HashMap<>();
    }

    public ChatRoom getRoom(String name){
        synchronized(this) {
            return rooms.computeIfAbsent(name, ChatRoom::new);
        }
    }
}
