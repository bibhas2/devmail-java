package com.mobiarch;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class SMTPState extends BaseState implements EventListener {
    private enum SMTPParseState {
        STATE_NONE,
        STATE_READ_CMD,
        STATE_READ_DATA
    } 

    SMTPParseState state = SMTPParseState.STATE_READ_CMD;
    RandomAccessFile saveFile = null;
    FileChannel saveFileChannel = null;
    FixedQueue<Byte> msgEnd = new FixedQueue<>(5);

    public SMTPState() {
        in = ByteBuffer.allocate(256);
        out = ByteBuffer.allocate(256);

        out.flip(); //Ready to write to
    }

    @Override
    public void onAccept(SelectionKey key) throws IOException {
        state = SMTPParseState.STATE_READ_CMD;

        sendReply(key, "220 example.com\r\n");
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
                BaseState.print(in);

                onCommand(key);

                //Start reading into the beginning of buffer
                in.clear();
            }
        } else if (state == SMTPParseState.STATE_READ_DATA) {
            in.flip();

            saveFileChannel.write(in);

            //See if we received the end of the 
            //mail file message: \r\n.\r\n

            //Add up to last 5 bytes of the data into the queue
            boolean isEndOfData = false;
            int start = Math.max(0, in.limit() - 5);
            
            for (int i = start; i < in.limit(); ++i) {
                msgEnd.add(in.get(i));
            }

            if (msgEnd.size() == 5) {
                byte[] endBytes = {'\r', '\n', '.', '\r', '\n'};
                int i = 0;

                for (var b : msgEnd) {
                    isEndOfData = b == endBytes[i++];

                    if (!isEndOfData) {
                        break;
                    }
                }
            }

            if (isEndOfData) {
                System.out.printf("Closing mail file.\n");

                //fsync the data into disk
                saveFileChannel.force(true);

                //Truncate the file to get rid of
                //".\r\n".
                saveFile.setLength(saveFile.length() - 3);

                saveFileChannel.close();
                saveFile.close();
                saveFileChannel = null;
                saveFile = null;

                state = SMTPParseState.STATE_READ_CMD;

                //Start reading into the beginning of buffer
                in.clear();

                sendReply(key, "250 Ok\r\n");
            } else {
                System.out.printf("More DATA expected.\n");

                //More DATA available.
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
            msgEnd.clear();
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
