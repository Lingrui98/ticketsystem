/*
 * BucketList.java
 *
 * Created on December 30, 2005, 3:24 PM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */
package ticketingsystem;

import java.util.concurrent.atomic.*;
import java.util.Iterator;
import java.util.*;

/**
 * @param T
 *            item type
 * @author Maurice Herlihy
 */
public class BucketList<T> implements BucketSet<T> {
	static final int WORD_SIZE = 25;
	static final int LO_MASK = 0x00000001;
	static final int HI_MASK = 0x00800000;
	static final int MASK = 0x00FFFFFF;
	Node head,tail;
	protected Node freeNode = null;

	/**
	 * Constructor
	 */
	public BucketList() {
		this.head = new Node(0);
		this.head.ref.incrementAndGet();
		this.tail=new Node(Integer.MAX_VALUE);
		this.tail.ref.incrementAndGet();
		this.tail.ref.incrementAndGet();
		this.head.next = new AtomicStampedReference<Node>(tail, 0);
	}

	private BucketList(Node e) {
		this.head = e;
	}

	/**
	 * Restricted-size hash code
	 * 
	 * @param x
	 *            object to hash
	 * @return hash code
	 */
	public static int hashCode(Object x) {
		return x.hashCode() & MASK;
	}

	public boolean add(T x) {
		int key = makeRegularKey(x);
        System.out.println("Trying to add key " + key);
		boolean splice;
		while (true) {
			// find predecessor and current entries
			Window window = find(head, key);
			Node pred = window.pred;
			Node curr = window.curr;
			int currMark = pred.next.getStamp();
			Node entry;
			// is the key present?
			if (curr.key == key) {
				pred.ref.decrementAndGet();
				curr.ref.decrementAndGet();
                System.out.println("Key" + key + " present, adding failed");
				return false;
			} else {
				// splice in new entry
				if( freeNode==null )
					entry = new Node(key, x);
				else{
					entry=freeNode;
					freeNode = freeNode.next.getReference();
				}


				curr.ref.incrementAndGet();
				entry.next.set(curr, currMark);
				entry.ref.set(1);
				splice = pred.next.compareAndSet(curr, entry, currMark, currMark + 1);
				if (splice){
					curr.ref.decrementAndGet();
                    System.out.println("key " + key + " added");
					return true;
				}
				else{
					entry.ref.decrementAndGet();
				}
				pred.ref.decrementAndGet();
				curr.ref.decrementAndGet();
			}
		}
	}

	public boolean remove(T x) {
		int key = makeRegularKey(x);
		while (true) {
			// find predecessor and current entries
			Window window = find(head, key);
			Node pred = window.pred;
			Node curr = window.curr;
			int[] nMarked = { 0 };

			// is the key present?
			if (curr.key != key) {
                System.out.println("key " + key +" not present, failed to remove");
				pred.ref.decrementAndGet();
				curr.ref.decrementAndGet();
				return false;
			} else {
				if (pred.next.attemptStamp(curr, -1)) {
					pred.ref.decrementAndGet();
					curr.ref.decrementAndGet();
                    System.out.println("key " + key +" removed");
					return true;
				}
				pred.ref.decrementAndGet();
				curr.ref.decrementAndGet();
			}
		}
	}

	public boolean contains(T x) {
		int key = makeRegularKey(x);
		Window window = find(head, key);
		Node pred = window.pred;
		Node curr = window.curr;
		pred.ref.decrementAndGet();
		curr.ref.decrementAndGet();
		return (curr.key == key);
	}

	public BucketList<T> getSentinel(int index) {
		int key = makeSentinelKey(index);
		boolean splice;
		while (true) {
			// find predecessor and current entries
			Window window = find(head, key);
			Node pred = window.pred;
			Node curr = window.curr;
			// is the key present?
			if (curr.key == key) {
				return new BucketList<T>(curr);
			} else {
				// splice in new entry
				Node entry = new Node(key);

				entry.next.set(pred.next.getReference(), entry.next.getStamp() + 1); 
				splice = pred.next.compareAndSet(curr, entry, pred.next.getStamp(), pred.next.getStamp() + 1);
				if (splice)
					return new BucketList<T>(entry);
				else
					continue;
			}
		}
	}

	public static int reverse(int key) {
		int loMask = LO_MASK;
		int hiMask = HI_MASK;
		int result = 0;
		for (int i = 0; i < WORD_SIZE; i++) {
			if ((key & loMask) != 0) { // bit set
				result |= hiMask;
			}
			loMask <<= 1;
			hiMask >>>= 1; // fill with 0 from left
		}
		return result;
	}

	public int makeRegularKey(T x) {
		int code = x.hashCode() & MASK; // take 3 lowest bytes
        System.out.println("In makeRegularKey, hashCode is " + x.hashCode() +
                "key is " + reverse(code | HI_MASK));
		return reverse(code | HI_MASK);
	}

	private int makeSentinelKey(int key) {
		return reverse(key & MASK);
	}

	// iterate over Set elements
	public Iterator<T> iterator() {
		throw new UnsupportedOperationException();
	}

	private class Node {
		public int key;
		public T value;
		public AtomicInteger ref = new AtomicInteger(0);

		AtomicStampedReference<Node> next;

		Node(int key, T object) { // usual constructor
			this.key = key;
			this.value = object;
			this.next = new AtomicStampedReference<BucketList<T>.Node>(null, 0);
		}

		Node(int key) { // sentinel constructor
			this.key = key;
			this.next = new AtomicStampedReference<BucketList<T>.Node>(null, 0);
		}

		Node getNext() {
			int[] cMarked = { 0 }; // is curr marked?
			int[] sMarked = { 0 }; // is succ marked?
			Node temp = this.next.get(cMarked);
			while(temp.ref.incrementAndGet() ==1){
				temp = this.next.get(cMarked);
			}
			Node curr = temp;

			while (cMarked[0] == -1) {
				Node temp2 = curr.next.getReference();
				if(temp2 == null || temp2.ref.incrementAndGet() == 1){

					temp = this.next.get(cMarked);
					while(temp.ref.incrementAndGet() ==1){
						temp = this.next.get(cMarked);
					}
					curr = temp;

					continue;
				}
				Node succ = temp2;
				sMarked[0] = temp2.next.getStamp();
				succ.ref.incrementAndGet();
				if(this.next.compareAndSet(curr, succ, -1, sMarked[0])) {
					curr.ref.decrementAndGet();

					if (curr.ref.decrementAndGet() == 0) {
						curr.next.set(null, -100);

						if( freeNode!=null )
							curr.next.set(freeNode, -100);

						freeNode=curr;

					}

				} else {
					succ.ref.decrementAndGet();
				}


				Node temp3 = this.next.get(cMarked);
				while(temp3.ref.incrementAndGet() ==1){
					temp3 = this.next.get(cMarked);
				}
				curr.ref.decrementAndGet();
				curr = temp3;

				succ.ref.decrementAndGet();
			}
			return curr;
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
		head.ref.incrementAndGet();
		Node pred = head;
		Node curr = head.getNext();
		while (curr.key < key) {
			curr.ref.incrementAndGet();
			pred.ref.decrementAndGet();
			pred = curr;
			
			curr = curr.getNext();
			pred.ref.decrementAndGet();
		}
		return new Window(pred, curr);
	}
}
