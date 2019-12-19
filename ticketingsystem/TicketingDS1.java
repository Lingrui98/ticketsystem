package ticketingsystem;

import java.util.concurrent.atomic.*;

public class TicketingDS implements TicketingSystem {

	//ToDo
    private int routenum = 5;
    private int coachnum = 8;
    private int seatnum = 100;
    private int stationnum = 10;
    private int threadnum = 16;

    Train[] trains = null;

    public class Train {
        int c;  //Coachnum
        int s;  //Seatnum
        int st; //Stationnum

        public Coach[] coaches = null;

        public class Coach {
            int s;  //Seatnum
            int st; //Stationnum

            public PerSeatDS[] seats = null;

            Coach(int seatnum){
                this.s = seatnum;
                SetSeats(this.s);
            }

            Coach() {
                this.s = 8;
                SetSeats(this.s);
            }

            private void SetSeats(int seatnum, int stationnum) {
                this.seats = new PerseatDS[seatnum+1];
                int i = 0;
                for (i = 0; i <= seatnum; i++) {
                    this.seats[i] = new PerseatDS(stationnum);
                }
            }
        }

        Train(int coachnum, int seatnum, int stationnum) {
            this.c = coachnum;
            this.s = seatnum;
            this.st = stationnum;
            SetCoaches(this.c, this.s, this.st);
        }

        Train() {
            this.c = 5;
            this.s = 100;
            this.st = 10;
            SetCoaches(this.c, this.s, this.st);
        }

        private void SetCoaches(int coachnum, int seatnum, int stationnum) {
            this.coaches = new Coach[coachnum+1];
            int i = 0;
            for (i = 0; i <= coachnum; i++) {
                this.coaches[i] = new Coach(seatnum, stationnum);
            }
        }
    }

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        SetTrains(routenum, coachnum, seatnum, stationnum);
    }

    public TicketingDS() {
        SetTrains(this.routenum, this.coachnum, this.seatnum, this.stationnum);
    }

    private void SetTrains(int routenum, int coachnum, int seatnum, int stationnum) {
        this.trains = new Train[routenum+1];
        int i = 0;
        for (i = 0; i <= routenum; i++) {
            this.trains[i] = new Train(coachnum, seatnum, stationnum)；
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
