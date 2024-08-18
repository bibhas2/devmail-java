package com.mobiarch;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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
    int messageIndex = 0;
    MappedByteBuffer map = null;

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
        } else if (map != null && map.hasRemaining()) {
            System.out.printf("Writing map. Remaining: %d\n", map.remaining());
            int sz = 0;

            try {
                sz = client.write(map);
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
            //We are done writing. Cancel further 
            //writeability test
            key.interestOps(SelectionKey.OP_READ);

            if (state == POPParseState.STATE_BYE) {
                System.out.println("Closing connection.");

                client.close();
                key.cancel();
            } else if (state == POPParseState.STATE_WRITE_LIST) {
                if (messageIndex == messageList.size()) {
                    //We're finished writing the LIST.
                    //Send the end sentinels
                    sendReply(key, ".\r\n");

                    state = POPParseState.STATE_READ_CMD;
                } else {
                    //Send file stat
                    sendReply(key, 
                        String.format("%d %d\r\n", 
                        messageIndex + 1, messageList.get(messageIndex).length()));
                    
                    ++messageIndex;
                }
            } else if (state == POPParseState.STATE_WRITE_UIDL_LIST) {
                if (messageIndex == messageList.size()) {
                    //We're finished writing the LIST.
                    //Send the end sentinels
                    sendReply(key, ".\r\n");

                    state = POPParseState.STATE_READ_CMD;
                } else {
                    //Send file stat
                    sendReply(key, 
                        String.format("%d %s\r\n", 
                        messageIndex + 1, messageList.get(messageIndex).getName()));
                    
                    ++messageIndex;
                }
            } else if (state == POPParseState.STATE_WRITE_RETR_HEADER) {
                var fileName = String.format("%s/%s", MAIL_DIR, messageList.get(messageIndex).getName());

                try (var file = new RandomAccessFile(fileName, "r")) {
                    //Memory map the file.
                    //There's no need to flip it.
                    //Position and limit already set for reading
                    map = file.getChannel()
                        .map(FileChannel.MapMode.READ_ONLY, 0, file.length());

                    state = POPParseState.STATE_WRITE_MSG;

                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
            } else if (state == POPParseState.STATE_WRITE_MSG) {
                //We're done sending the message body.
                map = null; //Hopefully GC will do the unmapping

                //Send the sentinel bytes
                sendReply(key, "\r\n.\r\n");

                state = POPParseState.STATE_READ_CMD;
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
            //One based index of email message
            int idx = parseInt(in);

            if (idx > messageList.size()) {
                sendReply(key, "-ERR\r\n");
            } else {
                var file = messageList.get(idx - 1);

                System.out.printf("Deleting: %s\n", file.getName());

                file.delete();

                sendReply(key, "+OK Message deleted\r\n");
            }
        } else if (isCommand("UIDL ")) {
            //This is UIDL with arg
            //One based index of email message
            int idx = parseInt(in);

            if (idx > messageList.size()) {
                sendReply(key, "-ERR\r\n");
            } else {
                sendReply(key, 
                        String.format("+OK %d %s\r\n", 
                        idx, messageList.get(idx - 1).getName()));
            }
        } else if (isCommand("UIDL")) {
            state = POPParseState.STATE_WRITE_UIDL_LIST;
            messageIndex = 0;

            loadMessageList();

            sendReply(key, "+OK\r\n");
        } else if (isCommand("LIST")) {
            state = POPParseState.STATE_WRITE_LIST;

            messageIndex = 0;
            loadMessageList(); 
            
            sendReply(key, "+OK Mailbox scan listing follows\r\n");
        } else if (isCommand("QUIT")) {
            state = POPParseState.STATE_BYE;

            sendReply(key, "+OK Bye\r\n");
        } else if (isCommand("RETR ")) {
            //One based index of email message
            int idx = parseInt(in);

            if (idx > messageList.size()) {
                sendReply(key, "-ERR\r\n");
            } else {
                state = POPParseState.STATE_WRITE_RETR_HEADER;
                messageIndex = idx - 1;

                sendReply(key, 
                        String.format("+OK %d octate\r\n", 
                        messageList.get(idx - 1).length()));
            }
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
