package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;

public class Pool<T> {

    private Pool instance;


    public void initInstance(Pool pool) {
        instance = pool;
    }

    public Pool getInstance() {
        return instance;
    }



    private int size;

    private LockFreeQueue<T> queue;

    private String name;

    // private ObjectFactory objectFactory;


    private AtomicInteger objCreateCount = new AtomicInteger(0);

    // public Pool(int size, ObjectFactory objectFactory) {
    //     this(size, "default-pool", objectFactory);
    // }

    public Pool<T>(String name) {
        // this.size = size;
        this.name = name;

        queue = new LockFreeQueue<T>;
    }


    /**
     * 获取对象，若队列中存在， 则直接返回；若不存在，则新创建一个返回
     * @return
     */
    public T get() {
        T obj = queue.dequeue();

        if (obj != null) {
            return obj;
        }

        obj = new T();
        // int num = objCreateCount.addAndGet(1);


        // if (log.isDebugEnabled()) {
        //     if (objCreateCount.get() >= size) {
        //         log.debug("objectPoll fulled! create a new object! total create num: {}, poll size: {}", num, queue.size());
        //     } else {
        //         // fixme 由于并发问题，这个队列的大小实际上与添加对象时的大小不一定相同
        //         log.debug("objectPoll not fulled!, init object, now poll size: {}", queue.size());
        //     }
        // }

        return obj;
    }


    private void clear(Ticket ticket) {
        ticket.tid = -1;
        ticket.passenger = "";
        ticket.route = 0;
        ticket.coach = 0;
        ticket.seat = 0;
        ticket.departure = 0;
        ticket.arrival = 0;
    }

    private void clear(TicketWithHash ticket) {
        ticket.ticket.tid = null;
    }
    /**
     * 将对象扔回到队列中
     *
     * @param obj
     */
    public void release(T obj) {
        clear(obj);

        // 非阻塞方式的扔进队列
        boolean ans = queue.enqueue(obj);

        // if (log.isDebugEnabled()) {
        //     log.debug("return obj to pool status: {}, now size: {}", ans, queue.size());
        // }
    }


    public void clear() {
        while (queue.dequeue() != null);
    }
}