package com.sun.sgs.app.util;

import java.io.Serializable;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

/**
 * A concurrent, distributed {@code Map} implementation that
 * automatically manages the mapping storage within the datstore, and
 * supports concurrent writes.  This class is intended as a drop-in
 * replacement for the {@link java.util.HashMap} class as needed.
 * Developers are encouraged to use this class when the size of a
 * {@link java.util.HashMap} causes sufficient contention due to
 * serialization overhead.
 *
 * <p>
 *
 * As the number of mappings increases, the mappings are distributed
 * through multiple objects in the datastore, thereby mitigating the
 * cost of serializing the map.  Furthermore, map operations have been
 * implemented to minimize the locality of change.  As the map grows
 * in size, mutable operations change only a small number of managed
 * objects, thereby increasing the concurrency for multiple writes.
 *
 * <p>
 *
 * This implementation supports the contract that all keys and values
 * must be {@link Serializable}.  If a developer provides a {@code
 * Serializable} key or value that is <i>not</i> a {@code
 * ManagedObject}, this implementation will take responsibility for
 * the lifetime of that object in the datastore.  The developer will
 * be responsible for the lifetime of all {@link ManagedObject} stored
 * in this map.
 *
 * <p>
 *
 * The {@code PrefixHashMap} is implemented as a prefix tree of hash
 * maps, which provides {@code O(log(n))} performance for the
 * following operations: {@code get}, {@code put}, {@code remove},
 * {@code containsKey}, where {@code n} is the number of leaves in the
 * tree (<i>not</i> the number of elements).  Note that unlike most
 * collections, the {@code size} operation is <u>not</u> a constant
 * time operation.  Because of the concurrent nature of the map,
 * determining the size requires accessing all of the leaf nodes in
 * the tree, which takes {@code O(n + log(n))}, where {@code n} is the
 * number of leaves.  The {@code isEmpty} operation, however, is still
 * {@code O(1)}.
 *
 * <p>
 *
 * An instance of {@code PrefixHashMap} offers one parameters for
 * performance tuning: {@code minConcurrency}, which specifies the
 * minimum number of write operations to support in parallel.  As the
 * map grows, the number of supported parallel operations will also
 * grow beyond the specified minimum, but this factor ensures that it
 * will never drop below the provided number.  Setting this value too
 * high will waste space and time, while setting it too low will cause
 * conflicts until the map grows sufficiently to support more
 * concurrent operations.  Furthermore, the efficacy of the
 * concurrency depends on the distribution of hash values; keys with
 * poor hashing will minimize the actual number of possible concurrent
 * writes, regardless of the {@code minConcurrency} value.
 *
 * <p>
 *
 * This class implements all of the optional {@code Map} operations
 * and supports both {@code null} keys and values.  This map provides
 * no guarantees on the order of elements when iterating over the key
 * set, values or entry set.
 * 
 * <p>
 *
 * The iterator for this implemenation will never throw a {@link
 * java.util.ConcurrentModificationException}, unlike many of the
 * other {@code Map} implementations.
 *
 * @since 1.0
 * @version 1.3
 *
 * @see Object#hashCode()
 * @see Map
 * @see Serializable
 * @see ManagedObject
 */
