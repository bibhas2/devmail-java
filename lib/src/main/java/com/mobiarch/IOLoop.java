package com.mobiarch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class IOLoop {
    private void startServer(Selector selector, String type, int port) throws IOException {
        var socket = ServerSocketChannel.open();

        socket.socket().bind(new InetSocketAddress(port));
        socket.configureBlocking(false);
        socket.register(selector, SelectionKey.OP_ACCEPT, type);
    }

    public void onAccept(Selector selector, SelectionKey key) throws IOException {
        // Accept the connection and set non-blocking mode
        var socket = (ServerSocketChannel) key.channel();
        SocketChannel client = socket.accept();
        
        client.configureBlocking(false);

        // Register that client is reading this channel
        if ("SMTP".equals(key.attachment())) {
            System.out.println("Accepted client type: SMTP");

            var smtp = new SMTPState();

            var clientKey = client.register(selector, SelectionKey.OP_READ, smtp);

            smtp.onAccept(clientKey);
        } else if ("POP3".equals(key.attachment())) {
            System.out.println("Accepted client type: POP3");

            var pop3 = new POP3State();

            var clientKey = client.register(selector, SelectionKey.OP_READ, pop3);

            pop3.onAccept(clientKey);
        }
    }

    public void begin() throws IOException {
        Selector selector = Selector.open();

        startServer(selector, "SMTP", 2525);
        startServer(selector, "POP3", 1100);
        
        while (true) {
            selector.select();

            //System.out.println("SELECT returned");

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> i = keys.iterator();

            while (i.hasNext()) {
                SelectionKey key = i.next();

                if (key.isAcceptable()) {
                    // New client has been accepted
                    onAccept(selector, key);
                } else if (key.isReadable()) {
                    //System.out.println("READABLE");
                    // We can run non-blocking operation READ on our client
                    var listener = (EventListener) key.attachment();

                    listener.onReadAvailable(key);
                } else if (key.isWritable()) {
                    //System.out.println("WRITABLE");
                    var listener = (EventListener) key.attachment();

                    listener.onWritePossible(key);
                } else {
                    System.out.println("UNKNOWN SELECT");
                }

                i.remove();
            }
        }
    }
}
