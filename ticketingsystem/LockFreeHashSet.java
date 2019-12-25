package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import java.util.*;


/**

 * @param T item type

 * @author Maurice Herlihy

 */

public class LockFreeHashSet<T> {

  protected BucketList<T>[] bucket;

  protected AtomicInteger bucketSize;

  protected AtomicInteger setSize;

  private static final double THRESHOLD = 4.0;

  /**

   * Constructor

   * @param capacity max number of bucket

   */

  public LockFreeHashSet(int capacity) {

    bucket = (BucketList<T>[]) new BucketList[capacity];

    bucket[0] = new BucketList<T>();

    bucketSize = new AtomicInteger(2);

    setSize = new AtomicInteger(0);

  }

  /**

   * Add item to set

   * @param x item to add

   * @return <code>true</code> iff set changed.

   */

  public boolean add(T x) {

    int myBucket = Math.abs(BucketList.hashCode(x) % bucketSize.get());

    BucketList<T> b = getBucketList(myBucket);

    if (!b.add(x)){
      System.out.println("falied to add");
      return false;
    }

    int setSizeNow = setSize.incrementAndGet();

    int bucketSizeNow = bucketSize.get();

    if (setSizeNow / (double)bucketSizeNow > THRESHOLD)

      bucketSize.compareAndSet(bucketSizeNow, 2 * bucketSizeNow);

    System.out.println("After adding, set size is " + setSizeNow);

    return true;

  }

  /**

   * Remove item from set

   * @param x item to remove

   * @return <code>true</code> iff set changed.

   */

  public boolean remove(T x) {

    int myBucket = Math.abs(BucketList.hashCode(x) % bucketSize.get());

    BucketList<T> b = getBucketList(myBucket);

    if (!b.remove(x)) {
      System.out.println("Failed to remove");
      return false;		// she's not there

    }
    int setSizeNow = setSize.decrementAndGet();
    System.out.println("After removing, set size is " + setSizeNow);

    return true;

  }

  public boolean contains(T x) {

    int myBucket = Math.abs(BucketList.hashCode(x) % bucketSize.get());

    BucketList<T> b = getBucketList(myBucket);

    return b.contains(x);

  }

  private BucketList<T> getBucketList(int myBucket) {

    if (bucket[myBucket] == null)

      initializeBucket(myBucket);

    return bucket[myBucket];

  }

  private void initializeBucket(int myBucket) {

    int parent = getParent(myBucket);

    if (bucket[parent] == null)

      initializeBucket(parent);

    BucketList<T> b = bucket[parent].getSentinel(myBucket);

    if (b != null)

      bucket[myBucket] = b;

  }

  private int getParent(int myBucket){

    int parent = bucketSize.get();

    do {

      parent = parent >> 1;

    } while (parent > myBucket);

    parent = myBucket - parent;

    return parent;

  }

}
