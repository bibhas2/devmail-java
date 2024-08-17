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
        state = SMTPParseState.STATE_READ_CMD;

        sendReply(key, "220 example.com\r\n");
    }

    public void sendReply(SelectionKey key, String txt) throws IOException {
        //We should not be in the middle of a write already
        if (out.hasRemaining()) {
            throw new RuntimeException("Yet to write bytes: " + out.remaining());
        }

        System.out.printf("SMTP: %s", txt);

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
        } else if (sz == 0) {
            //Nothing's read
            return;
        }

        if (state == SMTPParseState.STATE_READ_CMD) {
            //Note: Above read() moves position after the last
            //valid byte. It doesn't change limit.
            if (in.get(in.position() - 1) == '\n') {
                //Done reading line. Flipping makes the whole
                //buffer ready to read from.
                in.flip();

                System.out.print("CLI: ");
                print(in);

                onCommand(key);

                //Start reading into the beginning of buffer
                in.clear();
            }
        } else if (state == SMTPParseState.STATE_READ_DATA) {
            in.flip();

            saveFileChannel.write(in);

            //See if we received the end of the 
            //mail file message: \r\n.\r\n
            if (isEndOfData()) {
                System.out.printf("Closing mail file.\n");
                saveFileChannel.close();
                saveFile.close();
                saveFileChannel = null;
                saveFile = null;

                state = SMTPParseState.STATE_READ_CMD;

                //Start reading into the beginning of buffer
                in.clear();

                sendReply(key, "250 Ok\r\n");
            } else {
                //More DATA available.
                //Start reading into the beginning of buffer
                in.clear();
            }
        }
    }

    /**
     * Tries to determine if the end of the DATA segment has been reached.
     * Because we don't store the email body in memory it is hard
     * to detect if we're seeing the last "\r\n.\r\n" sentinel byte
     * sequence. Instead, we inspect the saved file.
     * 
     * @return true if the end of the email body in DATA segment has been 
     * received.
     * @throws IOException
     */
    private boolean isEndOfData() throws IOException {
        //If the last byte of DATA read is not a \n 
        //Then this cannot be the end
        if (in.get(in.limit() - 1) != '\n') {
            return false;
        }

        var len = saveFile.length();

        if (len < 5) {
            return false;
        }

        //Read the last 5 bytes in the file
        saveFile.seek(len - 5);

        byte[] b = {0, 0, 0, 0, 0};

        if (saveFile.read(b) != 5) {
            throw new IOException("Could not read last 5 bytes of email file.");
        }

        //Check for sentinel byte sequence
        if (b[0] == '\r' &&
            b[1] == '\n' &&
            b[2] == '.' &&
            b[3] == '\r' &&
            b[4] == '\n') {
            //Truncate the file to get rid of
            //".\r\n".
            saveFile.setLength(len - 3);

            return true;
        } else {
            //At this point the file pointer should
            //be back at the very end.
            return false;
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
