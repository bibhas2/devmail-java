package com.mobiarch;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Implements a fixed size queue. If you add an element
 * when the queue is full it will remove the element at the head
 * to make room.
 */
public class FixedQueue<E> extends ArrayBlockingQueue<E> {

    public FixedQueue(int capacity) {
        super(capacity);
    }

    @Override
    public boolean add(E e) {
        if (remainingCapacity() == 0) {
            remove();
        }

        return super.add(e);
    }
    
}
