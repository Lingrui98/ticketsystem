package ticketingsystem;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;


public class TicketingDS implements TicketingSystem {

	//ToDo
    private final int routenum;
    private final int coachnum;
    private final int seatnum;
    private final int stationnum;
    private final int threadnum;
    private final int intervalnum;
    private final int seatPerTrain;

    private final boolean USE_PROPOSAL = false;
    private final boolean USE_POTENTIAL_QUEUE = true;
    private final boolean USE_SOLDOUT_INDICATOR = true;

    // ind ---> y
    protected int[] remainingTicketSetIndexMap = null;

    protected AtomicInteger[][] seats = null;

    protected ConcurrentHashMap<Long, Boolean> soldTicketMap = new ConcurrentHashMap<Long, Boolean>();
    protected Set<Long> soldTicketSet = Collections.newSetFromMap(soldTicketMap);

    protected AtomicInteger[][] remainingTickets;

    protected LockFreeQueue<RegisterRequest> remainingTicketProcessingQueue = new LockFreeQueue<RegisterRequest>();

    protected LockFreeQueue<Integer>[] potentialQueue = null;

    protected LockFreeQueue<RegisterRequest> proposalSetProcessingQueue = new LockFreeQueue<RegisterRequest>();

    protected AtomicInteger[][] proposal;

    protected AtomicInteger[][] routeIntervalCounter;


    Thread ticketRegisteringThread;

    Thread proposalDealingThread;

    Thread proposalingThread;

    ObjectPool<Ticket> pool =ObjectPool.NonBlocking(new PoolableObject<Ticket>() {
        public void onReturn(Ticket t) {
            return;
        }

        public void onTake(Ticket t) {
            return;
        }

        @Override
        public Ticket create() {
            return new Ticket();
        }
    });

    enum Operation {
        BUY, REFUND;
    }

    enum Status {
        EMPTY, NOTAVAILABLE;
    }

    public int getRouteNum() {
        return routenum;
    }

    public int getIntervalNum() {
        return intervalnum;
    }

    private class RegisterRequest {
        Operation type;
        int route;
        int departure;
        int arrival;
        int status;
        int seatIndex;

        public RegisterRequest(Operation type, int route, int departure, int arrival, int status, int seatIndex) {
            this.type = type;
            this.route = route;
            this.departure = departure;
            this.arrival = arrival;
            this.status = status;
            this.seatIndex = seatIndex;
        }

        public void set(Operation type, int route, int departure, int arrival, int status, int seatIndex) {
            this.type = type;
            this.route = route;
            this.departure = departure;
            this.arrival = arrival;
            this.status = status;
            this.seatIndex = seatIndex;
        }
    }

    @Override
    protected void finalize() {
        System.out.println("TicketingDS is destroyed!");
        this.remainingTicketSetIndexMap = null;
        this.seats = null;
        this.soldTicketSet = null;
        this.remainingTickets = null;
        this.remainingTicketProcessingQueue = null;
        if (this.USE_POTENTIAL_QUEUE)
            this.potentialQueue = null;
        this.proposalSetProcessingQueue = null;
    }


    // TODO: use correct logic
    public class RemainingTicketProcessingThread extends Thread {
        public void run() {
            while (true) {
                RegisterRequest request = null;
                if ((request = remainingTicketProcessingQueue.dequeue()) != null) {
                    int route = request.route;
                    int from = request.departure;
                    int to = request.arrival;
                    int status = request.status;
                    if (request.type == Operation.BUY) {
                        int lower = getLowerBoundOfMaximumEmptyInterval(status, from);
                        int upper = getUpperBoundOfMaximumEmptyInterval(status, to);
                        // System.out.printf("Processing buying..Lower%d, Upper%d\n", lower, upper);
                        // System.out.flush();
                        int x, y;
                        for (x = lower; x < to; x++) {
                            for (y = from+1; y <= upper+1; y++) {
                                if (x < y) {
                                    int val = remainingTickets[route][getRemainingTicketSetIndex(x,y)].getAndDecrement();
                                    // System.out.printf("Decrease of (%d,%d), status 0x%x, from %d, to %d, value from %d to %d\n",
                                    //      x, y, status, from, to, val, val-1);
                                    // System.out.flush();
                                }
                            }
                        }
                    }
                    else if (request.type == Operation.REFUND) {
                        int lower = getLowerBoundOfMaximumEmptyInterval(status, from);
                        int upper = getUpperBoundOfMaximumEmptyInterval(status, to);
                        // System.out.printf("Processing refunding..Lower%d, Upper%d\n", lower, upper);
                        // System.out.flush();
                        int x, y;
                        for (x = lower; x < to; x++) {
                            for (y = from+1; y <= upper+1; y++) {
                                if (x < y) {
                                    int val = remainingTickets[route][getRemainingTicketSetIndex(x,y)].getAndIncrement();
                                    // System.out.printf("Increase of (%d,%d), status 0x%x, from %d, to %d, value from %d to %d\n",
                                    //  x, y, status, from, to, val, val+1);
                                    // System.out.flush();
                                }
                            }
                        }
                    }
                }
                
            }
        }
    }