@SuppressWarnings({"unchecked"})
public class PrefixHashMap<K,V> 
    extends AbstractMap<K,V>
    implements Map<K,V>, Serializable, ManagedObject {

    private static final long serialVersionUID = 1337;

    /**
     * The default number of parallel write operations used when none
     * is specified in the constructor.
     */
    private static final int DEFAULT_MINIMUM_CONCURRENCY = 1;    

    /**
     * The split factor used when none is specified in the constructor.
     */
    private static final float DEFAULT_SPLIT_FACTOR = 1.0f;    

    /**
     * The split factor used when none is specified in the
     * constructor.
     */
    private static final float DEFAULT_MERGE_FACTOR = .25f;

    
    /**
     * The default number of {@code PrefixEntry} entries per
     * array for a leaf table.
     */
    // NOTE: this should almost certainly be updated to include class
    //       overhead for the object that contains this array
    private static final int DEFAULT_LEAF_CAPACITY = 128;

    /**
     * The maximum depth of this tree
     */
    private static final int MAX_DEPTH = 32;
    
    /**
     * The parent node directly above this.  For the root node, this
     * should always be null.
     */
    private ManagedReference parent;


    // NOTE: the leftLeaf and rightLeaf references allow us to quickly
    //       iterate over the tree without needing to touch all of the
    //       intermediate nodes.

    /**
     * The leaf table immediately to the left of this table, or {@code
     * null} if this table is an intermediate node in tree.
     */
    private ManagedReference leftLeaf;

    /**
     * The leaf table immediately to the right of this table, or {@code
     * null} if this table is an intermediate node in tree.
     */
    private ManagedReference rightLeaf;


    // NOTE: either both the left and right child will be present, or
    //       neither will be
    
    /**
     * The leaf table, if any, under this table to the left
     */
    private ManagedReference leftChild;

    /**
     * The leaf table, if any, under this table to the right
     */
    private ManagedReference rightChild;
	
    /**
     * The fixed-size table for storing all Map entries.
     */
    // NOTE: this is actually an array of type PrefixEntry<K,V> but
    //       generic arrays are not allowed, so we cast the elements
    //       as necessary
    private transient PrefixEntry[] table;    
    
    /**
     * The number of elements in this table.  Note that this is
     * <i>not</i> the total number of elements in the entire tree.
     */
    private int size;

    /**
     * The maximum number of elements in this table before it will
     * split this table into two leaf tables.
     *
     * @see #split()
     */
    private int splitThreshold;

    /**
     * The minimum number of elements in this table before it will
     * attempt to merge itself with its sibling.
     *
     * @see #merge()
     */
    private int mergeThreshold;


    /**
     * The number of {@code PrefixEntry} at leaf node.
     */
    private final int leafCapacity;

    /**
     * The fraction of the leaf capacity that will cause the leaf to
     * split.
     *
     * @see #split()
     */
    private final float splitFactor;

    /**
     * The fraction of the leaf capacity that will cause the leaf to
     * merge.
     *
     * @see #merge()
     */
    private final float mergeFactor;

    /**
     * The minimum number of concurrent write operations to support.
     */
    private final int minConcurrency;

    /**
     * The minimum depth of the tree, which is controlled by the
     * minimum concurrency factor
     */
    private int minDepth;

    /**
     * The depth of this node in the tree
     */
    private final int depth;

    
    /** 
     * Constructs an empty {@code PrefixHashMap} at the provided
     * depth, with the specified minimum concurrency, split factor and
     * load factor.
     *
     * @param depth the depth of this table in the tree
     * @param minConcurrency the minimum number of concurrent write
     *        operations to support
     * @param splitFactor the fraction of the leaf capacity that will
     *        cause the leaf to split
     * @param mergeFactor the fraction of the leaf capacity that will
     *        cause the leaf to attempt merging with its sibling
     *
     * @throws IllegalArgumentException if the depth is out of the
     *         range of valid prefix lengths
     * @throws IllegalArgumentException if minConcurrency is non positive
     * @throws IllegalArgumentException if the split factor is non positive
     * @throws IllegalArgumentException if the merge factor is less
     *         than zero or greater than or equal to the split factor
     */
    private PrefixHashMap(int depth, int minConcurrency, float splitFactor,
			  float mergeFactor) {
	if (depth < 0 || depth > 32) {
	    throw new IllegalArgumentException("Illegal tree depth: " + 
					       depth);	    
	}
	if (minConcurrency <= 0) {
	    throw new IllegalArgumentException("Illegal minimum concurrency: " 
					       + minConcurrency);	    
	}
	if (splitFactor <= 0) {
	    throw new IllegalArgumentException("Illegal split factor: " + 
					      splitFactor);	    
	}
	if (mergeFactor < 0 || mergeFactor >= splitFactor) {
	    throw new IllegalArgumentException("Illegal merge factor: " + 
					       mergeFactor);	    
	}

	this.depth = depth;
	this.minConcurrency = minConcurrency;
	for (minDepth = 0; (1 << minDepth) < minConcurrency; minDepth++)
	    ;
	size = 0;
	parent = null;
	leftLeaf = null;
	rightLeaf = null;
	leftChild = null;
	rightChild = null;
	this.leafCapacity = DEFAULT_LEAF_CAPACITY;
	table = new PrefixEntry[leafCapacity];
	this.splitFactor = splitFactor;
	this.mergeFactor = mergeFactor;
	// ensure that the split threshold is at least 1
	this.splitThreshold = Math.max((int)(splitFactor * leafCapacity), 1);
	// ensure that the merge threshold is at least one less than
	// the split threshold
	this.mergeThreshold = Math.min((int)(splitFactor * leafCapacity), 
				       splitThreshold-1);       
	// Only the root note should ensure depth, otherwise this call
	// causes the children to be created in depth-first fashion,
	// which prevents the leaf references from being correctly
	// established
	if (depth == 0) 
	    ensureDepth(minDepth);
    }

    /** 
     * Constructs an empty {@code PrefixHashMap} with the provided
     * minimum concurrency.
     *
     * @param minConcurrency the minimum number of concurrent write
     *        operations supported
     *
     * @throws IllegalArgumentException if minConcurrency is non positive
     */
    public PrefixHashMap(int minConcurrency) {
	this(0, minConcurrency, DEFAULT_SPLIT_FACTOR, DEFAULT_MERGE_FACTOR);
    }


    /** 
     * Constructs an empty {@code PrefixHashMap} with the default
     * minimum concurrency (1).
     */
    public PrefixHashMap() {
	this(0, DEFAULT_MINIMUM_CONCURRENCY, 
	     DEFAULT_SPLIT_FACTOR, DEFAULT_MERGE_FACTOR);
    }

    /**
     * Constructs a new {@code PrefixHashMap} with the same mappings
     * as the specified {@code Map}, and the default 
     * minimum concurrency (1.0).
     *
     * @param m the mappings to include
     *
     * @throws NullPointerException if the provided map is null
     */
    public PrefixHashMap(Map<? extends K, ? extends V> m) {
	this(0, DEFAULT_MINIMUM_CONCURRENCY, 
	     DEFAULT_SPLIT_FACTOR, DEFAULT_MERGE_FACTOR);
	if (m == null)
	    throw new NullPointerException("The provided map is null");
	putAll(m);
    }

    /**
     * Ensures that this node has children of at least the provided
     * minimum depth.
     *
     * @param minDepth the minimum depth of the leaf nodes under this
     *        node
     *
     * @see #split()
     */
    private void ensureDepth(int minDepth) {
	if (depth >= minDepth)
	    return;
	else {
	    split(); // split first to ensure breadth-first creation
	    rightChild.get(PrefixHashMap.class).ensureDepth(minDepth);
	    leftChild.get(PrefixHashMap.class).ensureDepth(minDepth);
	}
    }

    /**
     * Clears the map of all entries in {@code O(n log(n))} time.
     * When clearing, all values managed by this map will be removed
     * from the persistence mechanism.
     */
    public void clear() {
	DataManager dm = AppContext.getDataManager();
	dm.markForUpdate(this);
	// go through and remove all the leaves
	if (leftChild == null) { // leaf node
	    for (PrefixEntry<K,V> e : table) {
		if (e != null) {
		    // remove all references that we are responsible
		    // for, only calling this when we are sure that we
		    // will never reference these entries again
		    e.unmanage();
		}
	    }
	}
	else {
	    PrefixHashMap l = leftChild.get(PrefixHashMap.class);
	    PrefixHashMap r = rightChild.get(PrefixHashMap.class);
	    l.clear();
	    r.clear();
	    dm.removeObject(l);
	    dm.removeObject(r);
	}
	if (parent == null) { // root node	    
	    if (table == null) // restore the table, if it was deleted
		table = new PrefixEntry[leafCapacity];
	    else // otherwise, clear it
		Arrays.fill(table, null);
	    size = 0;
	    parent = null;
	    leftLeaf = null;
	    rightLeaf = null;
	    leftChild = null;
	    rightChild = null;
	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(Object key) {
	return getEntry(key) != null;
    }

    /**
     * Returns the {@code PrefixEntry} associated with this key
     */ 
    private PrefixEntry<K,V> getEntry(Object key) {
	int hash = (key == null) ? 0 : hash(key.hashCode());
	PrefixHashMap<K,V> leaf = lookup(hash);
	for (PrefixEntry<K,V> e = leaf.table[indexFor(hash, leaf.table.length)];
	     e != null; e = e.next) {
	    
	    Object k;
	    if (e.hash == hash && ((k = e.getKey()) == key || 
				   (k != null && k.equals(key)))) {
		return e;
	    }
	}
	return null;
    } 

    /**
     * {@inheritDoc}
     */
    public boolean containsValue(Object value) {
	for (V v : values()) {
	    if (v == value || (v != null && v.equals(value)))
		return true;
	}
	return false;
    }

    /**
     * Merges the children nodes into this node and removes
     * them.
     *
     * @see #addEntry(PrefixEntry, int)
     */
    private void merge() {	   
	DataManager dataManager = AppContext.getDataManager();
	if (parent == null) // this node is the root!
	    return; // do not merge

	PrefixHashMap<K,V> leftChild_ = leftChild.get(PrefixHashMap.class);
	PrefixHashMap<K,V> rightChild_ = 
	    rightChild.get(PrefixHashMap.class);
	    
	// check that we are merging two leaf nodes
	if (leftChild_.leftChild != null ||
	    rightChild_.leftChild != null) 
	    // either one has children, so do not perform the merge
	    return;

	// check that their comibined sizes is less than half of the
	// split threshold, to ensure we don't split soon after the
	// merge takes place
	if ((leftChild_.size + rightChild_.size) / 2 > splitThreshold) {
	    return;
	}

	dataManager.markForUpdate(this);

	// recreate our table, as it was made null in split()
	table = new PrefixEntry[leftChild_.table.length];

	// iterate over each child's table, combining the entries into
	// this node's table.
	for (int i = 0; i < table.length; ++i) {

 	    for (PrefixEntry<K,V> e = leftChild_.table[i]; e != null; e = e.next) {
		addEntry(e, i);
	    }

 	    for (PrefixEntry<K,V> e = rightChild_.table[i]; e != null; e = e.next) {
		addEntry(e, i);
	    }
	}

	// update the remaining family references
	leftLeaf = leftChild_.leftLeaf;
	rightLeaf = rightChild_.rightLeaf;
	
	// ensure that the child's neighboring leaf reference now
	// point to this table
	if (leftLeaf != null) {
	    PrefixHashMap leftLeaf_ = leftLeaf.get(PrefixHashMap.class);
	    dataManager.markForUpdate(leftLeaf_);
	    leftLeaf_.rightLeaf = dataManager.createReference(this);
	}
	if (rightLeaf != null) {
	    PrefixHashMap rightLeaf_ = rightLeaf.get(PrefixHashMap.class);
	    dataManager.markForUpdate(rightLeaf_);	    
	    rightLeaf_.leftLeaf = dataManager.createReference(this);
	}	
	
	// mark this table as a leaf by removing the child
	// references
	leftChild = null;
	rightChild = null;

	// now delete the leaf tables
	dataManager.removeObject(leftChild_);
	dataManager.removeObject(rightChild_);
    }	

    /**
     * Divides the entires in this node into two leaf nodes on the
     * basis of prefix, and then marks this node as an intermediate
     * node.  This method should only be called when the entries
     * contained within this node have valid prefix bits remaining
     * (i.e. they have not already been shifted to the maximum
     * possible precision).
     *
     * @see #addEntry(PrefixEntry, int)
     */
    private void split() {
	    
	if (leftChild != null)  // can't split an intermediate node!
	    return;
	
	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

	PrefixHashMap<K,V> leftChild_ = 
	    new PrefixHashMap<K,V>(depth+1, minConcurrency, 
				   splitFactor, mergeFactor);
	PrefixHashMap<K,V> rightChild_ = 
	    new PrefixHashMap<K,V>(depth+1, minConcurrency, 
				   splitFactor, mergeFactor);

	// iterate over all the entries in this table and assign
	// them to either the right child or left child
	for (int i = 0; i < table.length; ++i) {

	    // go over each entry in the bucket since each might
	    // have a different prefix next
	    for (PrefixEntry<K,V> e = table[i]; e != null; e = e.next) {
		
		((((e.hash << (depth)) >>> 31) == 1) ? leftChild_ : rightChild_).
		//((e.leadingBit() == 1) ? leftChild_ : rightChild_).
		    addEntry(e, i);
	    }
	}

	// null out the intermediate node's table as an optimization
	// to reduce serialization time.
	table = null;
	size = 0;
		
	// create the references to the new children
	leftChild = dataManager.createReference(leftChild_);
	rightChild = dataManager.createReference(rightChild_);
	    
	if (leftLeaf != null) {
	    PrefixHashMap leftLeaf_ = leftLeaf.get(PrefixHashMap.class);
	    leftLeaf_.rightLeaf = leftChild;

	}
	
	if (rightLeaf != null) {
	    PrefixHashMap rightLeaf_ = rightLeaf.get(PrefixHashMap.class);
	    rightLeaf_.leftLeaf = rightChild;
	}

	// update the family links
	ManagedReference thisRef = dataManager.createReference(this);
	leftChild_.rightLeaf = rightChild;
	leftChild_.leftLeaf = leftLeaf;
	leftChild_.parent = thisRef;
	rightChild_.leftLeaf = leftChild;
	rightChild_.rightLeaf = rightLeaf;
	rightChild_.parent = thisRef;			  
	
	// invalidate this node's leaf references
	leftLeaf = null;
	rightLeaf = null;
    }

    /**
     * Locates the leaf node that is associated with the provided
     * prefix.  Upon return, the provided prefix will have been
     * shifted left according to the depth of the leaf.
     *
     * @param prefix the initial prefix for which to search 
     *
     * @return the leaf table responsible for storing all entries with
     *         the specified prefix
     */
    private PrefixHashMap<K,V> lookup(int prefix) {
	// a leading bit of 1 indicates the left child prefix
	PrefixHashMap<K,V> leaf;
 	for (leaf = this; leaf.leftChild != null && leaf.depth < MAX_DEPTH-1;) {
	    leaf = ((prefix >>> 31) == 1) 
		? leaf.leftChild.get(PrefixHashMap.class)
		: leaf.rightChild.get(PrefixHashMap.class);
 	    prefix <<= 1;
	}
	return leaf;
    }

    /**
     * Returns the value to which this key is mapped or {@code null}
     * if the map contains no mapping for this key.  Note that the
     * return value of {@code null} does not necessarily imply that no
     * mapping for this key existed since this implementation supports
     * {@code null} values.  The {@link #containsKey(Object)} method
     * can be used to determine whether a mapping exists.
     *
     * @param key the key whose mapped value is to be returned
     * @return the value mapped to the provided key or {@code null} if
     *         no such mapping exists
     */
    public V get(Object key) {

 	int hash = (key == null) ? 0 : hash(key.hashCode());
	PrefixHashMap<K,V> leaf = lookup(hash);
	for (PrefixEntry<K,V> e = leaf.table[indexFor(hash, leaf.table.length)]; 
	     e != null; 
	     e = e.next) {	    
	    Object k;
	    if (e.hash == hash && 
		((k = e.getKey()) == key || (k != null && k.equals(key)))) {
		return e.getValue();
	    }
	}
	return null;	
    }

    /**
     * A secondary hash function for better distributing the keys.
     *
     * @param h the initial hash value
     * @return a re-hashed version of the provided hash value
     */
    static int hash(int h) {
	
	/*
	 * This hash function is based on a fixed 4-byte version of
	 * lookup3.c by Bob Jenkins.  See
	 * http://burtleburtle.net/bob/c/lookup3.c for details.  This
	 * is supposed a superior hash function but testing reveals
	 * that it performs slightly worse than the current version
	 * from the JDK 1.6 HashMap.  It is being left in for future
	 * consideration once a more realistic key set can be tested
	 *  
	 * int a, b, c;
	 * a = b = 0x9e3779b9; // golden ratio, (arbitrary initial value)
	 * c = h + 4;
	 * 	
	 * a += h;
	 * 
	 * // mix, with rotations on the original values
	 * c ^= b; c -= ((b << 14) | (b >>> -14));
	 * a ^= c; a -= ((c << 11) | (c >>> -11));
	 * b ^= a; b -= ((a << 25) | (a >>> -25));
	 * c ^= b; c -= ((b << 16) | (b >>> -16));
	 * a ^= c; a -= ((c <<  4) | (c >>>  -4));
	 * b ^= a; b -= ((a << 14) | (a >>> -14));
	 * c ^= b; c -= ((b << 24) | (b >>> -24));
	 * 
	 * return c;
	 */
	
	// the HashMap.hash() function from JDK 1.6
	h ^= (h >>> 20) ^ (h >>> 12);
	return h ^ (h >>> 7) ^ (h >>> 4);

    }

    /**
     * Associates the specified key with the provided value and
     * returns the previous value if the key was previous mapped.
     * This map supports both {@code null} keys and values.
     *
     * @param key the key
     * @param value the value to be mapped to the key
     * @return the previous value mapped to the provided key, if any
     */
    public V put(K key, V value) {

	int hash = (key == null) ? 0 : hash(key.hashCode());
	PrefixHashMap<K,V> leaf = lookup(hash);
	AppContext.getDataManager().markForUpdate(leaf);

	int i = indexFor(hash, leaf.table.length);
	for (PrefixEntry<K,V> e = leaf.table[i]; e != null; e = e.next) {
	    
	    Object k;
	    if (e.hash == hash && 
		((k = e.getKey()) == key || (k != null && k.equals(key)))) {
		
		// if the keys and hash match, swap the values
		// and return the old value
		return e.setValue(value);
	    }
	}
	    
	// we found no key match, so add an entry
	leaf.addEntry(hash, key, value, i);

	return null;	
    }

    /**
     * Copies all of the mappings from the provided map into this map.
     * This operation will replace any mappings for keys currently in
     * the map if they occur in both this map and the provided map.
     *
     * @param m the map to be copied
     */
    public void putAll(Map<? extends K, ? extends V> m) {
	for (K k : m.keySet())
	    put(k, m.get(k));
    }

    /**
     * Adds a new entry at the specified index and determines if a
     * {@link #split()} operation is necessary.
     *
     * @param hash the hash code the of the key
     * @param key the key to be stored
     * @param value the value to be mapped to the key
     * @param the index in the table at which the mapping should be
     *        stored.
     */
    private void addEntry(int hash, K key, V value, int index) {
	PrefixEntry<K,V> prev = table[index];
	table[index] = new PrefixEntry<K,V>(hash, key, value, prev);

	// ensure that the prefix has enough precision to support
	// another split operation	    
	if ((size++) >= splitThreshold && depth < MAX_DEPTH)
	    split();
    }
    
    /**
     * Copies the values of the specified entry, excluding the {@code
     * PrefixEntry#next} reference, and adds to the the current leaf,
     * but does not perform the size check for splitting.  This should
     * only be called from {@link #split()} or {@link #merge()} when
     * adding children entries.
     *
     * @param copy the entry whose fields should be copied and added
     *        as a new entry to this leaf.
     * @param index the index where the new entry should be put
     */
    private void addEntry(PrefixEntry copy, int index) {
 	PrefixEntry<K,V> prev = table[index];
	table[index] = new PrefixEntry<K,V>(copy, prev); 
 	size++;
    }
    
    /**
     * Returns whether this map has no mappings.  This implemenation
     * runs in {@code O(1)} time.
     *
     * @return {@code true} if this map contains no mappings
     */
    public boolean isEmpty() {
	return leftChild != null || size == 0;
    }
     
     /**
     * Returns the size of the tree.  Note that this implementation
     * runs in {@code O(n + n*log(n))} time, where {@code n} is the
     * number of nodes in the tree (<i>not</i> the number of elements).
     * Developers should
     *
     * @return the size of the tree
     */
    public int size() {

 	if (leftChild == null) // leaf node, short-circuit case
 	    return size;

 	int totalSize = 0;
 	PrefixHashMap cur = leftMost();
  	totalSize += cur.size;
  	while(cur.rightLeaf != null) {
	    int s = (cur = cur.rightLeaf.get(PrefixHashMap.class)).size;
	    //totalSize += (cur = cur.rightLeaf.get(PrefixHashMap.class)).size;
	    totalSize += s;    
	}
	
  	return totalSize;
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    public V remove(Object key) {
	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	PrefixHashMap<K,V> leaf = lookup(hash);
	
	int i = indexFor(hash, leaf.table.length);
	PrefixEntry<K,V> e = leaf.table[i]; 
	PrefixEntry<K,V> prev = e;
	while (e != null) {
	    PrefixEntry<K,V> next = e.next;
	    Object k;
	    if (e.hash == hash && 
		((k = e.getKey()) == key || (k != null && k.equals(key)))) {
		
		// remove the value and reorder the chained keys
		if (e == prev) // if this was the first element
		    leaf.table[i] = next;
		else 
		    prev.next = next;

		// mark that this table's state has changed
		AppContext.getDataManager().markForUpdate(leaf);
		
		V v = e.getValue();
		
		// if this data structure is responsible for the
		// persistence lifetime of the key or value,
		// remove them from the datastore
		e.unmanage();
		
		// lastly, if the leaf size is less than the size
		// threshold, attempt a merge
		if ((leaf.size--) < mergeThreshold && depth > minDepth && 
		    leaf.parent != null) {

		    PrefixHashMap parent_ = leaf.parent.get(PrefixHashMap.class);
		    parent_.merge();
		}
		
		return v;
		
	    }		
	    prev = e;
	    e = e.next;
	}
	return null;
    }

    /**
     * Returns the left-most leaf table from this node in the prefix
     * tree.
     *
     * @return the left-most child under this node
     */
    private PrefixHashMap<K,V> leftMost() {
	return (leftChild == null)
	    ? this : (leftChild.get(PrefixHashMap.class)).leftMost();
    }
	       
	
    /**
     * Returns the bucket index for this hash value given the provided
     * number of buckets.
     *
     * @param h the hash value
     * @param length the number of possible indices
     * @return the index for the given hash 
     */
    static int indexFor(int h, int length) {
	return h & (length-1);
    }

    // for debugging, remove later
    public String treeString() {
	if (leftChild == null) {
	    String s = "(";
	    for (PrefixEntry e : table) {
		if (e != null) {
		    do {
			s += (e + ((e.next == null) ? "" : ", "));
			e = e.next;
		    } while (e != null);
		    s += ", ";
		}
	    }
	    return s + ")";
	}
	else {
	    PrefixHashMap l = leftChild.get(PrefixHashMap.class);
	    PrefixHashMap r = rightChild.get(PrefixHashMap.class);
	    return "(" + l.treeString() + ", " + r.treeString() + ")";
	}
    }

    public String treeDiag() {
	return "ROOT: " + 
	    ((leftChild == null) 
	     ? "conents: " + treeString()
	     : "(id: " 
	     + AppContext.getDataManager().createReference(this).getId() + ")\n"
	     + leftChild.get(PrefixHashMap.class).treeDiag(1) + "\n"
	     + rightChild.get(PrefixHashMap.class).treeDiag(1));	    
    }

    private String treeDiag(int depth) {
	String s; int i = 0;
	for (s = "  "; ++i < depth; s += "  ")
	    ;
	s += (leftChild == null)
	    ? "LEAF (id: " 
	    + AppContext.getDataManager().createReference(this).getId() + ")" 
	    + " contents: "+ treeString()
	    : "INTR (id: "
	    + AppContext.getDataManager().createReference(this).getId() + ")\n"
	    + leftChild.get(PrefixHashMap.class).treeDiag(depth+1)
	    + rightChild.get(PrefixHashMap.class).treeDiag(depth+1);
	return s + "\n";
    }

    public String treeLeaves() {	
	String s = "";
	PrefixHashMap cur = leftMost(); 
	for (ManagedReference r = AppContext.getDataManager().createReference(cur);
	     r != null; r = (cur = r.get(PrefixHashMap.class)).rightLeaf) 
	    s += r + " " + r.get(PrefixHashMap.class).treeString() + 
		((r.get(PrefixHashMap.class).rightLeaf != null) ? " -> " : "");
	return s;
    }

    /**
     * An implementation of {@code Map.Entry} that incorporates
     * information about the prefix at which it is stored, as well as
     * whether the {@link PrefixHashMap} is responsible for the
     * persistent lifetime of the value.
     *
     * <p>
     *
     * If an object that does not implement {@link ManagedObject} is
     * stored in the map, then it is wrapped using the {@link
     * ManagedSerializable} utility class so that the entry may have a
     * {@code ManagedReference} to the value, rather than a Java
     * reference.  This causes accesses to the entries to only
     * deserialize the keys.
     *
     * @see ManagedSerializable
     */	
    private static class PrefixEntry<K,V> implements Map.Entry<K,V>, Serializable {

	private static final long serialVersionUID = 1;
	    
	/**
	 * The a reference to key for this entry. The class type of
	 * this reference will depend on whether the map is managing
	 * the key
	 */	
	private final ManagedReference keyRef;

	/**
	 * A reference to the value.  The class type of this reference
	 * will depend on whether this map is managing the value
	 */ 
	private ManagedReference valueRef;

	/**
	 * The next chained entry in this entry's bucket
	 */
	PrefixEntry<K,V> next;

	/**
	 * The hash value for this entry
	 */
	final int hash;

	/**
	 * Whether the key stored in this entry is actually stored
	 * as a {@link ManagedSerializable}
	 */
	boolean isKeyWrapped;

	/**
	 * Whether the value stored in this entry is actually stored
	 * as a {@link ManagedSerializable}
	 */
	boolean isValueWrapped;

	/**
	 * Constructs this {@code PrefixEntry}
	 *
	 * @param h the hash code for the key
	 * @param k the key
	 * @param v the value
	 * @param next the next {@link PrefixEntry} in this bucked
	 */
	PrefixEntry(int h, K k, V v, PrefixEntry<K,V> next) { 

	    DataManager dm = AppContext.getDataManager();

	    // For the key and value, if each is already a
	    // ManagedObject, then we obtain a ManagedReference to the
	    // object itself, otherwise, we need to wrap it in a
	    // ManagedSerializable and get a ManagedReference to that
	    keyRef = (isKeyWrapped = !(k instanceof ManagedObject))
		? dm.createReference(new ManagedSerializable<K>(k))
		: dm.createReference((ManagedObject)k);


	    valueRef = (isValueWrapped = !(v instanceof ManagedObject))
		? dm.createReference(new ManagedSerializable<V>(v))
		: dm.createReference((ManagedObject)v);

	    this.next = next;
	    this.hash = h;
	}

	PrefixEntry(PrefixEntry<K,V> clone, PrefixEntry<K,V> next) { 
	    this.hash = clone.hash;
	    this.keyRef = clone.keyRef;
	    this.valueRef = clone.valueRef;
	    this.isValueWrapped = clone.isValueWrapped;
	    this.isKeyWrapped = clone.isKeyWrapped;
	    this.next = next;
	}
  
	/**
	 * {@inheritDoc}
	 */
	public final K getKey() {
	    return (isKeyWrapped)
		? ((ManagedSerializable<K>)
		   (keyRef.get(ManagedSerializable.class))).get()
		: (K)(keyRef.get(Object.class));
	}
	    
	/**
	 * Returns the value stored by this entry.  If the mapping has
	 * been removed from the backing map before this call is made,
	 * an {@code ObjectNotFoundException} will be thrown.
	 *
	 * @return the value stored in this entry
	 * @throws ObjectNotFoundException if the element in the
	 *         backing map was removed prior to this call
	 */
	// NOTE: this method will automatically unwrap all value that
	//       the map is responsible for managing
	public final V getValue() {
	    return (isValueWrapped) 
		? ((ManagedSerializable<V>)
		   (valueRef.get(ManagedSerializable.class))).get()
		: (V)(valueRef.get(Object.class));
	}

	/**
	 * Replaces the previous value of this entry with the provided
	 * value.  If {@code newValue} is not of type {@code
	 * ManagedObject}, the value is wrapped by a {@code
	 * ManagerWrapper} and stored in the data store.
	 *
	 * @param newValue the value to be stored
	 * @return the previous value of this entry
	 */
	public final V setValue(V newValue) {
	    V oldValue;
	    ManagedSerializable<V> wrapper = null;

	    // unpack the value from the wrapper prior to
	    // returning it
	    oldValue = (isValueWrapped) 
		? (wrapper = valueRef.get(ManagedSerializable.class)).get()
		: (V)(valueRef.get(Object.class));
	 

	    if (newValue instanceof ManagedObject) {
		// if v is already a ManagedObject, then do not put it
		// in the datastore, and instead get a reference to it
		valueRef = AppContext.getDataManager().
		    createReference((ManagedObject)newValue);
		isValueWrapped = false;

		// if the previous value was wrapper, remove the
		// wrapper from the datastore
		if (wrapper != null)
		    AppContext.getDataManager().removeObject(wrapper);
	    }
	    else {
		// re-use the old wrapper if we have one to avoid
		// making another create call
		if (wrapper != null)
		    wrapper.set(newValue);
		// otherwise, we need to wrap it in a new
		// ManagedSerializable
		else {		    
		    valueRef = AppContext.getDataManager().
			createReference(new ManagedSerializable<V>(newValue));
		    isValueWrapped = true; // already true in the if-case
		}
	    }
	    return oldValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean equals(Object o) {
	    if (!(o instanceof Map.Entry))
		return false;
	    Map.Entry e = (Map.Entry)o;
	    Object k1 = getKey();
	    Object k2 = e.getKey();
	    if (k1 == k2 || (k1 != null && k1.equals(k2))) {
		Object v1 = getValue();
		Object v2 = e.getValue();
		if (v1 == v2 || (v1 != null && v1.equals(v2)))
		    return true;
	    }
	    return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final int hashCode() {
	    return (keyRef==null   ? 0 : keyRef.hashCode()) ^
		(valueRef==null ? 0 : valueRef.hashCode());
	}
	
	/**
	 * Returns the string form of this entry as {@code
	 * entry}={@code value}.
	 */
	public String toString() {
	    return getKey() + "=" + getValue();
	}

	/**
	 * Removes any {@code Serializable} managed by this entry from
	 * the datastore.  This should only be called from {@link
	 * PrefixHashMap#clear()} and {@link
	 * PrefixHashMap#remove(Object)} under the condition that this
	 * entry's map-managed object will never be reference again by
	 * the map.
	 */
	final void unmanage() {
	    if (isKeyWrapped) {
		// unpack the key from the wrapper 
		ManagedSerializable<V> wrapper = 
		    keyRef.get(ManagedSerializable.class);
		AppContext.getDataManager().removeObject(wrapper);
	    }
	    if (isValueWrapped) {
		// unpack the value from the wrapper 
		ManagedSerializable<V> wrapper = 
		    valueRef.get(ManagedSerializable.class);
		AppContext.getDataManager().removeObject(wrapper);
	    }
	}
    }


    /**
     * An abstract base class for implementing iteration over entries
     * while subclasses define which data from the entry should be
     * return by {@code next()}.
     */
    private abstract class PrefixTreeIterator<E> 
	implements Iterator<E> {
	
	/**
	 * The next element to return
	 */
	PrefixEntry<K,V> next;

	/**
	 * The table index for the next element to return
	 */
	int index; 

	/**
	 * The current table in which the {@code next} reference is
	 * contained.
	 */
	PrefixHashMap<K,V> curTable;

	/**
	 * Constructs the prefix table iterator.
	 *
	 * @param start the left-most leaf in the prefix tree
	 */
	PrefixTreeIterator(PrefixHashMap<K,V> start) {

	    curTable = start;
	    index = 0;
	    next = null;

	    // load in the first table that has an element
	    while (curTable.size == 0 && curTable.rightLeaf != null) 
		curTable = curTable.rightLeaf.get(PrefixHashMap.class);
		
	    // advance to find the first Entry
	    for (index = 0; index < curTable.table.length &&
		     (next = curTable.table[index]) == null; ++index) 
		;
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean hasNext() {
	    return next != null;
	}

	/**
	 * Returns the next {@code PrefixEntry} found in this map.
	 *
	 * @throws NoSuchElementException if no next entry exists
	 */
	final PrefixEntry<K,V> nextEntry() {
	    PrefixEntry<K,V> e = next;
	    next = next.next;

	    if (e == null) 
		throw new NoSuchElementException();

	    if (next == null) {
		// start at the next index into the current table and
		// search for another element;
		for(index++; index < curTable.table.length && 
			(next = curTable.table[index]) == null; index++) 
		    ;		

		// if still null, we must be at the end of the table,
		// so begin loading in the next table, until another
		// element is found
		if (next == null) {
		    
  		    while (curTable.rightLeaf != null) {
  			curTable = curTable.rightLeaf.get(PrefixHashMap.class);
			
  			if (curTable.size == 0) 
 			    continue;
	
			// iterate to the next element
			for (index = 0; index < curTable.table.length &&
				 (next = curTable.table[index]) == null; ++index) 
			    ;		   		    		    
 			break;
 		    }
		}
	    }

	    return e;
	}
	
	/**
	 * This operation is not supported.
	 *
	 * @throws UnsupportedOperationException if called
	 */
	public void remove() {
	    // REMINDER: we probably could support this.
	    throw new UnsupportedOperationException();
	}
		
    }

    /**
     * An iterator over the entry set
     */
    private final class EntryIterator
	extends PrefixTreeIterator<Map.Entry<K,V>> {
	
	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	EntryIterator(PrefixHashMap<K,V> leftMostLeaf) {
	    super(leftMostLeaf);
	}

	/**
	 * {@inheritDoc}
	 */
	public Map.Entry<K,V> next() {
	    return nextEntry();
	}
    }

    /**
     * An iterator over the keys in the map
     */
    private final class KeyIterator extends PrefixTreeIterator<K> {

	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	KeyIterator(PrefixHashMap<K,V> leftMostLeaf) {
	    super(leftMostLeaf);
	}

	/**
	 * {@inheritDoc}
	 */
	public K next() {
	    return nextEntry().getKey();
	}
    }


    /**
     * An iterator over the values in the tree
     */
    private final class ValueIterator extends PrefixTreeIterator<V> {

	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	ValueIterator(PrefixHashMap<K,V> leftMostLeaf) {
	    super(leftMostLeaf);
	}

	/**
	 * {@inheritDoc}
	 */
	public V next() {
	    return nextEntry().getValue();
	}
    }

    /**
     * Returns a {@code Set} of all the mappings contained in this
     * map.  The returned {@code Set} is back by the map, so changes
     * to the map will be reflected by this view.  Note that the time
     * complexity of the operations on this set will be the same as
     * those on the map itself.  
     *
     * @return the set of all mappings contained in this map
     */
    public Set<Entry<K,V>> entrySet() {
	return new EntrySet(this);
    }

    /**
     * An internal-view {@code Set} implementation for viewing all the
     * entries in this map.  
     */
    private final class EntrySet extends AbstractSet<Entry<K,V>> {

	/**
	 * the root node of the prefix tree
	 */
	private final PrefixHashMap<K,V> root;

	EntrySet(PrefixHashMap<K,V> root) {
	    this.root = root;
	}
	    
	public Iterator<Entry<K,V>> iterator() {
	    return new EntryIterator(root.leftMost());
	}

	public boolean isEmpty() {
	    return root.isEmpty();
	}

	public int size() {
	    return root.size();
	}

	public boolean contains(Object o) {
	    if (!(o instanceof Map.Entry)) 
		return false;
	    Map.Entry<K,V> e = (Map.Entry<K,V>)o;
	    PrefixEntry<K,V> pe = root.getEntry(e.getKey());
	    return pe != null && pe.equals(e);
	}

	public void clear() {
	    root.clear();
	}
	
    }

    /**
     * Returns a {@code Set} of all the keys contained in this
     * map.  The returned {@code Set} is back by the map, so changes
     * to the map will be reflected by this view.  Note that the time
     * complexity of the operations on this set will be the same as
     * those on the map itself.  
     *
     * @return the set of all keys contained in this map
     */
    public Set<K> keySet() {
	return new KeySet(this);
    }

    /** 
     * An internal collections view class for viewing the keys in the
     * map
     */
    private final class KeySet extends AbstractSet<K> {
	  
	/**
	 * the root node of the prefix tree
	 */
	private final PrefixHashMap<K,V> root;

	KeySet(PrefixHashMap<K,V> root) {
	    this.root = root;
	}
	    
	public Iterator<K> iterator() {
	    return new KeyIterator(root.leftMost());
	}

	public boolean isEmpty() {
	    return root.isEmpty();
	}

	public int size() {
	    return root.size();
	}

	public boolean contains(Object o) {
	    return root.containsKey(o);
	}

	public void clear() {
	    root.clear();
	}
	
    }
	
    /**
     * Returns a {@code Collection} of all the keys contained in this
     * map.  The returned {@code Collection} is back by the map, so
     * changes to the map will be reflected by this view.  Note that
     * the time complexity of the operations on this set will be the
     * same as those on the map itself.
     *
     * @return the collection of all values contained in this map
     */

    public Collection<V> values() {
	return new Values(this);
    }
   
    /**
     * An internal collections-view of all the values contained in
     * this map
     */
    private final class Values extends AbstractCollection<V> {

	/**
	 * the root node of the prefix tree
	 */
	private final PrefixHashMap<K,V> root;

	public Values(PrefixHashMap<K,V> root) {
	    this.root = root;
	}

	public Iterator<V> iterator() {
	    return new ValueIterator(root.leftMost());
	}

	public boolean isEmpty() {
	    return root.isEmpty();
	}

	public int size() {
	    return root.size();
	}

	public boolean contains(Object o) {
	    return containsValue(o);
	}

	public void clear() {
	    root.clear();
	}
    }

    /**
     * Saves the state of this {@code PrefixHashMap} instance to the
     * provided stream.
     *
     * @serialData a {@code boolean} of whether this instance was a
     *             leaf node.  If this instance is a leaf node, this
     *             boolean is followed by a series {@code PrefixEntry}
     *             instances, some of which may be chained.  The
     *             deserialization should count each chained entry
     *             towards the total size of the leaf.
     */
    private void writeObject(java.io.ObjectOutputStream s) 
	throws java.io.IOException {
	// parent, leafs, children, stable, size, splitThresh, mergeThresh,
	// leafCapacity, splitFactor, mergeFactor, minDepth, depth
	// write out all the non-transient state
	s.defaultWriteObject();

	// write out whether this node was a leaf
	s.writeBoolean(table != null);
	
	// if this was a leaf node, write out all the elments in it
	if (table != null) {
	    // iterate over all the table, stopping when all the
	    // entries have been seen
	    PrefixEntry e;
	    for (int i = 0, j = 0; j < size; ++i) {
		if ((e = table[i]) != null) {
		    j++;
		    s.writeObject(table[i]);
		    for (; (e = e.next) != null; ++j)
			;
		}
	    }
	}
    }

    /**
     * Reconstructs the {@code PrefixHashMap} from the provided
     * stream.
     */
    private void readObject(java.io.ObjectInputStream s) 
	throws java.io.IOException, ClassNotFoundException {
	
	// read in all the non-transient state
	s.defaultReadObject();

	boolean isLeaf = s.readBoolean();

	// initialize the table if it is a leaf node, otherwise, mark
	// it as null
	table = (isLeaf) ? new PrefixEntry[leafCapacity] : null;
		
	// read in entries and assign them back their positions in the
	// table, noting that some positions may have chained entries
	for (int i = 0; i < size; i++) {
	    PrefixEntry<K,V> e = (PrefixEntry<K,V>) s.readObject();
	    table[indexFor(e.hash, leafCapacity)] = e;
	    for (; (e = e.next) != null; ++i)
		; // count chained entries
	}
    }
}
