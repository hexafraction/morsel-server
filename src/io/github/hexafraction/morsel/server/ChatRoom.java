package io.github.hexafraction.morsel.server;

import java.io.DataOutputStream;
import java.io.Serializable;
import java.util.HashSet;


public class ChatRoom implements Serializable {
    public static final long serialVersionUID = 4236538799018940954L;
    long lastMessageTime;
    String name;
    StringBuffer chatHistory;
    public ChatRoom(String name) {
        this.name = name;
        chatHistory = new StringBuffer();
        lastMessageTime = System.currentTimeMillis();
    }

    public synchronized void appendToHistory(char c) {

        if(System.currentTimeMillis()-lastMessageTime>60000){
            //chatHistory.append('n');
        }
        lastMessageTime = System.currentTimeMillis();
        chatHistory.append(c);
    }

    @Override
    public String toString() {
        return name;
    }

    public synchronized boolean delLast(char c) {
        if(chatHistory.length()>0) {
            chatHistory.deleteCharAt(chatHistory.length()-1);
            return true;
        } else return false;
    }

    public synchronized void delAll() {
        chatHistory = new StringBuffer();
    }
}
