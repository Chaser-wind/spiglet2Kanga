package dataflow;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static dataflow.Statement.State;

public final class Procedure {
	public static final List<String> registers;
	private static final Logger logger;
	/* static initialization */
	static {
		logger = Logger.getLogger(Procedure.class.getName());
		logger.setLevel(Level.OFF);
		registers = new ArrayList<String>() {{
			for(int i = 0; i < 10; ++i) add(String.format("t%d", i));
			for(int i = 0; i < 8; ++i) add(String.format("s%d", i));
		}};
	}

	public Map<String, String> registerMap;			/* temp to assigned register mapping */
	public Map<String, Integer> stackMap;			/* temp to stack offset mapping */
	private int arguments, maxArguments, spillCount;
	private Map<String, Integer> calleeStackOffset;
	private Map<String, Integer> callerStackOffset;
	private Map<String, Set<String>> adjacencyMap;
	private Map<String, Integer> frequencyMap;
	private List<BasicBlock> blocks;
	private Set<String> calleeSaved;				/* contains s-type registers that need to be stored by the procedure called */
	private String name;

	public Procedure(String name) {
		this.arguments = this.maxArguments = this.spillCount = 0;
		this.calleeStackOffset = new HashMap<String, Integer>();
		this.callerStackOffset = new HashMap<String, Integer>();
		this.adjacencyMap = new ConcurrentHashMap<String, Set<String>>();
		this.frequencyMap = new HashMap<String, Integer>();
		this.registerMap = new HashMap<String, String>();
		this.stackMap = new HashMap<String, Integer>();
		this.blocks = new ArrayList<BasicBlock>();
		this.calleeSaved = new HashSet<String>();
		this.name = name;
	}

	/**
	 * populate in & out sets
	 */
	public void analyzeLiveness(){
		for(boolean changed = true; changed; ){
			changed = false;
			for(BasicBlock block : blocks)
				changed = block.populateStatementSets() || changed;
			for(BasicBlock block : blocks)
				changed = block.populateBlockSets() || changed;
		}
		/* mark dead statements */
		/* todo: intergrate this to liveness analysis algorithm... */
		for(BasicBlock block : blocks)
			for(Statement statement : block.statements)
				for(String def : statement.def)
					if(!statement.out.contains(def)) // && !statement.containsCall()) //contained in use & def
						statement.setState(State.Dead);
		int j = arguments < 4 ? arguments > 0 ? arguments : 1 : 4;	/* at least one arg: this */
		/* force block.in && block.statement.in to contain {TEMP i, i in min(4,min(1,arguments))} function arguments */
		for(int i = 0; i < j; ++i) {
			BasicBlock block = blocks.get(0);
			Statement statement = block.statements.get(0);
			String argument = String.format("TEMP %d", i);
			statement.in.add(argument);
			block.in.add(argument);
		}
		/* additional arguments won't participate in register allocation (stack resident) */
		for(int i = 4; i < arguments; ++i) {
			String argument = String.format("TEMP %d", i);
			for(BasicBlock block : blocks)
				for(Statement statement : block.statements) {
					statement.use.remove(argument);
					statement.def.remove(argument);
					statement.out.remove(argument);
					statement.in.remove(argument);
				}
			stackMap.put(argument, spillCount++);
		}
	}

	/**
	 *  constructs adjacency matrix to represent interference graph (global scoped)
	 */
	public void connectComponents() {
		for(BasicBlock block : blocks)
			for(Statement statement : block.statements) {
				for(String vertex : statement.in) {
					if(!adjacencyMap.containsKey(vertex))
						adjacencyMap.put(vertex, new HashSet<String>());
					adjacencyMap.get(vertex).addAll(statement.in);
					adjacencyMap.get(vertex).remove(vertex);
				}
			}
	}

	/**
	 *  calculates static usage frequency for each temp
	 */
	public void computeSpillCost() {
		for(BasicBlock block : blocks)
			for(Statement statement : block.statements) {
				for(String vertex : statement.use) {
					if(!frequencyMap.containsKey(vertex))
						frequencyMap.put(vertex, 0);
					int frequency = frequencyMap.get(vertex);
					frequencyMap.put(vertex, frequency+1);
				}
				for(String vertex : statement.def) {
					if(!frequencyMap.containsKey(vertex))
						frequencyMap.put(vertex, 0);
					int frequency = frequencyMap.get(vertex);
					frequencyMap.put(vertex, frequency + 1);
				}
			}
	}

