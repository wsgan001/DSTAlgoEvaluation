package graphTheory.algorithms.steinerProblems.steinerArborescenceApproximation;

import graphTheory.graph.Arc;
import graphTheory.utils.Couple;
import graphTheory.utils.CustomFibonacciHeap;
import graphTheory.utils.CustomFibonacciHeapNode;
import graphTheory.utils.DoubleBoolean;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * 
 * Implementation of the G_F algorithm.
 * <p>
 * This algorithm returns a feasible solution for the Directed Steiner Tree problem.
 * It is a greedy algorihm which uses a sub-algorihthm named FLAC. 
 * <p>
 * FLAC is designed to returns a feasible solution for the minimum Density Directed Steiner Tree problem:
 * it searches for a tree rooted in the root of the instance which spans a part of the terminal. It try to
 * minimize the total cost of the tree divided by the number of terminals.
 * <p>
 * Gf uses FLAC to span some terminals, delete those terminals, and repeat FLAC to span other terminals.
 * It continues until all the terminals are reached.
 * <p>
 * To span the terminals, FLAC grows a flow from the terminals: we call them sources. That flow is send through the entering arcs,
 * with a flow rate equals to 1 L/s, considering the volume of an arc is its weight. Then, when an arc is
 * full (we say saturated), the source feeds its entering arcs. If two sources reach the same arc with a path
 * of saturated arcs, the flow rate is 2 L/s. And if more sources reaches the arc, the flow rate increases again:
 * the flow rate inside an arc is equals to the number of sources it can reach with paths of saturated arcs
 * <p>
 * FLAC continue until the root is reached, and we return the set of saturated arcs linked to the root. As we want to return a tree, each time an arc is saturated, if a node is connected 
 * to the same source with two paths of saturated arcs, we delete that last saturated arc.
 * <p>
 * @author Watel Dimitri
 *
 */
public class GFLACAlgorithm extends SteinerArborescenceApproximationAlgorithm{

	// --------------------   Directed Steiner Tree Part --------------------//

	/**
	 * Copy of the costs of the instance. It let the algorithm modify the costs
	 * withouth modifying the instance it self.
	 */
	private HashMap<Arc,Integer> costs;
	
	/**
	 * Copy of the required vertices of the instance. It let the algorithm
	 * modify this set without modifiying the instance.
	 */
	private HashSet<Integer> requiredVertices;
	
	/**
	 * For each node, this map sorts its input arcs by cost.
	 */
	private HashMap<Integer, TreeSet<Arc>> sortedInputArcs;
	
	/**
	 * A comparator used to sort arcs by costs.
	 */
	private Comparator<Arc> comp;

	@Override
	protected void computeWithoutTime() {
		
		// Copy the required vertices
		requiredVertices = new HashSet<Integer>(instance.getRequiredVertices());
		// Remove the root, if it is a terminal, otherwise, this algorithm could start
		// a infinite loop
		requiredVertices.remove(instance.getRoot());
		
		// This set will merge the trees returned by FLAC
		HashSet<Arc> currentSol = new HashSet<Arc>();

		this.costs = instance.getCosts();
		comp = getArcsComparator();

		sortedInputArcs = new HashMap<Integer, TreeSet<Arc>>();
		this.sortInputArcs();

		// Initialize parameters
		this.init();

		// Until all the terminals are reached
		while(requiredVertices.size() > 0){
			Couple<HashSet<Arc>,HashSet<Integer>> result = applyFLAC(); // Search a low Density Directed Steiner Tree with the FLAC algorithm

			if(result == null){	
				this.arborescence = null;
				this.cost = null;
				return;
			}

			// This is the tree returned by FLAC
			HashSet<Arc> tbest = result.first;
			
			// Those are the terminals reached by the previous tree
			HashSet<Integer> reached = result.second;

			/* 
			* For each arc in the tree set the cost of that arc to 0
			* As a consequence, the next tree returned by FLAC is preferentially
			* merged by the current partial solution.
			* 
			* In addition, this loop add the arc to the current partial solution.
			*/
			for(Arc a : tbest){
				currentSol.add(a); // add the arc to the current solution
				TreeSet<Arc> aNeighbours = sortedInputArcs.get(a.getOutput());
				aNeighbours.remove(a);
				costs.put(a, 0);
				aNeighbours.add(a); // Edit the cost of a and place it where it belongs now in the treeset 
			}

			requiredVertices.removeAll(reached); // Remove the reached terminals from the required vertices of the instance
		}

		// Set the output of this algorithm : the returned tree and its cost
		
		arborescence = currentSol;
		int c = 0;
		if(arborescence != null)
			for(Arc a : arborescence)
				c += instance.getCost(a);

		cost = c;
	}

