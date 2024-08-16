package com.mobiarch;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface EventListener {

    void onAccept(SelectionKey key) throws IOException;

    void onReadAvailable(SelectionKey key) throws IOException;

    void onWritePossible(SelectionKey key) throws IOException;
    
}
