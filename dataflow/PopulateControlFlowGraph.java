package dataflow;

import syntaxtree.*;
import visitor.GJNoArguDepthFirst;

import java.util.*;

import static dataflow.Statement.Type;
/*
 * Basic Block Inheritance Tree Logic:
 *
 * a basic block is created when control flow reaches
 * - a new procedure
 * - a jump label
 * - after a jump statement
 * - after a cjump statement
 */

public class PopulateControlFlowGraph extends GJNoArguDepthFirst<String> {
	private Map<String,Set<BasicBlock>> definition;	/* label definition to basic block mapping */
	private Map<String,Set<BasicBlock>> usage;		/* label usage to basic block mapping */
	private ControlFlowGraph cfg;
	private Procedure procedure;
	private BasicBlock block;
	private Statement statement;
	private int blockCount;

	public PopulateControlFlowGraph(ControlFlowGraph cfg) {
		this.definition = new HashMap<String, Set<BasicBlock>>();
		this.usage = new HashMap<String, Set<BasicBlock>>();
		this.cfg = cfg;
		this.blockCount = 0;
	}

	/**
	 * Represents a grammar list, e.g. ( A )+
	 */
	@Override
	public String visit(NodeList n) throws Exception {
		for (Enumeration<Node> e = n.elements(); e.hasMoreElements(); )
			e.nextElement().accept(this);
		return null;
	}

	/**
	 * Represents an optional grammar list, e.g. ( A )*
	 */
	@Override
	public String visit(NodeListOptional n) throws Exception {
		for (Enumeration<Node> e = n.elements(); e.hasMoreElements(); )
			e.nextElement().accept(this);
		return null;
	}

	/**
	 * Represents an grammar optional node, e.g. ( A )? or [ A ]
	 */
	@Override
	public String visit(NodeOptional n) throws Exception {
		if(n.present() && n.node instanceof Label){
			String label = cfg.getGlobalLabel(procedure.getName() + "_" + n.node.accept(this));
			if(statement != null && statement.getType() != Type.JumpStmt) {
				/* new basic block */
				BasicBlock block = new BasicBlock(blockCount++);
				procedure.addBlock(block);
				this.block.addSuccessor(block);
				this.block = block;
			}
			if(!definition.containsKey(label))
				definition.put(label,new HashSet<BasicBlock>());
			definition.get(label).add(block);
		}
		return n.present() ? n.node.accept(this) : null;
	}

	/**
	 * Represents a sequence of nodes nested within a choice, list,
	 * optional list, or optional, e.g. ( A B )+ or [ C D E ]
	 */
	@Override
	public String visit(NodeSequence n) throws Exception {
		for (Enumeration<Node> e = n.elements(); e.hasMoreElements(); )
			e.nextElement().accept(this);
		return null;
	}

	/**
	 * Represents a single token in the grammar.  If the "-tk" option
	 * is used, also contains a Vector of preceding special tokens.
	 */
	@Override
	public String visit(NodeToken n) throws Exception {
		return n.tokenImage;
	}