	private Comparator<Arc> getArcsComparator() {
		return new Comparator<Arc>() {

			@Override
			public int compare(Arc o1, Arc o2) {
				if(o1 == null)
					return 1;
				else if(o2 == null)
					return -1;
				else if (o1.equals(o2))
					return 0;
				else{
					Integer i1 = costs.get(o1);
					Integer i2 = costs.get(o2);
					int comp = i1.compareTo(i2);
					if(comp != 0)
						return comp;
					comp = o1.getInput().compareTo(o2.getInput());
					if(comp != 0)
						return comp;
					return o1.getOutput().compareTo(o2.getOutput());
				}
			}
		};
	}

	/**
	 * For each node, sort its input arcs by cost.
	 */
	private void sortInputArcs() {
		Iterator<Integer> it = instance.getGraph().getVerticesIterator();
		while(it.hasNext())
			sortInputArcs(it.next());
	} 

	/**
	 * Sort the input arcs of v by cost
	 * @param v
	 */
	private void sortInputArcs(Integer v){
		Iterator<Arc> it = instance.getGraph().getInputArcsIterator(v);

		TreeSet<Arc> ts = new TreeSet<Arc>(comp);
		while(it.hasNext()){
			ts.add(it.next());
		}

		sortedInputArcs.put(v, ts);
	}


	// ---------- density Directed Steiner Tree part ---------

	/**
	 * @return a tree rooted in the root of the instance spanning a part of the terminals, and the set of those terminals.
	 */
	private Couple<HashSet<Arc>,HashSet<Integer>> applyFLAC(){

		// Reinitialize the parameters to let FLAC restart normally
		reinit();
		while(true){

			// Check which arc will be the next saturated one
			Arc a = nextSaturatedArc();

			Integer u = a.getInput();
			Integer v = a.getOutput();

			// If the root is reached by the terminals, we can return a tree
			if(u.equals(instance.getRoot())){
				saturated.add(a);
				return buildTree();
			}

			// We now check if a node is linked to the root with two paths of saturated arcs: it is called a conflict
			boolean conflict = findConflict(u,v);
			
			// Whatever the case, we have to check which arc of v will be its next saturated entering arc, and when
			// it will be saturated
			updateNextSaturatedArc(v);

			// If there is a conflict, we just ignore the arc saturation, as if it was never added to the arc
			if(!conflict)
				// If there is no conflict, we have to update the flow rate of other arcs as a new arc is
				// saturated.
				saturateArcAndUpdate(a);  
		}
	}

	/**
	 * Set of saturated arcs (ie full of fluid)
	 */
	private HashSet<Arc> saturated;

	/**
	 * For each node, this map saves the sources this node is linked to with a path
	 * of saturated arcs
	 */
	private HashMap<Integer,HashSet<Integer>> sources;

	/**
	 * Set of nodes for which one entering arc is saturating. The key of
	 * each node in the Fibonacci Heap is the physical time when its next saturating arc will
	 * be saturated.
	 */
	private CustomFibonacciHeap<Integer,DoubleBoolean> sortedSaturating;

	/**
	 * Set of nodes associated with the next saturated entering arc of this node.
	 */
	private HashMap<Integer, Iterator<Arc>> nextSaturatedEnteringArcIterators;

	/**
	 * Set of nodes associated with the next saturated entering arc of this node.
	 */
	private HashMap<Integer, Arc> nextSaturatedEnteringArcs;

	/**
	 * The actual physical time since the beginning of saturation
	 */
	private double time;

	/**
	 * This maps each node to the node of the fibonacci heap containing it.
	 */
	private HashMap<Integer,CustomFibonacciHeapNode<Integer,DoubleBoolean>> n2fbn;

	/**
	 * Initialize the maps, sets and lists used by the algorithm FLAC 
	 */
	private void init(){
		saturated = new HashSet<Arc>();
		sources = new HashMap<Integer, HashSet<Integer>>();
		sortedSaturating = new CustomFibonacciHeap<Integer, DoubleBoolean>();
		nextSaturatedEnteringArcIterators = new HashMap<Integer, Iterator<Arc>>();
		nextSaturatedEnteringArcs = new HashMap<Integer, Arc>();
		n2fbn = new HashMap<Integer, CustomFibonacciHeapNode<Integer,DoubleBoolean>>();
	}

