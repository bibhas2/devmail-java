package com.mobiarch;

/**
 * A circular buffer of queue implementation. It doesn't offer a storage. It only
 * keeps track of start, end and size values of the queue. This is done so
 * that you can use a native array for storage for maximum performance. A generics based
 * class can only create an array of objects. That is not ideal for storing native data types.
 * 
 * Example usage:
 * 
 * <pre>
 * <code>
 * var buff = new CircBuff(4);
 * int[] storage = {0, 0, 0, 0};
 * 
 * storage[b.add()] = 1;
 * storage[b.add()] = 2;
 * storage[b.add()] = 3;
 * storage[b.add()] = 4; 
 * 
 * //At this point the queue is [1, 2, 3, 4].
 * 
 * storage[b.add()] = 5; //This will evict 1 at head
 * storage[b.add()] = 6; //This will evict 2 at head
 * 
 * //At this point the queue is [3, 4, 5, 6].
 * 
 * int i = storage[b.take()]; //i is 3 that was at head
 * int j = storage[b.take()]; //j is 4 that was at head
 * 
 * //At this point the queue is [5, 6].
 * 
 * </code>
 * </pre>
 */
public class CircBuff {
    private int start = 0;
    private int capacity = 0;
    private int size = 0;
    private int end = 0;
    
    public CircBuff(int capacity) {
        this.capacity = capacity;
    }

    public int start() {
        return start;
    }

    public int capacity() {
        return capacity;
    }

    public int size() {
        return size;
    }

    public int end() {
        return end;
    }

    public void clear() {
        start = 0;
        end = 0;
        size = 0;
    }

    /**
     * It returns the current end index. The item should be added
     * to the storage at that index. The method moves the end forward and wraps it
     * if needed.
     * 
     * @return The index of the storage where the item should be added.
     */
    public int add() {
        //Set value at the current end position
        int where = end;

        if (size < capacity) {
            //Grow size
            ++size;
        }

        //Move end forward
        end = (end + 1) % capacity;

        if (size == capacity) {
            //At full capacity start and end
            //must coincide
            start = end;
        }

        return where;
    }

    /**
     * Returns the current index that is the head of the queue and forwards the start index.
     * 
     * @return The index of the head of the queue.
     */
    public int take() {
        if (size == 0) {
            throw new ArrayIndexOutOfBoundsException("Buffer is empty.");
        }

        //Get the value at the front
        int where = at(0);

        //Shrink
        --size;

        //Move start forward
        start = (start + 1) % capacity;

        return where;
    }

    /**
     * Maps an index into the queue to an index into the storage.
     * 
     * @param pos Index into the queue. Must between 0 and size (excluded).
     * 
     * @return The corresponding index into the storage.
     */
    public int at(int pos) {
        if (pos >= size) {
            throw new ArrayIndexOutOfBoundsException("Index is outside the size of buffer.");
        }

        int i = (start + pos) % capacity;

        return i;
    }

    public boolean empty() {
        return size == 0;
    }
}
