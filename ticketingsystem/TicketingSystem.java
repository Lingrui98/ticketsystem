package ticketingsystem;

class Ticket extends IPoolCell{
	long tid;
	String passenger;
	int route;
	int coach;
	int seat;
	int departure;
	int arrival;

    public String toString() {
        return "[tid=" + tid +
            ",passenger=" + passenger +
            ",route=" + route +
            ",coach=" + coach +
            ",seat" + seat +
            ",departure=" + departure +
            ",arrival=" + arrival;
    }

	public void set(long tid, String passenger, int route, int coach, int seat, int departure, int arrival) {
		this.tid = tid;
        this.passenger = passenger;
        this.route = route;
        this.coach = coach;
        this.seat = seat;
        this.departure = departure;
        this.arrival = arrival;
	}

    public void clear() {
        this.tid = -1;
        this.passenger = "";
        this.route = 0;
        this.coach = 0;
        this.seat = 0;
        this.departure = 0;
        this.arrival = 0;
    }
}


public interface TicketingSystem {
	Ticket buyTicket(String passenger, int route, int departure, int arrival);
	int inquiry(int route, int departure, int arrival);
	boolean refundTicket(Ticket ticket);
}
