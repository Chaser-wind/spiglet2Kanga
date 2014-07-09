package dataflow;

import java.util.*;

import static sets.Sets.*;

public final class BasicBlock {
	public List<BasicBlock> successors;
	public List<Statement> statements;
	public Set<String> out;
	public Set<String> in;
	private int id;						/* unique block identifier */
	private int maxStatementDegree;		/* debug purpose */

	public BasicBlock(int id) {
		this.successors = new ArrayList<BasicBlock>();
		this.statements = new ArrayList<Statement>();
		this.out = new HashSet<String>();
		this.in = new HashSet<String>();
		this.id = id;
		this.maxStatementDegree = 0;
	}

	public void addStatement(Statement statement){
		statements.add(statement);
	}

	public void addSuccessor(BasicBlock successor){
		successors.add(successor);
	}

	public boolean populateStatementSets(){
		boolean changed = false;
		for(int i = statements.size()-1; i > -1; --i){
			Statement statement = statements.get(i);
			/* in[i] = (out[i] except def[i]) union use[i] */
			Set<String> inCopy = new HashSet<String>(statement.in);
			statement.in = union(difference(statement.out, statement.def), statement.use);
			changed = changed || differ(statement.in, inCopy);
			maxStatementDegree = statement.in.size() > maxStatementDegree ? statement.in.size() : maxStatementDegree;
			/* out[i] = in[successor(i)] for all successors of i */
			if(i+1 != statements.size()){
				Set<String> outCopy = new HashSet<String>(statement.out);
				statement.out = statements.get(i+1).in;
				changed = changed || differ(statement.out, outCopy);
			}
		}
		return changed;
	}

	public boolean populateBlockSets(){
		int lastStatementIndex = statements.size()-1;
		int firstStatementIndex = 0;
		Set<String> outCopy = new HashSet<String>(statements.get(lastStatementIndex).out);
		for(BasicBlock successor : successors)
			statements.get(lastStatementIndex).out =
					union(statements.get(lastStatementIndex).out, successor.statements.get(firstStatementIndex).in);
		this.in = new HashSet<String>(statements.get(firstStatementIndex).in);
		this.out = new HashSet<String>(statements.get(lastStatementIndex).out);
		return differ(out,outCopy);
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null || getClass() != obj.getClass())
			return false;
		return id == (((BasicBlock) obj).id);
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public String toString() {
		StringBuilder message = new StringBuilder();
		message.append("Basic block id: " + id + " Successors: " + new ArrayList<Integer>() {{
			for(BasicBlock block : successors)
				add(block.id);
		}} + " Max statement degree: " + maxStatementDegree + " in: " + in + " out: " + out + "\n");
		for(Statement statement : statements)
			message.append(statement);
		message.append("\n");
		return message.toString();
	}
}
