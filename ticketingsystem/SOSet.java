package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import java.util.*;

public class SOSet<T> {
    protected LockFreeList<T>[] table;
    protected AtomicInteger tableSize;
    protected AtomicInteger setSize;
    protected AtomicInteger size;
    private static final double THRESHOLD = 4.0;
	static final int WORD_SIZE = 32;

    public SOSet(int capacity) {
        table = (LockFreeList<T>[]) new LockFreeList[capacity];
        table[0] = new LockFreeList<T>();
        tableSize = new AtomicInteger(2);
        setSize = new AtomicInteger(0);
        size = new AtomicInteger(0);
    }

    public String toString() {
        return this.table[0].toString();
    }

    public boolean add(T x) {
        int hash = x.hashCode();
        //System.out.println("Adding" + x);
        int bucket = hash % tableSize.get();
        int key = makeRegularKey(hash);
        LockFreeList<T> list = getLockFreeList(bucket);
        if (!list.add(x, key))
            return false;
        size.getAndIncrement();
        resizeCheck();
        return true;
    }

    private final void resizeCheck() {
        int setSizeNow = setSize.incrementAndGet();
        int tableSizeNow = tableSize.get();
        if (setSizeNow / (double)tableSizeNow > THRESHOLD)
            tableSize.compareAndSet(tableSizeNow, 2 * tableSizeNow);
        //System.out.println("After adding, set size is " + setSizeNow);
    }

    public boolean remove(T x) {
        int hash = x.hashCode();
        int bucket = hash % tableSize.get();
        //System.out.println("Found bucket " + bucket);
        LockFreeList<T> l = getLockFreeList(bucket);
        int key = makeRegularKey(hash);
        if (!l.remove(key)) {
            //System.out.println("Failed to remove");
            return false;
        }
        size.getAndDecrement();
        return true;
    }

    public boolean contains(T x) {
        int hash = x.hashCode();
        int bucket = hash % tableSize.get();
        int key = makeRegularKey(hash);
        LockFreeList<T> l = getLockFreeList(bucket);
        return l.contains(key);
    }

    public boolean isEmpty() {
        return size.get() == 0;
    }
    
    private LockFreeList<T> getLockFreeList(int bucket) {
        if (table[bucket] == null)
            initializeBucket(bucket);
        return table[bucket];
    }

    private void initializeBucket(int bucket) {
        //System.out.println("Initializing bucket " + bucket);
        int parent = getParent(bucket);
        if (table[parent] == null)
            initializeBucket(parent);
        int key = makeSentinelKey(bucket);
        LockFreeList<T> l = new LockFreeList<T>(table[parent],key);
        this.table[bucket] = l;
    }

    private int getParent(int bucket){
        int parent = tableSize.get();
        do {
          parent = parent >> 1;
        } while (parent > bucket);
        parent = bucket - parent;
        return parent;
      }
    
    private int makeRegularKey(int key) {
        return reverse(key | 0x80000000);
    }

    private int makeSentinelKey(int key) {
        return reverse(key);
    }

    private int reverse(int key) {
        int hi = 0x80000000;
        int lo = 0x1;
        int result = 0;
        for (int i = 0; i < WORD_SIZE; i++) {
            if ((key & lo) != 0) {
                result |= hi;
            }
            lo <<= 1;
            hi >>>= 1;
        }
        return result;
    }
}
