package com.mobiarch;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class SMTPState implements EventListener {
    private enum SMTPParseState {
        STATE_NONE,
        STATE_READ_CMD,
        STATE_READ_DATA
    } 

    ByteBuffer in;
    ByteBuffer out;
    SMTPParseState state = SMTPParseState.STATE_READ_CMD;
    RandomAccessFile saveFile = null;
    FileChannel saveFileChannel = null;

    public static void print(ByteBuffer buff) {
        if (buff == null) {
            System.out.println("null");

            return;
        }

        //Dump bytes
        for (int i = 0; i < buff.limit(); ++i) {
            System.out.write((int) buff.get(i));
        }
    }

    public SMTPState() {
        //in = new ByteArrayInputStream(new byte[256]);
        in = ByteBuffer.allocate(256);
        out = ByteBuffer.allocate(256);

        out.flip(); //Ready to write to
    }

    private boolean isCommand(String cmd) {
        int most = Math.min(cmd.length(), in.limit());

        for (int i = 0; i < most; ++i) {
            if (cmd.charAt(i) != in.get()) {
                in.rewind();

                return false;
            }
        }

        return true;
    }

    @Override
    public void onAccept(SelectionKey key) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onAccept'");
    }

    public void sendReply(SelectionKey key, String txt) throws IOException {
        //We should not be in the middle of a write already
        if (out.hasRemaining()) {
            throw new RuntimeException("Yet to write bytes: " + out.remaining());
        }

        out.clear(); //Set position=0

        int most = Math.min(txt.length(), out.limit());

        for (int i = 0; i < most; ++i) {
            out.put((byte) txt.charAt(i));
        }

        out.flip(); //Set position=0 and limit correctly

        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
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
        }

        if (state == SMTPParseState.STATE_READ_CMD) {
            //Note: Above read() moves position after the last
            //valid byte. It doesn't change limit.
            if (in.get(in.position() - 1) == '\n') {
                //Done reading line. Flipping makes the whole
                //buffer ready to read from.
                in.flip();

                System.out.print("Command: [");
                print(in);
                System.out.println("]");

                onCommand(key);

                //Start reading into the beginning of buffer
                in.clear();
            }
        } else if (state == SMTPParseState.STATE_READ_DATA) {
            in.flip();

            System.out.printf("Received DATA: %d\n", in.remaining());

            //See if we received the end of the mail file message: 
            // \r\n.\r\n
            if (in.remaining() >= 5 &&
                in.get(in.limit() - 5) == '\r' &&
                in.get(in.limit() - 4) == '\n' &&
                in.get(in.limit() - 3) == '.' &&
                in.get(in.limit() - 2) == '\r' &&
                in.get(in.limit() - 1) == '\n') {

                in.limit(in.limit() - 5);
                saveFileChannel.write(in);

                System.out.printf("Closing mail file.\n");
                saveFileChannel.close();
                saveFile.close();
                saveFileChannel = null;
                saveFile = null;

                state = SMTPParseState.STATE_READ_CMD;

                //Start reading into the beginning of buffer
                in.clear();
            } else {
                saveFileChannel.write(in);
                //Start reading into the beginning of buffer
                in.clear();
            }
        }
    }

    private void onCommand(SelectionKey key) throws IOException {
        if (isCommand("HELLO")) {
            sendReply(key, "250 Ok\r\n");
        } else if (isCommand("QUIT")) {
            sendReply(key, "221 Bye\r\n");
        } else if (isCommand("EHLO")) {
            sendReply(key, "250-dev-smtp\r\n250-8BITMIME\r\n250-AUTH LOGIN\r\n250 Ok\r\n");
        } else if (isCommand("MAIL FROM:")) {
            sendReply(key,  "250 Ok\r\n");
        } else if (isCommand("RCPT TO:")) {
            sendReply(key,  "250 Ok\r\n");
        } else if (isCommand("DATA")) {
            sendReply(key,  "354 Send message, end with a \".\" on a line by itself\r\n");

            var fileName = String.format("mail/%d.eml", System.nanoTime());

            System.out.printf("Saving mail to: %s\n", fileName);

            saveFile = new RandomAccessFile(fileName, "rw");
            saveFileChannel = saveFile.getChannel();
            state = SMTPParseState.STATE_READ_DATA;
        } else if (isCommand("QUIT")) {
            sendReply(key, "221 Bye\r\n");
        } else {
            System.out.println("Unknown command");
            sendReply(key,  "250 Ok\r\n");
        }
    }

    @Override
    public void onWritePossible(SelectionKey key) throws IOException {
        if (out.hasRemaining()) {
            SocketChannel client = (SocketChannel) key.channel();
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
            key.interestOps(SelectionKey.OP_READ);
        }
    }
}
