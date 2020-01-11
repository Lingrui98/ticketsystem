package ticketingsystem;

// import java.util.concurrent.atomic.AtomicInteger;

// public class Pool <T extends IPoolCell> {

//     private Pool instance;


//     public void initInstance(Pool pool) {
//         instance = pool;
//     }

//     public Pool getInstance() {
//         return instance;
//     }



//     private int size;

//     private LockFreeQueue<T> queue;

//     private String name;

//     // private ObjectFactory objectFactory;


//     private AtomicInteger objCreateCount = new AtomicInteger(0);

//     // public Pool(int size, ObjectFactory objectFactory) {
//     //     this(size, "default-pool", objectFactory);
//     // }

//     public Pool(String name) {
//         // this.size = size;
//         this.name = name;

//         queue = new LockFreeQueue<T>();
//     }


//     /**
//      * 获取对象，若队列中存在， 则直接返回；若不存在，则新创建一个返回
//      * @return
//      */
//     public T get() {
//         T obj = queue.dequeue();

//         if (obj != null) {
//             return obj;
//         }

//         obj = T create();
//         // int num = objCreateCount.addAndGet(1);


//         // if (log.isDebugEnabled()) {
//         //     if (objCreateCount.get() >= size) {
//         //         log.debug("objectPoll fulled! create a new object! total create num: {}, poll size: {}", num, queue.size());
//         //     } else {
//         //         // fixme 由于并发问题，这个队列的大小实际上与添加对象时的大小不一定相同
//         //         log.debug("objectPoll not fulled!, init object, now poll size: {}", queue.size());
//         //     }
//         // }

//         return obj;
//     }


//     /**
//      * 将对象扔回到队列中
//      *
//      * @param obj
//      */
//     public void release(T obj) {
//         obj.clear();

//         // 非阻塞方式的扔进队列
//         queue.enqueue(obj);

//         // if (log.isDebugEnabled()) {
//         //     log.debug("return obj to pool status: {}, now size: {}", ans, queue.size());
//         // }
//     }


//     public void clear() {
//         while (queue.dequeue() != null);
//     }
// }

public final class Pool<OBJECT extends ObjectPool.RecyclableObject> {
    private OBJECT[] mTable;
    private AtomicInteger mOrderNumber;
    public final int RESET_NUM;

    public Pool(int size) {
        mOrderNumber = new AtomicInteger(0);
        mTable = new OBJECT[size];
        for (int i = 0; i < size; i++) {
            mTable[i] = new OBJECT();
        }
        RESET_NUM = size;
        // if (mTable == null) {
        //     throw new NullPointerException("The input array is null.");
        // }
        // int length = inputArray.length;
        // if ((length & length - 1) != 0) {
        //     throw new RuntimeException("The length of input array is not 2^n.");
        // }
    }

    public void recycle(OBJECT object) {
        object.isIdle.set(true);
    }


    public OBJECT obtain() {
        int index = mOrderNumber.getAndIncrement();
        if (index > RESET_NUM) {
            mOrderNumber.compareAndSet(index, 0);
            if (index > RESET_NUM * 2) {
                mOrderNumber.set(0);
            }
        }

        int num = index & (mTable.size - 1);

        OBJECT target = mTable[num];

        if (target.isIdle.compareAndSet(true, false)) {
            return target;
        } else {
            // 注意：此处可能会因为OBJECT回收不及时，而导致栈溢出。
            // 请增加retryTime参数，以及retryTime过多后的判断。
            // 具体思路请参考
            // https://github.com/SpinyTech/ModularizationArchitecture/blob/master/macore/src/main/java/com/spinytech/macore/router/RouterRequest.java
            // 中的obtain()及obtain(int retryTime);方法
            return null;
        }
    }

    public abstract static class RecyclableObject {
        AtomicBoolean isIdle = new AtomicBoolean(true);
    }
}