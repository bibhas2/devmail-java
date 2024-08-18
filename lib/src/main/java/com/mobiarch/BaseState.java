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

        /**
     * <p>Parses the ByteBuffer into an integer. Aside from digits 0-9 only 
     * a leading "-" character is permitted.</p>
     * 
     * <p>Valid values: 100, -100, -0012</p>
     * 
     * <p>Parsing starts from the current position of the buffer. 
     * After parsing, the position of the buffer is moved forward 
     * to the first non digit byte after the number. This lets 
     * you parse a sequence of numbers in the same buffer.
     * For example, parsing the buffer "100 -2 5" repeatedly will
     * return 100, -2 and 5 consecutively.</p>
     * 
     * <p>All non-digit and negative signs before the
     * number are ignored. So, parsing "HELLO-10WORLD"
     * will return -10. The position of the buffer
     * will be set to the 'W' character. This behavior
     * let's you parse a delimited list
     * repeatedly like this: "-1, 2, 33, 5".</p>
     * 
     * @param buff The ByteBuffer to parse.
     * 
     * @return The parsed integer value.
     */
    public static int parseInt(ByteBuffer buff) {
        int result = 0;
        int base = 1;
        //Start and end position of the
        //integer inclusive of both
        int start = -1;
        int end = -1;

        if (!buff.hasRemaining()) {
            throw new NumberFormatException("Invalid input.");
        }

        //Move position forward until we find the
        //start of the number.
        while (buff.hasRemaining()) {
            int ch = buff.get();

            if (ch == 45 || (ch >= 48 && ch <= 57)) {
                buff.position(buff.position() - 1);
                start = buff.position();

                break;
            }
        }

        if (!buff.hasRemaining()) {
            throw new NumberFormatException("Invalid input.");
        } 

        //Now look for the end of the number
        while (buff.hasRemaining()) {
            int ch = buff.get();

            if (!(ch == 45 || (ch >= 48 && ch <= 57))) {
                buff.position(buff.position() - 1);

                break;
            }
        }

        end = buff.position() - 1;

        System.out.printf("Start: %d End: %d\n", start, end);

        for (int i = end; i >= start; --i) {
            int ch = buff.get(i);
     
            //Deal with minus sign
            if (i == start && ch == 45) {
                result *= -1;
     
                continue;
            }
     
            if (ch < 48 || ch > 57) {
                throw new NumberFormatException("Invalid input.");
            }
     
            int digit = buff.get(i) - 48;
     
            result = result + digit * base;
     
            base = base * 10;
        }
     
        return result;
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
