package ticketingsystem;

import java.util.concurrent.atomic.*;

public class TicketingDS implements TicketingSystem {

	//ToDo
    private int routenum = 5;
    private int coachnum = 8;
    private int seatnum = 100;
    private int stationnum = 10;
    private int threadnum = 16;

    private int perRouteSetNum = stationnum * (stationnum -  1) / 2;
    private int ticketSetNum = routenum * perRouteSetNum;
    private int seatPerCoach = coachnum * seatnum;

    LockFreeHashSet<Integer>[] ticketSetArray = null;
    // The hashset is indexed by its value
    // the value is an index calculated from (coach, seat),
    //   maximum is coach*seat

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        this.perRouteSetNum = (this.stationnum - 1) * this.stationnum / 2;
        this.ticketSetNum = this.routenum * this.perRouteSetNum;
        this.seatPerCoach = this.coachnum * this.seatnum;
        SetSets();
    }

    public TicketingDS() {
        SetSets();
    }

    private void SetSets() {
        int i = 0;
        int j = 0;
        this.ticketSetArray = new LockFreeHashSet<Integer>[this.ticketSetNum];
        for (i = 0; i < this.ticketSetNum; i++) {
            this.ticketSetArray[i] = new LockFreeHashSet<Integer>(this.coachnum * this.seatnum); //TO modify Capacity
            for (j = 0; j < this.seatPerCoach; j++) {
                this.ticketSetArray[i].add(j);
            }
        }
    }

    private AtomicLong systemtid = 0;

    private long getSystemid() {
        return this.systemtid.get();
    }

    private final int getTicketSetIndex(int route, int departure, int arrival) {
        return (route - 1) * this.perRouteSetNum + departure +
            arrival * (arrival - 3) / 2;
    }

    private final int getSeatIndex(int coach, int seat) {
        return (coach - 1) * this.seatnum + seat - 1;
    }

    private final int seatIndexToCoach(int ind) {
        return (int) (ind / this.seatnum) + 1;
    }

    private final int seatIndexToSeat(int ind) {
        return ind % this.seatnum + 1;
    }

    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        Ticket ticket = new(Ticket);
        Ticket.tid = systemtid.getAndAccumulate();
        ticket.passenger = passenger;
        ticket.route = route;
        ticket.departure = departure;
        ticket.arrival = arrival;
        LockFreeHashSet<Integer> set = this.ticketSetArray[getTicketSetIndex(route, departure, arrival)];
        // Ramdomly remove an item, if succeeds, reutrn true
        int ind;
        if (ind = (int)set.randomPop() >= 0) {
            ticket.coach = seatIndexToCoach(ind);
            ticket.seat = seatIndexToSeat(ind);
            RegisterTicket(ticket); // First register in lock-free queue, then a thread handle it in turn
            EliminateCorrespondingTickets(): //
            return ticket;
        }
        else {
            return null;
        }
    }

    public int inquiry(int route, int departure, int arrival) {
        LockFreeHashSet<Integer> set = this.ticketSetArray[getTicketSetIndex(route, departure, arrival)];
        return set.setsize;
    }

    public boolean refundTicket(Ticket ticket) {
        Ticket 