    private void InitializeSeats() {
        this.seats = new AtomicInteger[this.routenum+1][];
        int i = 0, j = 0;
        for (i = 0; i <= this.routenum; i++) {
            this.seats[i] = new AtomicInteger[this.seatPerTrain];
            for (j = 0; j < this.seatPerTrain; j++) {
                this.seats[i][j] = new AtomicInteger(0);
            }
        }
    }

    private void SetRemainingTicketSetIndexMap() {
        this.remainingTicketSetIndexMap = new int[this.intervalnum];
        int i = 0;
        int y = 2;
        int numOfy = 1;
        int p = 0;
        for (i = 0; i < this.intervalnum; i++) {
            this.remainingTicketSetIndexMap[i] = y;
            //System.out.printf("%d ", y);
            p++;
            if (p >= numOfy) {
                p = 0;
                numOfy++;
                y++;
            }
        }

    }
    
    // Every train has (intervalnum) buckets,
    // initial value of each of which is seatPerTrain
    protected void SetTicketSet() {
        int i, j = 0;
        this.remainingTickets = new AtomicInteger[this.routenum+1][];
        for (i = 0; i <= this.routenum; i++) {
            this.remainingTickets[i] = new AtomicInteger[this.intervalnum];
            for (j = 0; j < this.intervalnum; j++) {
                this.remainingTickets[i][j] = new AtomicInteger(this.seatPerTrain);
            }
        }

    }

    public void printParams() {
        System.out.printf("%d routes, %d coaches, %d seats, %d stations\n", 
                this.routenum, this.coachnum, this.seatnum, this.stationnum);
    }

    private void initPotentialQueue() {
        this.potentialQueue = new LockFreeQueue[this.routenum+1];
        for (int i = 0; i <= this.routenum; i++) {
            this.potentialQueue[i] = new LockFreeQueue<Integer>();
            for (int j = 0; j < this.seatPerTrain; j++) {
                this.potentialQueue[i].enqueue(new Integer(j));
            }
        }
    }

