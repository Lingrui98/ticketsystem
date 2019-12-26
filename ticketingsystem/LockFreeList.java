package ticketingsystem;

import java.util.concurrent.atomic.*;
import java.util.Iterator;
import java.util.*;

public class LockFreeList<T> {
    Node head;
    Node tail;

    public String toString() {
        int i = this.head.key;
        int n = 1;
        Node p = head;
        String res = "";
        while (i < Integer.MAX_VALUE) {
            res += ("Node " + n + ": key=" + i + " value=" + p.value + "\n");
            p = p.next.getReference();
            if (p == null)
                break;
            n++;
            i = p.key;
        }
        return res;
    }

    public LockFreeList() {
        this.head = new Node(0);
        this.tail = new Node(Integer.MAX_VALUE);
        this.head.next = new AtomicMarkableReference<Node>(tail, false);
    }

    // For sentinel
    public LockFreeList(LockFreeList<T> parent, int key) {
        boolean splice;
        while (true) {
            Window window = find(parent.head, key);
            Node pred = window.pred;
            Node curr = window.curr;
            if (curr.key == key) { // has been present
                this.head = curr;
                break;
            }
            else {
                Node node = new Node(key);
                node.next.set(pred.next.getReference(), false);
                splice = pred.next.compareAndSet(curr, node, false, false);
                if (splice){
                    this.head = node;
                    break;
                }
                else
                    continue;
            }
        }
	}

    private class Node {
        public int key;
        public T value;
        AtomicMarkableReference<Node> next;

        Node(T x, int key) {
            this.key = key;
            this.value = x;
            this.next = new AtomicMarkableReference<LockFreeList<T>.Node>(null, false);
        }

        Node(int key) {
            this.key = key;
            this.next = new AtomicMarkableReference<LockFreeList<T>.Node>(null, false);
        }
    }

    class Window {
        public Node pred;
        public Node curr;
        Window(Node pred, Node curr) {
            this.pred = pred;
            this.curr = curr;
        }
    }

    public Window find(Node head, int key) {
        Node pred = null, curr = null, succ = null;
        boolean[] marked = {false};
        boolean snip;
        retry: while (true) {
            pred = head;
            curr = pred.next.getReference();
            while (true) {
                succ = curr.next.get(marked);
                while (marked[0]) {
                    snip = pred.next.compareAndSet(curr, succ, false, false);
                    if (!snip)
                        continue retry;
                    curr = succ;
                    succ = curr.next.get(marked);
                }
                if (curr.key >= key)
                    return new Window(pred, curr);
                pred = curr;
                curr = succ;
            }
        }
    }

    public boolean remove(int key) {
        boolean snip;
        while (true) {
            Window window = find(this.head,key);
            Node pred = window.pred;
            Node curr = window.curr;
            if (curr.key != key) {
                return false;
            }
            else {
                Node succ = curr.next.getReference();
                snip = curr.next.compareAndSet(succ, succ, false, true);
                if (!snip)
                    continue;
                pred.next.compareAndSet(curr, succ, false, false);
                return true;
            }
        }
    }

    public boolean add(T x, int key) {
        boolean splice;
        while (true) {
            Window window = find(head, key);
            Node pred = window.pred;
            Node curr = window.curr;
            if (curr.key == key) {
                return false;
            }
            else {
                Node node = new Node(x, key);
                node.next.set(curr, false);
                if (pred.next.compareAndSet(curr, node, false, false))
                    return true;
            }
        }
    }

    public boolean contains(int key) {
        boolean[] marked = {false};
        Node curr = this.head;
        while (curr.key < key)
            curr = curr.next.getReference();
        Node succ = curr.next.get(marked);
        return (curr.key == key && !marked[0]);
    }

}