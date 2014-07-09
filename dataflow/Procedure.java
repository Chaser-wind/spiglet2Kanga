package dataflow;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Procedure {
	private Map<String, Set<String>> adjacencyMap;
	private Map<String, Integer> frequencyMap;
	private Map<String,String> vertex2registerMap;
	private Map<String,Integer> vertex2stackMap;
	private List<BasicBlock> blocks;
	private int arguments,maxArguments;
	private String name;

	private static final Logger logger;

	/* static initialization */
	static {
		logger = Logger.getLogger(Procedure.class.getName());
		logger.setLevel(Level.OFF);
	}

	public Procedure(String name) {
		this.adjacencyMap = new ConcurrentHashMap<String, Set<String>>();
		this.frequencyMap = new HashMap<String, Integer>();
		this.vertex2registerMap = new HashMap<String, String>();
		this.vertex2stackMap = new HashMap<String, Integer>();
		this.blocks = new ArrayList<BasicBlock>();
		this.arguments = this.maxArguments = 0;
		this.name = name;
	}

	/** populate in & out sets */
	public void analyzeLiveness(){
		for(boolean changed = true; changed; ){
			changed = false;
			for(BasicBlock block : blocks)
				changed = block.populateStatementSets() || changed;
			for(BasicBlock block : blocks)
				changed = block.populateBlockSets() || changed;
		}
	}

	/** constructs adjacency matrix to represent interference graph (global scoped) */
	public void connectComponents() {
		for(BasicBlock block : blocks)
			for(Statement statement : block.statements)
				for(String vertex : statement.in) {
					if(!adjacencyMap.containsKey(vertex))
						adjacencyMap.put(vertex, new HashSet<String>());
					adjacencyMap.get(vertex).addAll(statement.in);
					adjacencyMap.get(vertex).remove(vertex);
				}
	}

	/** calculates static usage frequency for each temp */
	public void computeSpillCost() {
		for(BasicBlock block : blocks)
			for(Statement statement : block.statements)
				for(String vertex : statement.use) {
					if(!frequencyMap.containsKey(vertex))
						frequencyMap.put(vertex, 0);
					int frequency = frequencyMap.get(vertex);
					frequencyMap.put(vertex, frequency+1);
				}
	}

	/**
	 * Chaitin's algorithm:
	 *
	 * While ∃ vertices with < k neighbors in G:
	 * - Pick any vertex n such that n°< k and put it on the stack.
	 * - Remove that vertex and all edges incident to it from G.
	 *
	 * If G is non-empty (all vertices have k or more neighbors) then:
	 * - Pick a vertex n that minimizes (spill cost / n's current degree) and spill the
	 *   live range associated with n.
	 * - Remove vertex n from G , along with all edges incident to it.
	 * - If this causes some vertex in G to have fewer than k neighbors, try again to color the graph,
	 *   otherwise try to spill another vertex.
	 *
	 * (*) the code bellow assumes that registers.size() > 0
	 */
	public void colorComponents() {
		Map<String, Set<String>> adjacencyMap =
				new ConcurrentHashMap<String, Set<String>>(this.adjacencyMap);
		Stack<String> stack = new Stack<String>();

		while(true) {
			for(boolean progressed = true; progressed; ) {
				progressed = false;
				for(String vertex : adjacencyMap.keySet())
					/* if vertex can be colored */
					if(adjacencyMap.get(vertex).size() < ControlFlowGraph.registers.size()) {
						/* remove vertex neighbours from adjacency map */
						for(String adjacent : adjacencyMap.get(vertex))
							adjacencyMap.get(adjacent).remove(vertex);
						/* remove the vertex itself */
						adjacencyMap.remove(vertex);
						stack.push(vertex);
						progressed = true;
					}
			}
			if(adjacencyMap.isEmpty())
				break;
			for(boolean colorable = false; !colorable; ) {
				/* pick spill candidate according to Chaitin's heuristic */
				String spillCandidate = null;
				double currentCost, minimumCost = Double.MAX_VALUE;
				for(String vertex : adjacencyMap.keySet()) {
					double spillCost = frequencyMap.get(vertex);
					double currentDegree = adjacencyMap.get(vertex).size();
					/* as an alternative, spillCost / (currentDegree * currentDegree) can be used */
					if((currentCost = spillCost / currentDegree) < minimumCost) {
						minimumCost = currentCost;
						spillCandidate = vertex;
					}
				}
				assert spillCandidate != null : "can not pick spill candidate";
				/* spill candidate vertex to stack */
				int stackOffset = maxArguments + vertex2stackMap.size();	//?
				vertex2stackMap.put(spillCandidate, stackOffset);
				/* logging */
				System.out.println("Procedure: " + name.replaceFirst("_","::") +
						" Spilling: " + spillCandidate +
						" Stack offset: " + stackOffset +
						" Cost: " + new DecimalFormat("#.###").format(minimumCost));
				/* remove candidate vertex from adjacency map */
				for(String vertex : adjacencyMap.keySet())
					adjacencyMap.get(vertex).remove(spillCandidate);
				adjacencyMap.remove(spillCandidate);
				/* lookup for a vertex with degree lower than k */
				for(Iterator<Set<String>> iterator = adjacencyMap.values().iterator();
					iterator.hasNext() && !colorable; ) {
					Set<String> adjacentSet = iterator.next();
					if(adjacentSet.size() < ControlFlowGraph.registers.size())
						colorable = true;
				}
			}
		}
		/* assign registers to temps */
		while(!stack.isEmpty()) {
			List<String> available = new ArrayList<String>(ControlFlowGraph.registers);
			String vertex = stack.pop();
			for(String adjacent : this.adjacencyMap.get(vertex))
				available.remove(vertex2registerMap.get(adjacent));
			String register = available.get(0);
			vertex2registerMap.put(vertex, register);
			/* logging */
//			System.out.println("Procedure: " + name.replaceFirst("_","::") +
//					" Assigning register: " + register + " to " + vertex);
		}
	}

	/* dummy methods */
	public String getName(){
		return name;
	}

	public void setArguments(int arguments){
		this.arguments = arguments;
	}

	public int getArguments(){
		return arguments;
	}

	public void setMaxArguments(int maxArguments){
		this.maxArguments = maxArguments;
	}

	public int getMaxArguments(){
		return maxArguments;
	}

	public int getSpillArguments(){
		return vertex2stackMap.size();
	}

	public void addBlock(BasicBlock block){
		blocks.add(block);
	}

	@Override
	public String toString() {
		StringBuilder message = new StringBuilder();
		message.append(name)
			   .append(" [ arguments: ")
			   .append(arguments)
			   .append(" spilled: ")
			   .append(vertex2stackMap.size())
			   .append(" max arguments: ")
			   .append(maxArguments)
			   .append(" basic blocks: ")
			   .append(blocks.size())
			   .append(" ]\n");
		for(BasicBlock block : blocks)
			message.append(block.toString());
		for(String vertex : vertex2stackMap.keySet())
			message.append("Spilled argument: ")
				   .append(vertex)
				   .append(" lies on stack offset: ")
				   .append(vertex2stackMap.get(vertex));
		return message.toString();
	}
}
