package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import java.util.*;

public class SOSet<T> {
    protected LockFreeList<T>[] table;
    protected AtomicInteger tableSize;
    protected AtomicInteger setSize;
    private static final double THRESHOLD = 4.0;
	static final int WORD_SIZE = 32;

    public SOSet(int capacity) {
        table = (LockFreeList<T>[]) new LockFreeList[capacity];
        table[0] = new LockFreeList<T>();
        tableSize = new AtomicInteger(2);
        setSize = new AtomicInteger(0);
    }

    public boolean add(T x) {
        int hash = x.hashCode();
        int bucket = hash % tableSize.get();
        int key = makeRegularKey(hash);
        
        LockFreeList<T> list = getLockFreeList(bucket);
        if (!list.add(x, key))
            return false;
        resizeCheck();
        return true;
    }

    private final resizeCheck() {
        int setSizeNow = setSize.incrementAndGet();
        int tableSizeNow = tableSize.get();
        if (setSizeNow / (double)tableSizeNow > THRESHOLD)
            tableSize.compareAndSet(tableSizeNow, 2 * tableSizeNow);
        System.out.println("After adding, set size is " + setSizeNow);
        return true;
    }

    public boolean remove(T x) {
        int bucket = x.hashCode() % tableSize.get();
        LockFreeList<T> l = getLockFreeList(bucket);

        if (!l.remove(x)) {
            System.out.println("Failed to remove");
            return false;
        }
        return true;
    }

    public boolean contains(T x) {
        int bucket = x.hashCode() % tableSize.get();
        LockFreeList<T> l = getLockFreeList(bucket);
        return l.contains(x);
    }
    
    private LockFreeList<T> getLockFreeList(int bucket) {
        if (table[bucket] == null)
            initializeBucket(bucket);
        return table[bucket];
    }

    private void initializeBucket(int bucket) {
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