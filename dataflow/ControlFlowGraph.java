package dataflow;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ControlFlowGraph {
	private Map<String, Procedure> procedures;
	private Map<String,String> labels;			/* global label map */
	private int labelCount;
	private int blockCount;		//todo

	private static final Logger logger;
	public static final List<String> registers;


	static {
		logger = Logger.getLogger(ControlFlowGraph.class.getName());
		logger.setLevel(Level.OFF);
		registers = new ArrayList<String>(){{
			for(int i=0; i<8; ++i) add(String.format("s%d",i));
			//		for(int i=0; i<10; ++i) add(String.format("t%d",i));
		}};
		assert registers.size() > 0 : "no registers available";
	}

	public ControlFlowGraph() {
		this.procedures = new HashMap<String, Procedure>();
		this.labels = new HashMap<String, String>();
		this.labelCount = this.blockCount = 0;
	}

	public String getGlobalLabel(String label){
		if(!labels.containsKey(label))
			labels.put(label,String.format("L%d", labelCount++));
		return labels.get(label);
	}

	public void addProcedure(String procedure,Procedure procedureObj){
		procedures.put(procedure,procedureObj);
	}

	public Procedure getProcedure(String procedure){
		return procedures.get(procedure);
	}

	public void compute(){

		logger.log(Level.INFO, "Analyzing variable liveness... ");
		for(Procedure procedure : procedures.values())
			procedure.analyzeLiveness();

		logger.log(Level.INFO, "Calculating spill cost... ");
		for(Procedure procedure : procedures.values())
			procedure.computeSpillCost();

		logger.log(Level.INFO, "Constructing interference graph... ");
		for(Procedure procedure : procedures.values())
			procedure.connectComponents();

		logger.log(Level.INFO, "Running Chaitin's algorithm... ");
		for(Procedure procedure : procedures.values())
			procedure.colorComponents();

		logger.log(Level.INFO, toString());
	}


	@Override
	public String toString() {
		StringBuilder message = new StringBuilder();
		for(Map.Entry<String, Procedure> entry : procedures.entrySet())
			message.append(entry.getValue()).append('\n');
		return message.toString();
	}
}
