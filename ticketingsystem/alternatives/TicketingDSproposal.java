package ticketingsystem;

import java.util.concurrent.atomic.*;
import java.util.*;

public class TicketingDS implements TicketingSystem {

	//ToDo
    private int routenum = 5;
    private int coachnum = 8;
    private int seatnum = 100;
    private int stationnum = 10;
    private int threadnum = 16;
    private int intervalnum = 45;
    private int seatPerTrain = 800;

    // ind ---> y
    protected int[] remainingTicketSetIndexMap = null;

    protected AtomicInteger[][] seats = null;

    protected SOSet<TicketWithHash> soldTicketSet = new SOSet<TicketWithHash>(0x7fffff);

    protected AtomicInteger[][] remainingTickets = null;

    protected LockFreeQueue<RegisterRequest> remainingTicketProcessingQueue = new LockFreeQueue<RegisterRequest>();

    protected LockFreeQueue<Integer>[] potentialQueue = null;

    protected SOSet<Integer>[][] ticketProposalSet = null;

    protected LockFreeQueue<RegisterRequest> proposalSetProcessingQueue = new LockFreeQueue<RegisterRequest>();

    protected AtomicInteger[][] proposal;

    Thread ticketRegisteringThread;

    Thread proposalDealingThread;

    Thread proposalingThread;

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
    }

    @Override
    protected void finalize() {
        System.out.println("TicketingDS is destroyed!");
        this.remainingTicketSetIndexMap = null;
        this.seats = null;
        this.soldTicketSet = null;
        this.remainingTickets = null;
        this.remainingTicketProcessingQueue = null;
        this.potentialQueue = null;
        this.ticketProposalSet = null;
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
                        //System.out.printf("Processing buying..Lower%d, Upper%d\n", lower, upper);
                        //System.out.flush();
                        int x, y;
                        for (x = lower; x < to; x++) {
                            for (y = from+1; y <= upper+1; y++) {
                                if (x < y) {
                                    remainingTickets[route][getRemainingTicketSetIndex(x,y)].getAndDecrement();
                                    //System.out.printf("Decrease of (%d,%d), status 0x%x, from %d, to %d\n", x, y, status, from, to);
                                    //System.out.flush();
                                }
                            }
                        }
                    }
                    else if (request.type == Operation.REFUND) {
                        int lower = getLowerBoundOfMaximumEmptyInterval(status, from);
                        int upper = getUpperBoundOfMaximumEmptyInterval(status, to);
                        //System.out.printf("Processing refunding..Lower%d, Upper%d\n", lower, upper);
                        //System.out.flush();
                        int x, y;
                        for (x = lower; x < to; x++) {
                            for (y = from+1; y <= upper+1; y++) {
                                if (x < y) {
                                    remainingTickets[route][getRemainingTicketSetIndex(x,y)].getAndIncrement();
                                    //System.out.printf("Increase of (%d,%d), status 0x%x, from %d, to %d\n", x, y, status, from, to);
                                    //System.out.flush();
                                }
                            }
                        }
                    }
                }
                
            }
        }
    }

    public class proposalSetProcessingThread extends Thread {
        public void run() {
            while (true) {
                RegisterRequest request = null;
                if ((request = proposalSetProcessingQueue.dequeue()) != null) {
                    int route = request.route;
                    int from = request.departure;
                    int to = request.arrival;
                    int status = request.status;
                    int ind = request.seatIndex;
                    // Mem usage should be lowered
                    Integer elem = new Integer(ind);
                    if (request.type == Operation.BUY) {
                        int lower = getLowerBoundOfMaximumEmptyInterval(status, from);
                        int upper = getUpperBoundOfMaximumEmptyInterval(status, to);
                        //System.out.printf("Processing buying..Lower%d, Upper%d\n", lower, upper);
                        //System.out.flush();
                        int x, y;
                        for (x = lower; x < to; x++) {
                            for (y = from+1; y <= upper+1; y++) {
                                if (x < y) {
                                    ticketProposalSet[route][getRemainingTicketSetIndex(x,y)].remove(elem);
                                    //System.out.printf("Decrease of (%d,%d), status 0x%x, from %d, to %d\n", x, y, status, from, to);
                                    //System.out.flush();
                                }
                            }
                        }
                    }
                    else if (request.type == Operation.REFUND) {
                        int lower = getLowerBoundOfMaximumEmptyInterval(status, from);
                        int upper = getUpperBoundOfMaximumEmptyInterval(status, to);
                        //System.out.printf("Processing refunding..Lower%d, Upper%d\n", lower, upper);
                        //System.out.flush();
                        int x, y;
                        for (x = lower; x < to; x++) {
                            for (y = from+1; y <= upper+1; y++) {
                                if (x < y) {
                                    ticketProposalSet[route][getRemainingTicketSetIndex(x,y)].add(elem);
                                    //System.out.printf("Increase of (%d,%d), status 0x%x, from %d, to %d\n", x, y, status, from, to);
                                    //System.out.flush();
                                }
                            }
                        }
                    }
                }
                
            }
        }
    }

   // TODO: use correct logic
    public class proposalSettingThread extends Thread {
        public void run() {
            int routenum = getRouteNum();
            int intervalnum = getIntervalNum();
            while (true) {
                for (int r = 1; r <= routenum; r++) {
                    for (int interval = 0; interval < intervalnum; interval++) {
                        Integer prop = ticketProposalSet[r][interval].propose();
                        if (prop == null)
                            ticketProposalSet[r][interval].setProposal();
                        else {
                            proposal[r][interval].set(prop.intValue());
                            continue;
                        }
                        prop = ticketProposalSet[r][interval].propose();
                        if (prop == null)
                            continue;
                        else
                            proposal[r][interval].set(prop.intValue());
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
        // System.out.printf("-----------------------------------\n");
        // System.out.printf("Space consumed:\n");
        // System.out.printf("remainingTicketSetIndexMap: %d\n", sizeof(remainingTicketSetIndexMap));
        // System.out.printf("seats: %d\n", sizeof(seats));
        // System.out.printf("soldTicketSet: %d\n", sizeof(soldTicketSet));
        // System.out.printf("remainingTickets: %d\n", sizeof(remainingTickets));
        // System.out.printf("remainingTicketProcessingQueue: %d\n", sizeof(remainingTicketProcessingQueue));
        // System.out.printf("potentialQueue: %d\n", sizeof(potentialQueue));
        // System.out.printf("ticketProposalSet: %d\n", sizeof(ticketProposalSet));
        // System.out.printf("proposalSetProcessingQueue: %d\n", sizeof(proposalSetProcessingQueue));
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

    private void initProposalSet() {
        this.ticketProposalSet = new SOSet[this.routenum+1][];
        this.proposal = new AtomicInteger[this.routenum+1][];
        for (int i = 0; i <= this.routenum; i++) {
            this.ticketProposalSet[i] = new SOSet[this.intervalnum];
            this.proposal[i] = new AtomicInteger[this.intervalnum];
            for (int j = 0; j < this.intervalnum; j++) {
                this.ticketProposalSet[i][j] = new SOSet<Integer>(0x3fff, true); // Less space consumed
                for (int k = 0; k < this.seatPerTrain; k++) {
                    this.ticketProposalSet[i][j].add(new Integer(k));
                }
                this.proposal[i][j] = new AtomicInteger();
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
        // initPotentialQueue();
        initProposalSet();
        printParams();
        this.ticketRegisteringThread = new RemainingTicketProcessingThread(); 
        this.ticketRegisteringThread.setDaemon(true);
        this.ticketRegisteringThread.start();
        this.proposalDealingThread = new proposalSetProcessingThread();
        this.proposalDealingThread.setDaemon(true);
        this.proposalDealingThread.start();
        this.proposalingThread = new proposalSettingThread();
        this.proposalingThread.setDaemon(true);
        this.proposalingThread.start();
    }

    public TicketingDS() {
        InitializeSeats();
        SetTicketSet();
        SetRemainingTicketSetIndexMap();
        // initPotentialQueue();
        initProposalSet();
        printParams();
        this.ticketRegisteringThread = new RemainingTicketProcessingThread(); 
        this.ticketRegisteringThread.setDaemon(true);
        this.ticketRegisteringThread.start();
        this.proposalDealingThread = new proposalSetProcessingThread();
        this.proposalDealingThread.setDaemon(true);
        this.proposalDealingThread.start();
        this.proposalingThread = new proposalSettingThread();
        this.proposalingThread.setDaemon(true);
        this.proposalingThread.start();
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
        //  num, x, y+1, num | (xBase & yBase));    
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
        // yBase = ~yBase;
        return num & (xBase | yBase);
    }

    // From x to y ----> [x,y-1]
    // if status[x,y-1] == 00...0, return true, else return false
    public final boolean intervalIsAvailable(int status, int from, int to) {
        int base = setBitsToZero(0xffffffff, from, to-1);
        // int xBase = 0xffffffff >>> (33 - from);
        // int yBase = 0xffffffff << (to - 1);
        // int base = 0xffffffff & (xBase | yBase);
        boolean res = (status | base) == base;
        // System.out.printf("status is 0x%x, base is 0x%x, from %d, to %d, is %s available\n",
        //  status, base, from, to, res ? "" : "not");
        return (status | base) == base;
    }

    // Check if the bit pos in status is 1
    public final boolean checkGivenBit(int status, int pos) {
        return !intervalIsAvailable(status,pos,pos+1);
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

    

    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        Ticket ticket = new Ticket();
        ticket.tid = this.systemtid.getAndIncrement();
        ticket.passenger = passenger;
        ticket.route = route;
        ticket.departure = departure;
        ticket.arrival = arrival;

        int ind;
        int initialSeatIndex;
        // Integer indFromPotentialQueue = this.potentialQueue[route].dequeue();
        // boolean proposalTaken = false;
        int indFromProposalSet = this.proposal[route][getRemainingTicketSetIndex(departure,arrival)].get();
        // boolean proposalValid = false;
        // if (indFromProposalSet == null) {
        //     Random rand = new Random();
        //     initialSeatIndex = rand.nextInt(this.coachnum * this.seatnum);
        //     ind = initialSeatIndex;
        //     // System.out.printf("Proposal null!\n");
        // }
        // else {
            // initialSeatIndex = indFromProposalSet.intValue();
            initialSeatIndex = indFromProposalSet;
            ind = initialSeatIndex;
            // proposalValid = true;
        // }
        // if (indFromPotentialQueue == null) {
        //     Random rand = new Random();
        //     initialSeatIndex = rand.nextInt(this.coachnum * this.seatnum);
        //     ind = initialSeatIndex;
        // }
        // else {
        //     initialSeatIndex = indFromPotentialQueue.intValue();
        //     ind = initialSeatIndex;
        // }
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
                // if (proposalValid){
                //     proposalValid = false;
                //     proposalTaken = false;
                //     System.out.printf("Proposal not taken!\n");
                // }
                continue bretry;
            }
            // If succeeds, wrap the ticket with coach and seat
            else {
                ticket.coach = seatIndexToCoach(ind);
                ticket.seat = seatIndexToSeat(ind);
                //System.out.println("ind"+ind);
                //System.out.println("Success, buying ticket of " + ticket);
                //System.out.flush();
                // if (proposalValid) {
                //     proposalTaken = true;
                // }
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
                return null;
            }
        }
}
        TicketWithHash soldTicket = new TicketWithHash();

        soldTicket.ticket.tid = ticket.tid;
        soldTicket.ticket.passenger = ticket.passenger;
        soldTicket.ticket.route = ticket.route;
        soldTicket.ticket.departure = ticket.departure;
        soldTicket.ticket.arrival = ticket.arrival;
        soldTicket.ticket.coach = ticket.coach;
        soldTicket.ticket.seat = ticket.seat;

        if (!this.soldTicketSet.add(soldTicket)) {
            System.out.println("Error adding sold ticket to hashset");
        }
        // if (proposalTaken && proposalValid)
        //     System.out.printf("Proposal of route %d, seat %d, coach %d taken! tid %d from %d to %d\n", 
        //         ticket.route, ticket.seat, ticket.coach, ticket.tid, ticket.departure, ticket.arrival);
        RegisterRequest request = new RegisterRequest(Operation.BUY, route, departure, arrival, status, ind);
        proposalSetProcessingQueue.enqueue(request);
        remainingTicketProcessingQueue.enqueue(request);
        //System.out.println("Buying ticket of " + ticket);
        //System.out.flush();
        return ticket;
    }

    public int inquiry(int route, int departure, int arrival) {
        AtomicInteger remaining = this.remainingTickets[route][getRemainingTicketSetIndex(departure,arrival)];
        return remaining.get();
    }

    public boolean refundTicket(Ticket ticket) {
        TicketWithHash soldTicket = new TicketWithHash();

        soldTicket.ticket.tid = ticket.tid;
        soldTicket.ticket.passenger = ticket.passenger;
        soldTicket.ticket.route = ticket.route;
        soldTicket.ticket.departure = ticket.departure;
        soldTicket.ticket.arrival = ticket.arrival;
        soldTicket.ticket.coach = ticket.coach;
        soldTicket.ticket.seat = ticket.seat;

        if (!soldTicketSet.remove(soldTicket)) {
            return false;
        }
        else {
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
            // this.potentialQueue[ticket.route].enqueue(new Integer(seatIndex));
            RegisterRequest request = new RegisterRequest(
                Operation.REFUND, ticket.route, ticket.departure, ticket.arrival, status, seatIndex);
            proposalSetProcessingQueue.enqueue(request);
            remainingTicketProcessingQueue.enqueue(request);
            return true;
}

        }
    }
}
