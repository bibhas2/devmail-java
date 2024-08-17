package com.mobiarch;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.channels.SelectionKey;

public class BaseState {
    protected static final String MAIL_DIR = "mail";
    
    protected ByteBuffer in;
    protected ByteBuffer out;

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

    protected boolean isCommand(String cmd) {
        int most = Math.min(cmd.length(), in.limit());

        for (int i = 0; i < most; ++i) {
            if (cmd.charAt(i) != in.get()) {
                in.rewind();

                return false;
            }
        }

        return true;
    }

    protected void sendReply(SelectionKey key, String txt) throws IOException {
        //We should not be in the middle of a write already
        if (out.hasRemaining()) {
            throw new RuntimeException("Yet to write bytes: " + out.remaining());
        }

        System.out.printf("SRV: %s", txt);

        out.clear(); //Set position=0

        int most = Math.min(txt.length(), out.limit());

        for (int i = 0; i < most; ++i) {
            out.put((byte) txt.charAt(i));
        }

        out.flip(); //Set position=0 and limit correctly

        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

}
