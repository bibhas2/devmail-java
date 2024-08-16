package com.mobiarch;

import java.nio.channels.Selector;

public class Main {
    public static void main(String[] args) throws Exception {
        var selector = Selector.open();

        var loop = new IOLoop(selector);
        var smtp = new TCPServer(selector);

        smtp.start(8089);

        loop.start();
    }
}
