package ticketingsystem;

import java.util.concurrent.atomic.*;

public class TicketingDS implements TicketingSystem {

	//ToDo
    private int routenum = 5;
    private int coachnum = 8;
    private int seatnum = 100;
    private int stationnum = 10;
    private int threadnum = 16;

    protected AtomicInteger[][] seats = null;

    protected LockFreeHashSet<Ticket> ticketSet = new LockFreeHashSet<Ticket>(p);


    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        InitializeSeats();
        //SetTicketSet();
    }

    public TicketingDS() {
        InitializeSeats();
        //SetTicketSet();
    }

    private InitializeSeats() {
        this.seats = new AtomicInteger[this.routenum+1][];
        int i = 0, j = 0;
        for (i = 0; i <= this.routenum; i++) {
            this.seats[i] = new AtomicInteger[this.coachnum * this.seatnum];
            for (j = 0; j < this.coachnum * this.seatnum; j++) {
                this.seats[i][j] = new AtomicInteger(0);
            }
        }
    }

    private AtomicLong systemtid = 0;

    private long getSystemid() {
        return this.systemtid.get();
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
        Ticket.tid = systemtid.getAndIncrement();
        ticket.passenger = passenger;
        ticket.route = route;
        ticket.departure = departure;
        ticket.arrival = arrival;
        // Randomly choose a seat to start
        Random rand = new Random();
        int seatIndex = rand.nextInt(this.coachnum * this.seatnum);

retry:
        int status = seats[route][seatIndex].get();
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