    private void initRouteIntervalCounter() {
        this.routeIntervalCounter = new AtomicInteger[this.routenum+1][];
        for (int i = 0; i <= this.routenum; i++) {
            this.routeIntervalCounter[i] = new AtomicInteger[this.stationnum];
            for (int j = 0; j < this.stationnum; j++) {
                this.routeIntervalCounter[i][j] = new AtomicInteger(0);
            }
        }
    }

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        this.intervalnum = this.stationnum * (this.stationnum - 1) / 2;
        this.seatPerTrain = this.coachnum * this.seatnum;
        InitializeSeats();
        SetTicketSet();
        SetRemainingTicketSetIndexMap();
        if (this.USE_POTENTIAL_QUEUE)
            initPotentialQueue();
        // printParams();
        this.ticketRegisteringThread = new RemainingTicketProcessingThread(); 
        this.ticketRegisteringThread.setDaemon(true);
        this.ticketRegisteringThread.start();
        if (this.USE_SOLDOUT_INDICATOR) {
            initRouteIntervalCounter();
        }
    }


    AtomicLong systemtid = new AtomicLong(2);

    private long getSystemid() {
        return this.systemtid.get();
    }

    private final int getSeatIndex(int coach, int seat) {
        int ind = (coach - 1) * this.seatnum + seat - 1;
        //System.out.printf("getSeatIndex: coach = %d, seat = %d, ind is %d\n",coach, seat, ind);
        return ind;
    }

    private final int seatIndexToCoach(int ind) {
        int coach = (int) (ind / this.seatnum) + 1;
        //System.out.printf("seatIndexToCoach: ind = %d, coach = %d\n", ind, coach);
        return coach;
    }

    private final int seatIndexToSeat(int ind) {
        int seat = ind % this.seatnum + 1;
        //System.out.printf("seatIndexToSeat: ind = %d, seat = %d\n", ind, seat);
        return seat;
    }

    private final int getRemainingTicketSetIndex(int departure, int arrival) {
        return departure + arrival * (arrival - 3) / 2;
    }

    private final int remainingTicketSetIndexToArrival(int ind) {
        return remainingTicketSetIndexMap[ind];
    }

    private final int remainingTicketSetIndexToDeparture(int ind){
        int y = remainingTicketSetIndexToArrival(ind);
        return ind - y * (y - 3) / 2;
    }

    // This function set bits [x, y] to 1 (including x and y)
    // [32, 31, ..., 2, 1]
    // x is less than y
    // x, y belongs to [1,NUM_BITS]
    public final int setBitsToOne(int num, int x, int y) { // x should be less than y
        int xBase = 0xffffffff << (x - 1);
        int yBase = 0xffffffff >>> (32 - y);
        // System.out.printf("num is 0x%x, from %d, to %d, is set one to 0x%x\n",
        return num | (xBase & yBase);
    }

    // This function set bits [x, y] to 0 (including x and y)
    // x is less than y
    // x, y belongs to [1,NUM_BITS]
    public final int setBitsToZero(int num, int x, int y) { // x should be less than y
        int xBase = (int)(0xffffffffL >>> (33 - x));
        int yBase = 0xffffffff  << y;
        // System.out.printf("num is 0x%x, from %d, to %d, is set zero to 0x%x\n",
        //  num, x, y+1, num & (xBase | yBase)); 
        return num & (xBase | yBase);
    }

    // From x to y ----> [x,y-1]
    // if status[x,y-1] == 00...0, return true, else return false
    public final boolean intervalIsAvailable(int status, int from, int to) {
        int base = setBitsToZero(0xffffffff, from, to-1);
        boolean res = (status | base) == base;
        // System.out.printf("status is 0x%x, base is 0x%x, from %d, to %d, is %s available\n",
        //  status, base, from, to, res ? "" : "not");
        return (status | base) == base;
    }

    // Check if the bit pos in status is 1
    public final boolean checkGivenBit(int status, int pos) {
        int base = setBitsToOne(0,pos,pos);
        return (base & status) == base;
    }

    private final int mask(int num, int x, int y) {
        return setBitsToOne(0xffffffff, x, y) & num;
    }

    public boolean isSoldOut(int route, int departure, int arrival) {
        for (int i = departure; i < arrival; i++) {
            if (routeIntervalCounter[route][i].get() >= this.seatPerTrain)
                return true;
        }
        return false;
    }

    private boolean updateRouteIntervalCounter(int route, int departure, int arrival, Operation type) {
        int inter;
        // boolean[] flag = new boolean[arrival-departure+1];
        if (type == Operation.BUY) {
            for (inter = departure; inter < arrival; inter++) {
                int count;
                // Acquire intervals        
                if (routeIntervalCounter[route][inter].getAndIncrement() >= this.seatPerTrain) {
                    // Roll back
                    for (int i = departure; i <= inter; i++) {
                        routeIntervalCounter[route][i].getAndDecrement();
                    }
                    return false;
                }
            }
            return true;
        }
        else if (type == Operation.REFUND) {
            for (inter = departure; inter < arrival; inter++) {
                // This do not need checking
                int count = routeIntervalCounter[route][inter].decrementAndGet();
                if (count < 0 || count >= this.seatPerTrain) {
                    assert(false);
                }
            }
            return true;
        }
        return true;
    }

    // Returning minimum bit in status of the whole empty interval
    public final int getLowerBoundOfMaximumEmptyInterval(int status, int from) {
        if (from == 1) {
            return from;
        }
        else {
            int i;
            int r = from;
            for (i = from-1; i >= 1; i--) {
                if (checkGivenBit(status,i)) { // Not empty
                    break;
                }
                else {
                    r = i;
                }
            }
            return r;
        }
    }

    // Returning maximum bit in status of the whole empty interval
    public final int getUpperBoundOfMaximumEmptyInterval(int status, int to) {
        if (to == stationnum) {
            return to-1;
        }
        else {
            int i;
            int r = to-1;
            for (i = to; i < stationnum; i++) {
                if (checkGivenBit(status,i)) { // Not empty
                    break;
                }
                else {
                    r = i;
                }
            }
            return r;
        }
    }


    private final long wrapTid(long tid, String passenger, int route, int coach,
        int seat, int departure, int arrival) {
        // Suppose tid < 2^20, passenger num < 2^20, route < 2^5, coach < 2^5, seat < 2^5,
        // departure < 2 ^ 4, arrival < 2^4
        String dest = passenger.replaceAll("[^0-9]","");
        int passengerInt = Integer.parseInt(dest);
        long finalTid = tid;
        finalTid <<= 20;
        finalTid += passengerInt;
        finalTid <<= 20;
        finalTid += route;
        finalTid <<= 5;
        finalTid += coach;
        finalTid <<= 5;
        finalTid += seat;
        finalTid <<= 5;
        finalTid += departure;
        finalTid <<= 4;
        finalTid += arrival;
        return finalTid;
    }

    private final long wrapTid(Ticket ticket) {
        String dest = ticket.passenger.replaceAll("[^0-9]","");
        
        int passengerInt = Integer.parseInt(dest);
        // System.out.println("get passenger Int " + passengerInt);
        long finalTid = ticket.tid;
        finalTid <<= 20;
        finalTid += passengerInt;
        finalTid <<= 5;
        finalTid += ticket.route;
        finalTid <<= 5;
        finalTid += ticket.coach;
        finalTid <<= 5;
        finalTid += ticket.seat;
        finalTid <<= 4;
        finalTid += ticket.departure;
        finalTid <<= 4;
        finalTid += ticket.arrival;
        return finalTid;       
    }


    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        if (isSoldOut(route, departure, arrival))
            return null;

        if (!updateRouteIntervalCounter(route, departure, arrival, Operation.BUY)) {
            return null;
        }
        else {
            
            Ticket ticket = null;
            // if ((ticket = pool.take()) == null)
                ticket = new Ticket();
            long tid = this.systemtid.getAndIncrement();

            // if (ticket.tid % 1000000 == 0)
            //     printSoldTicketSetMaxElem();


            int ind;
            int initialSeatIndex;
            Integer indFromPotentialQueue = this.potentialQueue[route].dequeue();
            if (indFromPotentialQueue == null) {
                Random rand = new Random();
                initialSeatIndex = rand.nextInt(this.coachnum * this.seatnum);
                ind = initialSeatIndex;
            }
            else {
                initialSeatIndex = indFromPotentialQueue.intValue();
                ind = initialSeatIndex;
            }
        // Randomly choose a seat to start
        // ind = indFromPotentialQueue;
            int status;
    bretry: while(true)
    {
            status = seats[route][ind].get();
            if (intervalIsAvailable(status,departure,arrival)) {
                // If the status is modified, retry with the same seat
                if (!seats[route][ind].compareAndSet(
                    status,setBitsToOne(status,departure,arrival-1))) {
                    continue bretry;
                }
                // If succeeds, wrap the ticket with coach and seat
                else {
                    int coach = seatIndexToCoach(ind);
                    int seat = seatIndexToSeat(ind);
                    //System.out.println("ind"+ind);
                    //System.out.println("Success, buying ticket of " + ticket);
                    //System.out.flush();
                    ticket.set(tid, passenger, route, coach, seat, departure, arrival);
                    // System.out.println(ticket);
                    break bretry;
                }
            }
            // If not available, choose the next seat and retry
            else {
                // Saturate
                if (++ind >= this.coachnum * this.seatnum) {
                    ind = 0;
                }
                if (ind != initialSeatIndex) {
                    continue bretry;
                }
                // If all failed, out
                else {
                    updateRouteIntervalCounter(route, departure, arrival, Operation.REFUND);
                    // pool.put(ticket);
                    return null;
                }
            }
    }


            Long soldTicket = new Long(wrapTid(ticket));

            if (!this.soldTicketSet.add(soldTicket)) {
                System.out.println("Error adding sold ticket to hashset");
            }
            RegisterRequest request;
                request = new RegisterRequest(
                    Operation.BUY, ticket.route, ticket.departure, ticket.arrival, status, ind);

            if (this.USE_PROPOSAL)
                proposalSetProcessingQueue.enqueue(request);
            remainingTicketProcessingQueue.enqueue(request);
            //System.out.println("Buying ticket of " + ticket);
            //System.out.flush();
            return ticket;
        }
    }

    public int inquiry(int route, int departure, int arrival) {
        int remaining = this.remainingTickets[route][getRemainingTicketSetIndex(departure,arrival)].get();
        return remaining;
    }

    public boolean refundTicket(Ticket ticket) {

        Long soldTicket = new Long(wrapTid(ticket));

        if (!soldTicketSet.remove(soldTicket)) {
            return false;
        }
        else {
            updateRouteIntervalCounter(ticket.route, ticket.departure, ticket.arrival, Operation.REFUND);
            int seatIndex;
rretry: while(true)
{
            seatIndex = getSeatIndex(ticket.coach,ticket.seat);
            int status = seats[ticket.route][seatIndex].get();
            if (!seats[ticket.route][seatIndex].compareAndSet(
                status,setBitsToZero(
                    status,ticket.departure,ticket.arrival-1))) {
                continue rretry;
            }
            if (this.USE_POTENTIAL_QUEUE)
                this.potentialQueue[ticket.route].enqueue(new Integer(seatIndex));
            
            RegisterRequest request = null;
                request = new RegisterRequest(
                    Operation.REFUND, ticket.route, ticket.departure, ticket.arrival, status, seatIndex);

            if (this.USE_PROPOSAL)
                proposalSetProcessingQueue.enqueue(request);
            remainingTicketProcessingQueue.enqueue(request);
            // pool.put(ticket);
            return true;
}

        }
    }
}
