package dataflow;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ControlFlowGraph {
	private static final Logger logger;

	static {
		logger = Logger.getLogger(ControlFlowGraph.class.getName());
		logger.setLevel(Level.OFF);
	}

	private Map<String, Procedure> procedures;
	private Map<String, String> labels;			/* global label map */
	private int labelCount;

	public ControlFlowGraph() {
		this.procedures = new LinkedHashMap<String, Procedure>();
		this.labels = new HashMap<String, String>();
		this.labelCount = 0;
	}

	public boolean containsGlobalLabel(String label) {
		return labels.containsKey(label);
	}

	public String getGlobalLabel(String label) {
		if(!labels.containsKey(label))
			labels.put(label, String.format("L%d", labelCount++));
		return labels.get(label);
	}

	public void addProcedure(String procedure, Procedure procedureObj) {
		procedures.put(procedure, procedureObj);
	}

	public Procedure getProcedure(String procedure) {
		return procedures.get(procedure);
	}

	public void compute() {

		logger.log(Level.INFO, "Analyzing variable liveness... ");
		for(Procedure procedure : procedures.values())
			procedure.analyzeLiveness();

		logger.log(Level.INFO, "Constructing interference graph... ");
		for(Procedure procedure : procedures.values())
			procedure.connectComponents();

		logger.log(Level.INFO, "Computing spill cost... ");
		for(Procedure procedure : procedures.values())
			procedure.computeSpillCost();

		logger.log(Level.INFO, "Running Chaitin's algorithm... ");
		for(Procedure procedure : procedures.values())
			procedure.colorComponents();

		logger.log(Level.INFO, "Populating Callee and Caller saved register sets... ");
		for(Procedure procedure : procedures.values())
			procedure.populateSpillSets();

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