	/**
	 * Clear the maps, sets and lists used by the algorithm FLAC.
	 * Reinitialize the parameters used by FLAC. 
	 */
	private void reinit() {
		saturated.clear();
		sources.clear();
		sortedSaturating.clear();
		nextSaturatedEnteringArcIterators.clear();
		nextSaturatedEnteringArcs.clear();
		n2fbn.clear();

		// The saturation begin at 0 seconds
		time = 0D;

		// Init parameters for each terminal
		Iterator<Integer> it = instance.getGraph().getVerticesIterator();
		while(it.hasNext()){
			Integer v = it.next();
			if(requiredVertices.contains(v)){
				
				// define the sources feeding that terminal as the terminal itself
				getSources(v).add(v);
				
				// define the next saturated arc entering v, and compute the time
				// in seconds needed to saturate it.
				updateNextSaturatedArc(v);
			}
		}
	}

	/**
	 * @param v
	 * @return the set of sources of v. If it was not initialized, init it.
	 */
	private HashSet<Integer> getSources(Integer v){
		HashSet<Integer> srcs = sources.get(v);
		if(srcs == null){
			srcs = new HashSet<Integer>();
			sources.put(v,srcs);
		}
		return srcs;
	}


	/**
	 * Assuming an entering arc of v is saturated, the next one is the 
	 * next in the list of entering arcs of v sorted by weigths.
	 * <br/>
	 * This method find this arc and compute when it will be saturated
	 * @param v
	 */
	private void updateNextSaturatedArc(Integer  v){
		
		// Iterator over the entering arcs of v sorted by weights
		Iterator<Arc> it = nextSaturatedEnteringArcIterators.get(v);

		if(it == null){
			// Init the iterator, if it was not the case
			it = sortedInputArcs.get(v).iterator();
			nextSaturatedEnteringArcIterators.put(v,it);
		}

		// Last saturated arc entering v
		Arc b = nextSaturatedEnteringArc(v);

		// Next saturated arc entering v
		Arc a = null;
		if(it.hasNext()){
			// If b was not the arc entering v with biggest cost
			// a exists
			a = it.next();
			nextSaturatedEnteringArcs.put(v,a);
		}
		else
		{
			// Else, all arcs entering v are already saturated
			nextSaturatedEnteringArcs.remove(v);
			n2fbn.remove(v);
			return;
		}


		// Saturated time of a
		Double satTime;

		if(b == null)
			// No arc entering v start to saturate
			satTime = getVolume(a)/getVolFlowRate(v);
		else
			// All arc entering v start to saturate, including a
			satTime = (getVolume(a) - getVolume(b))/getVolFlowRate(v);
		
		// Reinsert v in the list with the saturated time of a
		CustomFibonacciHeapNode<Integer,DoubleBoolean> fbn = sortedSaturating.insert(v, 
				new DoubleBoolean(time + satTime, !a.getInput().equals(instance.getRoot())));
		n2fbn.put(v,fbn);

	}

	/**
	 * @param v
	 * @return the next saturated time entering v
	 */
	private Arc nextSaturatedEnteringArc(Integer v) {
		return nextSaturatedEnteringArcs.get(v);
	}

	/**
	 * 
	 * @return the next saturated time entering the first node if the fibonnacci heap
	 */
	private Arc nextSaturatedArc(){
		CustomFibonacciHeapNode<Integer,DoubleBoolean> fbn = sortedSaturating.removeMin();
		time = fbn.getKey().getDoubleValue();
		return nextSaturatedEnteringArc(fbn.getData());
	}

	/**
	 * 
	 * @param u
	 * @param v
	 * @return true if the saturation of arc (u,v) implies a conflict
	 */
	private boolean findConflict(Integer u, Integer v) {

		// Will contain the list of nodes linked to u with a saturated path...
		LinkedList<Integer> toList = new LinkedList<Integer>();
		// ... including u
		toList.add(u);

		// If one of those nodes is already linked to one of the sources linked to v
		// there is a conflict.
		HashSet<Integer> vsrcs = getSources(v);

		while(!toList.isEmpty()){
			Integer w = toList.pollFirst();

			// If the sources reaching w intersect the sources reaching v there is a conflict
			if(nonEmptyIntersection(getSources(w),vsrcs))
				return true;

			Arc saturatingInputArc = nextSaturatedEnteringArc(w);

			// Add all the saturated arcs entering w to the list of arcs we have to check
			for(Arc inputArc : sortedInputArcs.get(w)){
				if(inputArc.equals(saturatingInputArc))
					break;
				if(isSaturated(inputArc))
					toList.add(inputArc.getInput());
			}
		}
		return false;
	}

