package com.mobiarch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TCPServer implements EventListener {
    private Selector selector;
    private ServerSocketChannel socket;

    public TCPServer(Selector selector) {
        this.selector = selector;
    }

    public void start(int port) {
        try {
            // We have to set connection host, port and non-blocking mode
            socket = ServerSocketChannel.open();

            socket.socket().bind(new InetSocketAddress("localhost", port));
            socket.configureBlocking(false);
            socket.register(selector, SelectionKey.OP_ACCEPT, this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onAccept(SelectionKey key) throws IOException {
        System.out.println("Connection Accepted...");

        // Accept the connection and set non-blocking mode
        SocketChannel client = socket.accept();
        
        client.configureBlocking(false);

        // Register that client is reading this channel
        client.register(selector, SelectionKey.OP_READ, new IOState());
    }

    public void onReadAvailable(SelectionKey key)
            throws IOException {
        System.out.println("Reading...");
        // create a ServerSocketChannel to read the request
        SocketChannel client = (SocketChannel) key.channel();

        // Create buffer to read data
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        client.read(buffer);

        // Parse data from buffer to String
        String data = new String(buffer.array()).trim();

        if (data.length() > 0) {
            System.out.println("Received message: " + data);

            if (data.equalsIgnoreCase("exit")) {
                client.close();
                key.cancel();
                System.out.println("Connection closed...");
            }
        }
    }

    @Override
    public void onWritePossible(SelectionKey key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onWritePossible'");
    }
}