	private int getSpillCost(String vertex) {
		if(!frequencyMap.containsKey(vertex))
			return 0;
		return frequencyMap.get(vertex);
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
	 */
	public void colorComponents() {
		Map<String, Set<String>> adjacencyMap =
				new ConcurrentHashMap<String, Set<String>>(this.adjacencyMap);	/* local copy */
		Stack<String> stack = new Stack<String>();				/* to keep coloring traverse order */
		List<String> available = registers;

		if(registers.isEmpty()) {
			/* no registers at all, spill all variables */
			for(String vertex : adjacencyMap.keySet())
				stackMap.put(vertex, spillCount++);
			return;
		}

		while(true) {
			for(boolean changed = true; changed; ) {
				changed = false;
				//todo coloring candidate heuristic
				for(String vertex : adjacencyMap.keySet())
				/* if vertex can be colored */
					if(adjacencyMap.get(vertex).size() < available.size()) {
						/* remove vertex neighbours from adjacency map */
						for(String adjacent : adjacencyMap.get(vertex))
							adjacencyMap.get(adjacent).remove(vertex);
						/* remove the vertex itself */
						adjacencyMap.remove(vertex);
						stack.push(vertex);
						changed = true;
					}
			}
			if(adjacencyMap.isEmpty())
				break;
			for(boolean colorable = false; !colorable; ) {
				/* pick spill candidate according to Chaitin's heuristic */
				String spillCandidate = null;
				double currentCost, minimumCost = Double.MAX_VALUE;
				for(String vertex : adjacencyMap.keySet()) {
					double spillCost = getSpillCost(vertex);
					double currentDegree = adjacencyMap.get(vertex).size();
					/* as an alternative, (spillCost / currentDegree) can be used */
					if((currentCost = spillCost / (currentDegree * currentDegree)) < minimumCost) {
						minimumCost = currentCost;
						spillCandidate = vertex;
					}
				}
				/* spill candidate vertex to stack */
				assert spillCandidate != null : "can not pick spill candidate";
				/* assign stack offset to spill candidate (lives permanently in stack) */
				stackMap.put(spillCandidate, spillCount++);
				/* logging */
				logger.log(Level.INFO, "Procedure: " + name.replaceFirst("_", "::") +
						" Spilling: " + spillCandidate + " Cost: " + new DecimalFormat("#.###").format(minimumCost));
				/* remove candidate vertex from adjacency map */
				for(String vertex : adjacencyMap.keySet())
					adjacencyMap.get(vertex).remove(spillCandidate);
				adjacencyMap.remove(spillCandidate);
				/* lookup for a vertex with degree lower than k */
				for(Set<String> adjacentSet : adjacencyMap.values())
					if(adjacentSet.size() < available.size())
						colorable = true;
			}
		}
		adjacencyMap = new HashMap<String, Set<String>>(this.adjacencyMap);
		/* assign registers to temps */
		while(!stack.isEmpty()) {
			available = new ArrayList<String>(registers);
			String vertex = stack.pop();
			for(String adjacent : adjacencyMap.get(vertex))
				available.remove(registerMap.get(adjacent));
			String register = available.get(0);
			registerMap.put(vertex, register);
			/* logging */
			logger.log(Level.INFO, "Procedure: " + name.replaceFirst("_", "::") +
					" Assigning register: " + register + " to " + vertex);
		}
	}

	/**
	 * populates callee and caller spill sets and assigns stack offsets
	 */
	public void populateSpillSets() {

		/* main : no need to store s-type registers, main is called by no one */
		if(!getName().equals("MAIN"))
			for(String register : registerMap.values()) {
				/* S: callee saved */
				if(register.charAt(0) != 's')
					continue;
				calleeSaved.add(register);
				if(!calleeStackOffset.containsKey(register))
					calleeStackOffset.put(register, this.spillCount++);
			}
		/* if procedure doesn't contain a call there is no need to keep space in stack for t-type registers */
		for(BasicBlock block : blocks)
			for(Statement statement : block.statements)
				if(statement.containsCall())
					for(String vertex : statement.out) {
						if(!statement.in.contains(vertex))
							continue;
						String register = registerMap.get(vertex);
						/* T: caller saved */
						if(register != null && register.charAt(0) == 't') {
							statement.callerSaved.add(register);
							if(!callerStackOffset.containsKey(register))
								callerStackOffset.put(register, this.spillCount++);
						}
					}
		logger.log(Level.INFO, "Procedure: " + name.replaceFirst("_", "::") + " Spilled: " + this.spillCount);
	}

	public String where(String vertex) {
		if(registerMap.containsKey(vertex))
			return "resides @register: " + registerMap.get(vertex);
		if(stackMap.containsKey(vertex))
			return "resides @stack with offset: " + stackMap.get(vertex);
		return "resides @the outer space...";    //lol
	}

	public Set<String> getCalleeSaved() {
		return calleeSaved;
	}

	public int getCalleeStackOffset(String register) {
		return calleeStackOffset.get(register);
	}

	public int getCallerStackOffset(String register) {
		return callerStackOffset.get(register);
	}

	public boolean mappedInRegister(String vertex) {
		return registerMap.containsKey(vertex);
	}

	public boolean mappedInStack(String vertex) {
		return stackMap.containsKey(vertex);
	}

	public String getRegister(String vertex) {
		return registerMap.get(vertex);
	}

	public int getStackOffset(String vertex) {
		return stackMap.get(vertex);
	}

	/* dummy methods */
	public String getName(){
		return name;
	}

	public int getArguments() {
		return arguments;
	}

	public void setArguments(int arguments) {
		this.arguments = arguments;
	}

	public int getMaxArguments() {
		return maxArguments;
	}

	public void setMaxArguments(int maxArguments) {
		this.maxArguments = maxArguments;
	}

	public int getSpillCount() {
		return spillCount;
	}

	public void addBlock(BasicBlock block){
		blocks.add(block);
	}

	public BasicBlock getBlock(int block) {
		return blocks.get(block);
	}

	@Override
	public String toString() {
		StringBuilder message = new StringBuilder();
		message.append(name)
			   .append(" [ arguments: ")
			   .append(arguments)
			   .append(" spilled: ")
				.append(spillCount)
			   .append(" max arguments: ")
			   .append(maxArguments)
			   .append(" basic blocks: ")
			   .append(blocks.size())
			   .append(" ]\n");
		for(BasicBlock block : blocks)
			message.append(block.toString());
		List<String> printSet = new ArrayList<String>(stackMap.keySet());
		Collections.sort(printSet);
		for(String vertex : printSet)
			message.append("Spilled argument: ")
				   .append(vertex)
				   .append(" lies on stack offset: ")
					.append(stackMap.get(vertex))
					.append("\n");
		return message.toString();
	}
}