	/**
	 * 
	 * @param s1
	 * @param s2
	 * @return true if s1 and s2 have a non empty intersection
	 */
	private boolean nonEmptyIntersection(HashSet<Integer> s1, HashSet<Integer> s2){
		HashSet<Integer> sa,sb;

		if(s2.size() > s1.size()){
			sa = s1;
			sb = s2;}
		else{
			sa = s2;
			sb = s1;}

		for(Integer v : sa){
			if(sb.contains(v))
				return true;
		}

		return false;
	}

	
	/**
	 * Add the arc a to the set of saturated arcs, and update the flow rate of all the arcs
	 * affected by this saturation
	 * @param a
	 */
	private void saturateArcAndUpdate(Arc a) {
		Integer u = a.getInput();
		Integer v = a.getOutput();

		// This list will contain the set of node for which the flow rate will change
		// after the saturation of a...
		LinkedList<Integer> toUpdate = new LinkedList<Integer>();
		// ...including u
		toUpdate.add(u);

		// The sources of v we have to add to update the affected nodes
		HashSet<Integer> vsrcs = getSources(v);

		while(!toUpdate.isEmpty()){
			Integer w = toUpdate.pollFirst();

			// The current flow rate inside each entering arc of w, before a is saturated
			double prevVolFlowRate = getVolFlowRate(w);
			getSources(w).addAll(vsrcs); // disjoint union, because there is no conflict

			if(prevVolFlowRate != 0){
				// if w already received flow before a became saturated
				// the time the next entering arc of w is saturated is accelerated like this:
				CustomFibonacciHeapNode<Integer,DoubleBoolean> fbn = n2fbn.get(w);
				// by the following test, we test if there is an entering arc of w which is not fully saturated
				// in the other case we do nothing
				if(fbn != null){
					double prevNextSaturatedEnteringArcTime = fbn.getKey().getDoubleValue();
					double newVolFlowRate = getVolFlowRate(w);

					double newNextSaturatedEnteringArcTime = time + (prevNextSaturatedEnteringArcTime - time) * (prevVolFlowRate/newVolFlowRate);
					sortedSaturating.decreaseKey(fbn, new DoubleBoolean(newNextSaturatedEnteringArcTime,fbn.getKey().getBooleanValue()));
				}
			}
			else
				// if w did not receive any flow from the source, we initialize its saturation like this
				updateNextSaturatedArc(w);
			
			
			Arc saturatingInputArc = nextSaturatedEnteringArc(w);

			// For each node linked to w with a saturated arc, we insert it in the list
			// of nodes we have to update
			for(Arc inputArc : sortedInputArcs.get(w)){
				if(inputArc.equals(saturatingInputArc))
					break;
				if(isSaturated(inputArc))
					toUpdate.add(inputArc.getInput());
			}
		}

		saturated.add(a);
	}


	/**
	 * 
	 * @return the set of saturated arcs linked to the root, and the terminals
	 * this set of arcs reach.
	 */
	private Couple<HashSet<Arc>, HashSet<Integer>> buildTree() {
		Integer root = instance.getRoot();
		LinkedList<Integer> toList = new LinkedList<Integer>();
		toList.add(root);

		HashSet<Arc> tree = new HashSet<Arc>();
		HashSet<Integer> leaves = new HashSet<Integer>();

		while(!toList.isEmpty()){
			Integer v = toList.pollFirst();
			if(requiredVertices.contains(v))
				leaves.add(v);

			Iterator<Arc> it = instance.getGraph().getOutputArcsIterator(v);
			while(it.hasNext()){
				Arc a = it.next();
				if(!isSaturated(a))
					continue;

				tree.add(a);
				toList.add(a.getOutput());
			}
		}
		return new Couple<HashSet<Arc>, HashSet<Integer>>(tree,leaves);
	}

	/**
	 * 
	 * @param a
	 * @return the maximum value of flow an arc a can contain: its cost
	 */
	private double getVolume(Arc a){
		return (double)costs.get(a);
	}

	/**
	 * 
	 * @param v
	 * @return the current flow rate entering v: the sources it can reach with saturated arcs
	 */
	private double getVolFlowRate(Integer v){
		return (double)getSources(v).size();
	}

	/**
	 * 
	 * @param a
	 * @return true if a is full of flow
	 */
	private boolean isSaturated(Arc a){
		return saturated.contains(a);
	}
}