package ticketingsystem;

import java.util.concurrent.atomic.*;
import java.util.function.*;

public class TicketingDS implements TicketingSystem {

	//ToDo
    private int routenum = 5;
    private int coachnum = 8;
    private int seatnum = 100;
    private int stationnum = 10;
    private int threadnum = 16;

    private int seatPerTrain = coachnum * seatnum;
    private int totalSeatNum = seatPerTrain * routenum;


    AtomicInteger[] trains = null;
    // 31 : 2*stationnum  | 2*stationnum-1 : 0
    //    Unused          | Bitset of status of each station(departure, arrive)

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        this.seatPerTrain = this.seatnum * this.coachnum;
        this.totalSeatNum = this.seatPerTrain * this.routenum;
        SetTrains(routenum, coachnum, seatnum, stationnum);
    }

    // This function set bits [x, y] to 1 (including x and y)
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

    IntBinaryOperator buyUpdate = new IntBinaryOperator() {
        @Override
        public int applyAsInt(int left, int right) {
            // left is current value
            // right is a number mapped from (depature, arrival)
            int y = ticketSetIndexToArrival(right);
            int x = ticketSetIndexToDeparture(right);
            return setBitsToOne(left, x, y);
        }
    }

    IntBinaryOperator refundUpdate = new IntBinaryOperator() {
        @Override
        public int applyAsInt(int left, int right) {
            // left is current value
            // right is a number mapped from (depature, arrival)
            int y = ticketSetIndexToArrival(right);
            int x = ticketSetIndexToDeparture(right);
            return setBitsToZero(left, x, y);
        }
    }



    public TicketingDS() {
        SetTrains(this.routenum, this.coachnum, this.seatnum, this.stationnum);
    }

    private void SetTrains() {
        this.trains = new AtomicInteger[this.totalSeatNum];
        int i = 0;
        for (i = 0; i < this.totalSeatNum; i++) {
            this.trains[i].set(0);
        }
    }

    private AtomicLong systemtid = 0;

    private long getSystemid() {
        return this.systemtid.get();
    }

    private PerSeatDS locatePerSeatDS(int route, int coach, int seat) {
        return trains[route].coaches[coach].seats[seat];
    }

    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        Ticket ticket = new(Ticket);
        Ticket.tid = systemtid.getAndAccumulate();
    }
}
