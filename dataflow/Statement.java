package dataflow;

import java.util.HashSet;
import java.util.Set;

public final class Statement {
	public Set<String> def;
	public Set<String> use;
	public Set<String> out;
	public Set<String> in;
	public Set<String> callerSaved;		/* contains t-type registers that need to be stored before call */
	private boolean containsCall;
	private State state;				/* contains statement state ,State.Live or State.Dead */
	private Type type;					/* contains statement type */

	public Statement() {
		this.def = new HashSet<String>();
		this.use = new HashSet<String>();
		this.out = new HashSet<String>();
		this.in = new HashSet<String>();
		this.containsCall = false;
		this.state = State.Live;
		this.type = Type.Undefined;
	}

	public Set<String> getCallerSaved() {
		return callerSaved;
	}

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public Type getType(){
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public boolean containsCall() {
		return containsCall;
	}

	public void setCall() {
		if(containsCall)
			return;
		containsCall = true;
		callerSaved = new HashSet<String>();
	}

	@Override
	public String toString() {
		StringBuilder message = new StringBuilder();
		message.append(String.format("\t%11s ", state != State.Live ? "(dead code)" : ""))
				.append(String.format("%-10s", type))
				.append(" def: ")
				.append(def)
				.append(" use: ")
				.append(use)
				.append(" in: ")
				.append(in)
				.append(" out: ")
				.append(out);
		if(callerSaved != null)
			message.append(" saved: " + callerSaved);
		message.append("\n");
		return message.toString();
	}

	public static enum State {
		Live, Dead
	}

	public static enum Type {
		NoOpStmt, ErrorStmt, CJumpStmt, JumpStmt, HStoreStmt, HLoadStmt, MoveStmt, PrintStmt, ReturnStmt, Undefined
	}
}