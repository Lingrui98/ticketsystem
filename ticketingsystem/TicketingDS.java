package ticketingsystem;

import java.util.concurrent.atomic.*;
import java.util.*;

//class TicketWithHash extends Ticket {
//	long tid;
//	String passenger;
//	int route;
//	int coach;
//	int seat;
//	int departure;
//  int arrival;
//  @Override
//  public int hashCode() {
//        System.out.println("tid is " + tid + " int tid is" + (int)tid);
//        return (int) tid;
//    }
//}


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

    protected LockFreeHashSet<Ticket> soldTicketSet = new LockFreeHashSet<Ticket>(0xffffff);

    protected AtomicInteger[][] remainingTickets = null;

    Thread ticketRegisteringThread;

    enum Operation {
        BUY, REFUND;
    }

    enum Status {
        EMPTY, NOTAVAILABLE;
    }

    private class RegisterRequest {
        Operation type;
        int route;
        int departure;
        int arrival;
        int status;

        public RegisterRequest(Operation type, int route, int departure, int arrival, int status) {
            this.type = type;
            this.route = route;
            this.departure = departure;
            this.arrival = arrival;
            this.status = status;
        }
    }

    protected LockFreeQueue<RegisterRequest> remainingTicketProcessingQueue = new LockFreeQueue<RegisterRequest>();


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

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        this.intervalnum = this.stationnum * (this.stationnum - 1) / 2;
        this.seatPerTrain = this.coachnum * this.seatnum;
        printParams();
        InitializeSeats();
        SetTicketSet();
        SetRemainingTicketSetIndexMap();
        this.ticketRegisteringThread = new RemainingTicketProcessingThread(); 
        this.ticketRegisteringThread.setDaemon(true);
        this.ticketRegisteringThread.start();
    }

    public TicketingDS() {
        printParams();
        InitializeSeats();
        SetTicketSet();
        SetRemainingTicketSetIndexMap();
        this.ticketRegisteringThread = new RemainingTicketProcessingThread(); 
        this.ticketRegisteringThread.setDaemon(true);
        this.ticketRegisteringThread.start();
    }


    AtomicLong systemtid = new AtomicLong(0);

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
        int xBase = 0xffffffff >>> (33 - x);
        xBase = ~xBase;
        int yBase = 0xffffffff >>> (32 - y);
        return num | (xBase & yBase);
    }

    // This function set bits [x, y] to 0 (including x and y)
    // x is less than y
    // x, y belongs to [1,NUM_BITS]
    public final int setBitsToZero(int num, int x, int y) { // x should be less than y
        int xBase = 0xffffffff >>> (33 - x);
        int yBase = 0xffffffff >>> (32 - y);
        yBase = ~yBase;
        return num & (xBase | yBase);
    }

    // From x to y ----> [x,y-1]
    // if status[x,y-1] == 00...0, return true, else return false
    public final boolean intervalIsAvailable(int status, int from, int to) {
        int base = setBitsToZero(0xffffffff, from, to-1);
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
        // Randomly choose a seat to start
        Random rand = new Random();
        int initialSeatIndex = rand.nextInt(this.coachnum * this.seatnum);
        int ind = initialSeatIndex;
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
                ticket.coach = seatIndexToCoach(ind);
                ticket.seat = seatIndexToSeat(ind);
                //System.out.println("ind"+ind);
                //System.out.println("Success, buying ticket of " + ticket);
                //System.out.flush();
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
        Ticket soldTicket = new Ticket();

        soldTicket.tid = ticket.tid;
        soldTicket.passenger = ticket.passenger;
        soldTicket.route = ticket.route;
        soldTicket.departure = ticket.departure;
        soldTicket.arrival = ticket.arrival;
        soldTicket.coach = ticket.coach;
        soldTicket.seat = ticket.seat;

        if (!this.soldTicketSet.add(soldTicket)) {
            System.out.println("Error adding sold ticket to hashset");
        }
        RegisterRequest request = new RegisterRequest(Operation.BUY, route, departure, arrival, status);
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
        Ticket soldTicket = new Ticket();

        soldTicket.tid = ticket.tid;
        soldTicket.passenger = ticket.passenger;
        soldTicket.route = ticket.route;
        soldTicket.departure = ticket.departure;
        soldTicket.arrival = ticket.arrival;
        soldTicket.coach = ticket.coach;
        soldTicket.seat = ticket.seat;

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
            RegisterRequest request = new RegisterRequest(
                Operation.REFUND, ticket.route, ticket.departure, ticket.arrival, status);
            remainingTicketProcessingQueue.enqueue(request);
            return true;
}

        }
    }
}
