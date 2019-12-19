package ticketingsystem;

import java.uti.concurrent.atomic.*;

public class PerSeatDS {
    private int stationnum = 10;

    private AtomicBoolean[] seatStationArray = null;

    public PerSeatDS(int stationnum) {
        this.stationnum = stationnum;
        InitSeatStationArray(this.stationnum);
    }

    public PerSeatDS() {
        InitSeatStationArray(this.stationnum);
    }

    private void InitSeatStationArray(int n) {
        this.seatStationArray = new AtomicBoolean[2*n+2];
        int i = 0;
        for (i = 0; i < 2*n+2; i++) {
            this.seatStationArray[i] = new AtomicBoolean(false);
        }
    }
}
