package dataflow;

import java.util.HashSet;
import java.util.Set;

public final class Statement {
	public Set<String> def;
	public Set<String> use;
	public Set<String> out;
	public Set<String> in;
	private Type type;


	public static enum Type {
		NoOpStmt, ErrorStmt, CJumpStmt, JumpStmt, HStoreStmt, HLoadStmt, MoveStmt, PrintStmt, ReturnStmt,Undefined
	}

	public Statement(){
		this.def = new HashSet<String>();
		this.use = new HashSet<String>();
		this.out = new HashSet<String>();
		this.in = new HashSet<String>();
		this.type = Type.Undefined;
	}

	public void setType(Type type){
		this.type = type;
	}

	public Type getType(){
		return type;
	}

	@Override
	public String toString() {
//		StringBuilder message = new StringBuilder();
//		message.append(type + "\n");
//		message.append("\tdef: " + def + "\n");
//		message.append("\tuse: " + use + "\n");
//		message.append("\tin : " + in + "\n");
//		message.append("\tout: " + out + "\n");
//		return message.toString();
		return String.format("\t%-10s def: %s use: %s in: %s out: %s\n",type,def,use,in,out);
	}
}