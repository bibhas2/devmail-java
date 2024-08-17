package com.mobiarch;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

public class POP3State extends BaseState implements EventListener {
    enum POPParseState {
        STATE_NONE,
        STATE_READ_CMD,
        STATE_WRITE_LIST,
        STATE_WRITE_UIDL_LIST,
        STATE_WRITE_RETR_HEADER,
        STATE_WRITE_TOP_HEADER,
        STATE_WRITE_MSG_HEADER,
        STATE_WRITE_MSG,
        STATE_BYE
    } 

    POPParseState state = POPParseState.STATE_NONE;
    ArrayList<File> messageList = new ArrayList<>();

    public POP3State() {
        in = ByteBuffer.allocate(256);
        out = ByteBuffer.allocate(256);

        out.flip(); //Ready to write to
    }

    @Override
    public void onAccept(SelectionKey key) throws IOException {
        state = POPParseState.STATE_READ_CMD;

        sendReply(key, "+OK POP3 example.com server ready\r\n");
    }

    @Override
    public void onReadAvailable(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        int sz = 0;

        try {
            //sz will be -1 for a graceful disconnect by client
            sz = client.read(in);
        } catch (SocketException e) {
            //Ungraceful disconnect by client
            sz = -1;
        }
        
        //This will deal with both graceful and
        //ungraceful disconnect by client
        if (sz < 0) {
            System.out.println("Client disconnected.");

            client.close();
            key.cancel();

            return;
        } else if (sz == 0) {
            //Nothing's read
            return;
        }

        if (state == POPParseState.STATE_READ_CMD) {
            //Note: Above read() moves position after the last
            //valid byte. It doesn't change limit.
            if (in.get(in.position() - 1) == '\n') {
                //Done reading line. Flipping makes the whole
                //buffer ready to read from.
                in.flip();

                System.out.print("CLI: ");
                BaseState.print(in);

                onCommand(key);

                //Start reading into the beginning of buffer
                in.clear();
            }
        }
    }

    @Override
    public void onWritePossible(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        if (out.hasRemaining()) {
            int sz = 0;

            try {
                sz = client.write(out);
            } catch (Exception e) {
                sz = -1;
            }

            if (sz < 0) {
                System.out.println("Client disconnected.");

                client.close();
                key.cancel();
    
                return;    
            }
        } else {
            //We are done writing
            if (state == POPParseState.STATE_BYE) {
                System.out.println("Closing connection.");

                client.close();
                key.cancel();
            } else {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void onCommand(SelectionKey key) throws IOException {
        if (isCommand("USER")) {
            sendReply(key, "+OK User name accepted, password please\r\n");
        } else if (isCommand("PASS")) {
            sendReply(key, "+OK Mailbox open\r\n");
        } else if (isCommand("STAT")) {
            loadMessageList();

            int sz = 0;

            for (var f : messageList) {
                sz += f.length();
            }

            sendReply(key, 
                String.format("+OK %d %d\r\n", 
                    messageList.size(), sz));
        } else if (isCommand("DELE ")) {
        } else if (isCommand("UIDL ")) {
        } else if (isCommand("UIDL")) {
        } else if (isCommand("LIST")) {
        } else if (isCommand("QUIT")) {
            state = POPParseState.STATE_BYE;

            sendReply(key, "+OK Bye\r\n");
        } else if (isCommand("RETR")) {
        } else if (isCommand("TOP")) {
        } else {
            System.out.println("Unknown command");

            sendReply(key, "-ERR\r\n");
        }
    }

    private void loadMessageList() {
        messageList.clear();

        var dir = new File(MAIL_DIR);

        for (var f : dir.listFiles()) {
            if (f.isFile()) {
                messageList.add(f);
            }
        }
    }
}