	/**
	 * Grammar production:
	 * f0 -> "MAIN"
	 * f1 -> StmtList()
	 * f2 -> "END"
	 * f3 -> ( Procedure() )*
	 * f4 -> <EOF>
	 */
	@Override
	public String visit(Goal n) throws Exception {
		/* new procedure */
		procedure = new Procedure(n.f0.tokenImage);
		procedure.setArguments(0);
		cfg.addProcedure(procedure.getName(), procedure);

		/* new basic block */
		block = new BasicBlock(blockCount++);
		procedure.addBlock(block);
		statement = null;

		n.f1.accept(this);
		n.f3.accept(this);

		/* update basic block inheritance tree */
		for(String label : usage.keySet())
			for(BasicBlock block : usage.get(label))
				for(BasicBlock successor : definition.get(label))
					block.addSuccessor(successor);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> ( ( Label() )? Stmt() )*
	 */
	@Override
	public String visit(StmtList n) throws Exception {
		n.f0.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> Label()
	 * f1 -> "["
	 * f2 -> IntegerLiteral()
	 * f3 -> "]"
	 * f4 -> StmtExp()
	 */
	@Override
	public String visit(syntaxtree.Procedure n) throws Exception {
		/* new procedure */
		procedure = new Procedure(n.f0.f0.tokenImage);
		procedure.setArguments(Integer.parseInt(n.f2.f0.tokenImage));
		cfg.addProcedure(procedure.getName(), procedure);
		/* new basic block */
		block = new BasicBlock(blockCount++);
		procedure.addBlock(block);
		statement = null;

		n.f4.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> NoOpStmt()
	 *       | ErrorStmt()
	 *       | CJumpStmt()
	 *       | JumpStmt()
	 *       | HStoreStmt()
	 *       | HLoadStmt()
	 *       | MoveStmt()
	 *       | PrintStmt()
	 */
	@Override
	public String visit(Stmt n) throws Exception {
		statement = new Statement();
		block.addStatement(statement);
		n.f0.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "NOOP"
	 */
	@Override
	public String visit(NoOpStmt n) throws Exception {
		statement.setType(Type.NoOpStmt);
		return n.f0.tokenImage;
	}

	/**
	 * Grammar production:
	 * f0 -> "ERROR"
	 */
	@Override
	public String visit(ErrorStmt n) throws Exception {
		statement.setType(Type.ErrorStmt);
		return n.f0.tokenImage;
	}

	/**
	 * Grammar production:
	 * f0 -> "CJUMP"
	 * f1 -> Temp()
	 * f2 -> Label()
	 */
	@Override
	public String visit(CJumpStmt n) throws Exception {
		statement.setType(Type.CJumpStmt);
		statement.use.add(n.f1.accept(this));
		String label = cfg.getGlobalLabel(procedure.getName() + "_" + n.f2.f0.tokenImage);
		if(!usage.containsKey(label))
			usage.put(label,new HashSet<BasicBlock>());

		/* new basic block */
		BasicBlock block = new BasicBlock(blockCount++);
		procedure.addBlock(block);
		usage.get(label).add(this.block);
		this.block.addSuccessor(block);
		this.block = block;
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "JUMP"
	 * f1 -> Label()
	 */
	@Override
	public String visit(JumpStmt n) throws Exception {
		statement.setType(Type.JumpStmt);
		String label = cfg.getGlobalLabel(procedure.getName() + "_" + n.f1.f0.tokenImage);
		if(!usage.containsKey(label))
			usage.put(label,new HashSet<BasicBlock>());

		/* new basic block */
		BasicBlock block = new BasicBlock(blockCount++);
		procedure.addBlock(block);
		usage.get(label).add(this.block);
		this.block = block;
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "HSTORE"
	 * f1 -> Temp()
	 * f2 -> IntegerLiteral()
	 * f3 -> Temp()
	 */
	@Override
	public String visit(HStoreStmt n) throws Exception {
		statement.setType(Type.HStoreStmt);
		statement.def.add(n.f1.accept(this));
		statement.use.add(n.f3.accept(this));
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "HLOAD"
	 * f1 -> Temp()
	 * f2 -> Temp()
	 * f3 -> IntegerLiteral()
	 */
	@Override
	public String visit(HLoadStmt n) throws Exception {
		statement.setType(Type.HLoadStmt);
		statement.def.add(n.f1.accept(this));
		statement.use.add(n.f2.accept(this));
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "MOVE"
	 * f1 -> Temp()
	 * f2 -> Exp()
	 */
	@Override
	public String visit(MoveStmt n) throws Exception {
		statement.setType(Type.MoveStmt);
		statement.def.add(n.f1.accept(this));
		n.f2.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "PRINT"
	 * f1 -> SimpleExp()
	 */
	@Override
	public String visit(PrintStmt n) throws Exception {
		statement.setType(Type.PrintStmt);
		n.f1.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> Call()
	 *       | HAllocate()
	 *       | BinOp()
	 *       | SimpleExp()
	 */
	@Override
	public String visit(Exp n) throws Exception {
		n.f0.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "BEGIN"
	 * f1 -> StmtList()
	 * f2 -> "RETURN"
	 * f3 -> SimpleExp()
	 * f4 -> "END"
	 */
	@Override
	public String visit(StmtExp n) throws Exception {
		n.f1.accept(this);
		statement = new Statement();
		statement.setType(Type.ReturnStmt);
		block.addStatement(statement);
		n.f3.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "CALL"
	 * f1 -> SimpleExp()
	 * f2 -> "("
	 * f3 -> ( Temp() )*
	 * f4 -> ")"
	 */
	@Override
	public String visit(Call n) throws Exception {
		if(procedure.getMaxArguments() < n.f3.size())
			procedure.setMaxArguments(n.f3.size());
		n.f1.accept(this);
		for(Node node : n.f3.nodes)
			statement.use.add(node.accept(this));
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "HALLOCATE"
	 * f1 -> SimpleExp()
	 */
	@Override
	public String visit(HAllocate n) throws Exception {
		n.f1.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> Operator()
	 * f1 -> Temp()
	 * f2 -> SimpleExp()
	 */
	@Override
	public String visit(BinOp n) throws Exception {
		n.f1.accept(this);
		statement.use.add(n.f1.accept(this));
		n.f2.accept(this);
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "LT"
	 *       | "PLUS"
	 *       | "MINUS"
	 *       | "TIMES"
	 */
	@Override
	public String visit(Operator n) throws Exception {
		return n.f0.accept(this);
	}

	/**
	 * Grammar production:
	 * f0 -> Temp()
	 *       | IntegerLiteral()
	 *       | Label()
	 */
	@Override
	public String visit(SimpleExp n) throws Exception {
		if(n.f0.choice instanceof Temp)
			statement.use.add(n.f0.accept(this));
		return null;
	}

	/**
	 * Grammar production:
	 * f0 -> "TEMP"
	 * f1 -> IntegerLiteral()
	 */
	@Override
	public String visit(Temp n) throws Exception {
		return String.format("TEMP %s",n.f1.f0.tokenImage);
	}

	/**
	 * Grammar production:
	 * f0 -> <INTEGER_LITERAL>
	 */
	@Override
	public String visit(IntegerLiteral n) throws Exception {
		return n.f0.tokenImage;
	}

	/**
	 * Grammar production:
	 * f0 -> <IDENTIFIER>
	 */
	@Override
	public String visit(Label n) throws Exception {
		return n.f0.tokenImage;
	}
}
