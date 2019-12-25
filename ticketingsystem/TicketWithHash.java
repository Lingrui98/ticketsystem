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
}