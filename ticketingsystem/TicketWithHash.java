package ticketingsystem;

import java.util.*;

public class TicketWithHash {
    public Ticket ticket;

    public TicketWithHash() {
        this.ticket = new Ticket();
    }

    public int hashCode() {
        System.out.println("tid is " + this.ticket.tid + " int tid is" + (int)this.ticket.tid);
        return (int) this.ticket.tid;
    }

    public boolean equals(Object other) {
        System.out.println("equals invoked");

        if (other == this)
            return true;

        if (!(other instanceof TicketWithHash))
            return false;

        TicketWithHash temp = (TicketWithHash) other;
        return ticket.tid == temp.ticket.tid &&
               ticket.passenger.equals(temp.ticket.passenger) &&
               ticket.route == temp.ticket.route &&
               ticket.coach == temp.ticket.coach &&
               ticket.seat == temp.ticket.seat &&
               ticket.departure == temp.ticket.departure &&
               ticket.arrival == temp.ticket.arrival;
    }
}
