package com.mobiarch;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

public class IOLoop {
    private Selector selector;

    public IOLoop(Selector selector) {
        this.selector = selector;
    }

    public void start() throws IOException {
        while (true) {
            selector.select();

            System.out.println("SELECT returned");

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> i = keys.iterator();

            while (i.hasNext()) {
                SelectionKey key = i.next();
                var listener = (EventListener) key.attachment();

                if (key.isAcceptable()) {
                    // New client has been accepted
                    listener.onAccept(key);
                } else if (key.isReadable()) {
                    //System.out.println("READABLE");
                    // We can run non-blocking operation READ on our client
                    listener.onReadAvailable(key);
                } else if (key.isWritable()) {
                    //System.out.println("WRITABLE");
                    listener.onWritePossible(key);
                } else {
                    System.out.println("UNKNOWN SELECT");
                }

                i.remove();
            }
        }
    }
}
