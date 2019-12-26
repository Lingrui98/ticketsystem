package ticketingsystem;

import java.util.*;

public class SOSetTest {
    public SOSetTest() {
    }
    public static void main(String[] args) {
        SOSet<Integer> set = new SOSet<Integer>(0xfff);
        int n;
        for (n = 0; n < 10; n++) {
            System.out.println("Adding " + n + " in set...");
            set.add(new Integer(n));
            System.out.println("After adding, set is " + set);
        }
        for (n = 0; n < 10; n++) {
            System.out.println("removing " + n + " out of set...");
            set.remove(new Integer(n));
            System.out.println("After removing, set is " + set);
        }
    }
}
