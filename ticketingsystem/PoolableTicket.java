package ticketingsystem;

public class PoolableTicket extends PoolableObject<T>{
	Ticket ticket;

    // public String toString() {
    //     return "[tid=" + tid +
    //         ",passenger=" + passenger +
    //         ",route=" + route +
    //         ",coach=" + coach +
    //         ",seat" + seat +
    //         ",departure=" + departure +
    //         ",arrival=" + arrival;
    // }
    private void setTicketToNull(Ticket t) {
        t.tid = -1;
        t.passenger = "";
        t.route = 0;
        t.coach = 0;
        t.seat = 0;
        t.departure = 0;
        t.arrival = 0;
    }

    // public void onReturn(T object) {
    //     setTicketToNull((Ticket) object);
    // }

    // public void onTake(T object) {
    //     return;
    // }

    // @Override
    // public T create() {
    //     return 
    // }

	public void set(long tid, String passenger, int route, int coach, int seat, int departure, int arrival) {
		this.tid = tid;
        this.passenger = passenger;
        this.route = route;
        this.coach = coach;
        this.seat = seat;
        this.departure = departure;
        this.arrival = arrival;
	}
}