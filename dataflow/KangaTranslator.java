package dataflow;

import syntaxtree.*;
import visitor.GJNoArguDepthFirst;

import java.util.Enumeration;
import java.util.Stack;

import static dataflow.Statement.State;
import static dataflow.Statement.Type;

public class KangaTranslator extends GJNoArguDepthFirst<String> {
	private final ControlFlowGraph cfg;
	private final KangaBuffer b;
	private Procedure procedure;
	private BasicBlock block;
	private Statement statement;
	private int blockCount;
	private int statementCount;
	private Stack<String> vregs;
	private boolean procedureLabel = false;

	public KangaTranslator(ControlFlowGraph cfg) {
		this.b = new KangaBuffer();
		this.cfg = cfg;
		this.blockCount = 0;
		this.statementCount = 0;
	}

	private String getRegister(String vertex) throws Exception {
		if(!procedure.mappedInRegister(vertex) && !procedure.mappedInStack(vertex))
			throw new Exception("invalid state " + vertex + " " + procedure.where(vertex));
		if(procedure.mappedInRegister(vertex))
			return procedure.getRegister(vertex);
		String register = vregs.pop();
		b.append("ALOAD", register, "SPILLEDARG", procedure.getStackOffset(vertex));
		return register;
	}

	private String getLabel(String label) throws Exception {
		if(!cfg.containsGlobalLabel(procedure.getName() + "_" + label))
			throw new Exception("invalid state " + procedure.getName() + "_" + label);
		return cfg.getGlobalLabel(procedure.getName() + "_" + label);
	}

	/**
	 * Represents a grammar list, e.g. ( A )+
	 */
	@Override
	public String visit(NodeList n) throws Exception {
		for(Enumeration<Node> e = n.elements(); e.hasMoreElements(); )
			e.nextElement().accept(this);
		return null;
	}

	/**
	 * Represents an optional grammar list, e.g. ( A )*
	 */
	@Override
	public String visit(NodeListOptional n) throws Exception {
		for(Enumeration<Node> e = n.elements(); e.hasMoreElements(); )
			e.nextElement().accept(this);
		return null;
	}

	/**
	 * Represents an grammar optional node, e.g. ( A )? or [ A ]
	 */
	@Override
	public String visit(NodeOptional n) throws Exception {
		if(n.present() && n.node instanceof Label) {
			String label = cfg.getGlobalLabel(procedure.getName() + "_" + n.node.accept(this));
			b.append(label);
			if(statement != null && statement.getType() != Type.JumpStmt) {
				block = procedure.getBlock(blockCount++);	/* new basic block */
				statementCount = 0;
			}
		}
		return n.present() ? n.node.accept(this) : null;
	}

	/**
	 * Represents a sequence of nodes nested within a choice, list,
	 * optional list, or optional, e.g. ( A B )+ or [ C D E ]
	 */
	@Override
	public String visit(NodeSequence n) throws Exception {
		for(Enumeration<Node> e = n.elements(); e.hasMoreElements(); )
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
		/* reset block counter */
		blockCount = 0;
		procedure = cfg.getProcedure(n.f0.tokenImage);
		b.append(procedure.getName(),
				"[", procedure.getArguments(), "]",
				"[", procedure.getSpillCount(), "]",
				"[", procedure.getMaxArguments(), "]");
		/* new basic block */
		block = procedure.getBlock(blockCount++);
		/* reset statement counter */
		statementCount = 0;
		statement = null;

		n.f1.accept(this);
		b.append("END");
		n.f3.accept(this);
		return b.toString();
	}

	/**
	 * Grammar production:
	 * f0 -> ( ( Label() )? Stmt() )*
	 */
	@Override
	public String visit(StmtList n) throws Exception {
		n.f0.accept(this);
		return "StmtList";
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
		/* reset block counter */
		blockCount = 0;
		procedure = cfg.getProcedure(n.f0.f0.tokenImage);
		b.append(procedure.getName(),
				'[', procedure.getArguments(), ']',
				'[', procedure.getSpillCount(), ']',
				'[', procedure.getMaxArguments(), ']');
		/* new basic block */
		block = procedure.getBlock(blockCount++);
		/* reset statement counter */
		statementCount = 0;
		statement = null;

		/* mips convention : callee stores all s-type registers that he uses */
		for(String register : procedure.getCalleeSaved())
			b.append("ASTORE", "SPILLEDARG", procedure.getCalleeStackOffset(register), register);

		for(int i = 0; i < 4 && i < procedure.getArguments(); ++i) {
			String argument = String.format("TEMP %d", i);
			if(!procedure.mappedInRegister(argument) && !procedure.mappedInStack(argument))
				throw new Exception("invalid state " + argument + " " + procedure.where(argument));
			else if(procedure.mappedInRegister(argument))
				b.append("MOVE", procedure.getRegister(argument), "a" + i);
			else if(procedure.mappedInStack(argument))
				b.append("ASTORE", "SPILLEDARG", procedure.getStackOffset(argument), "a" + i);
		}

		n.f4.accept(this);

		/* mips convention : callee loads previous values of s-type registers that he used */
		for(String register : procedure.getCalleeSaved())
			b.append("ALOAD", register, "SPILLEDARG", procedure.getCalleeStackOffset(register));
		b.append("END");
		return "Procedure";
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
		statement = block.getStatement(statementCount++);
		if(statement.getState() != State.Live)
			return "DeadStmt";
		vregs = new Stack<String>() {{
			for(int i = 1; i >= 0; push("v" + i--)) ;
		}};
		n.f0.accept(this);
		return "LiveStmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "NOOP"
	 */
	@Override
	public String visit(NoOpStmt n) throws Exception {
		b.append("NOOP");
		assert Type.NoOpStmt == statement.getType();
		return "NoOpStmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "ERROR"
	 */
	@Override
	public String visit(ErrorStmt n) throws Exception {
		b.append("ERROR");
		assert Type.ErrorStmt == statement.getType();
		return "ErrorStmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "CJUMP"
	 * f1 -> Temp()
	 * f2 -> Label()
	 */
	@Override
	public String visit(CJumpStmt n) throws Exception {
		assert Type.CJumpStmt == statement.getType();

		String label = getLabel(n.f2.f0.tokenImage);
		String register = getRegister(n.f1.accept(this));
		b.append("CJUMP", register, label);

		/* new basic block */
		block = procedure.getBlock(blockCount++);
		statementCount = 0;
		return "CJumpStmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "JUMP"
	 * f1 -> Label()
	 */
	@Override
	public String visit(JumpStmt n) throws Exception {
		assert Type.JumpStmt == statement.getType();

		String label = getLabel(n.f1.f0.tokenImage);
		b.append("JUMP", label);

		/* new basic block */
		block = procedure.getBlock(blockCount++);
		statementCount = 0;
		return "JumpStmt";
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
		assert Type.HStoreStmt == statement.getType();

		String target = getRegister(n.f1.accept(this));
		String offset = n.f2.f0.tokenImage;
		String source = getRegister(n.f3.accept(this));
		b.append("HSTORE", target, offset, source);
		//fixme: update spilledarg:
		if(target.charAt(0) == 'v')
			b.append("ASTORE", "SPILLEDARG", procedure.getStackOffset(n.f1.accept(this)), target);
		return "HStoreStmt";
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
		assert Type.HLoadStmt == statement.getType();

		String target = getRegister(n.f1.accept(this));
		String source = getRegister(n.f2.accept(this));
		String offset = n.f3.f0.tokenImage;
		b.append("HLOAD", target, source, offset);
		//fixme: update spilledarg:
		if(target.charAt(0) == 'v')
			b.append("ASTORE", "SPILLEDARG", procedure.getStackOffset(n.f1.accept(this)), target);
		return "HLoadStmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "MOVE"
	 * f1 -> Temp()
	 * f2 -> Exp()
	 */
	@Override
	public String visit(MoveStmt n) throws Exception {
		assert Type.MoveStmt == statement.getType();

		String target = n.f1.accept(this);
		if(!procedure.mappedInRegister(target) && !procedure.mappedInStack(target))
			throw new Exception("invalid state " + target + " " + procedure.where(target));
		Node node = n.f2.f0.choice;
		if(node instanceof HAllocate) {
			String exp = node.accept(this);
			if(procedure.mappedInRegister(target)) {
				target = procedure.getRegister(target);
				b.append("MOVE", target, exp);
			}
			if(procedure.mappedInStack(target)) {
				b.append("MOVE", "v0", exp);
				b.append("ASTORE", "SPILLEDARG", procedure.getStackOffset(target), "v0");
			}
		}
		if(node instanceof BinOp) {
			String exp = node.accept(this);
			if(procedure.mappedInRegister(target)) {
				target = procedure.getRegister(target);
				b.append("MOVE", target, exp);
			}
			if(procedure.mappedInStack(target)) {
				b.append("MOVE", "v0", exp);
				b.append("ASTORE", "SPILLEDARG", procedure.getStackOffset(target), "v0");
			}
		}
		if(node instanceof SimpleExp) {
			procedureLabel = true;
			String exp = node.accept(this);
			procedureLabel = false;
			if(procedure.mappedInRegister(target)) {
				target = procedure.getRegister(target);
				b.append("MOVE", target, exp);
			}
			if(procedure.mappedInStack(target)) {
				b.append("MOVE", "v0", exp);
				b.append("ASTORE", "SPILLEDARG", procedure.getStackOffset(target), "v0");
			}
		}

		if(node instanceof Call) {
			String exp = node.accept(this);
			if(procedure.mappedInRegister(target)) {
				target = procedure.getRegister(target);
				b.append("MOVE", target, exp);
			}
			if(procedure.mappedInStack(target)) {
				b.append("MOVE", "v0", exp);
				b.append("ASTORE", "SPILLEDARG", procedure.getStackOffset(target), "v0");
			}
		}

		return "MoveStmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "PRINT"
	 * f1 -> SimpleExp()
	 */
	@Override
	public String visit(PrintStmt n) throws Exception {
		assert Type.PrintStmt == statement.getType();

		String exp = n.f1.accept(this);
		b.append("PRINT", exp);

		return "PrintStmt";
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
		return n.f0.accept(this);
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

		statement = block.getStatement(statementCount++);
		assert Type.ReturnStmt == statement.getType();

		vregs = new Stack<String>() {{
			for(int i = 1; i >= 0; push("v" + i--)) ;
		}};

		String exp = n.f3.accept(this);
		b.append("MOVE", "v0", exp);

		return "StmtExp";
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

		int arg = 0;
		for(Node node : n.f3.nodes) {
			String target = node.accept(this);
			String register = null;
			if(!procedure.mappedInRegister(target) && !procedure.mappedInStack(target))
				throw new Exception("invalid state " + target + " " + procedure.where(target));
			if(procedure.mappedInRegister(target))
				register = procedure.getRegister(target);
			else {
				register = "v0";
				b.append("ALOAD", register, "SPILLEDARG", procedure.getStackOffset(target));
			}
			if(arg < 4)
				b.append("MOVE", "a" + arg++, register);
			else
				b.append("PASSARG", arg++ - 3, register);
		}

		String proc = n.f1.accept(this);

		/* mips convention : store used t-type registers */
		for(String register : statement.getCallerSaved())
			b.append("ASTORE", "SPILLEDARG", procedure.getCallerStackOffset(register), register);

		b.append("CALL", proc);

		/* mips convention : load used t-type registers */
		for(String register : statement.getCallerSaved())
			b.append("ALOAD", register, "SPILLEDARG", procedure.getCallerStackOffset(register));
		return "v0";
	}

	/**
	 * Grammar production:
	 * f0 -> "HALLOCATE"
	 * f1 -> SimpleExp()
	 */
	@Override
	public String visit(HAllocate n) throws Exception {
		String exp = n.f1.accept(this);
		return String.format("HALLOCATE %s",exp);
	}

	/**
	 * Grammar production:
	 * f0 -> Operator()
	 * f1 -> Temp()
	 * f2 -> SimpleExp()
	 */
	@Override
	public String visit(BinOp n) throws Exception {
		String operator = n.f0.accept(this);
		String loperand = getRegister(n.f1.accept(this));
		String roperand = n.f2.accept(this);

		return String.format("%s %s %s", operator, loperand, roperand);
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
		Node node = n.f0.choice;
		if(node instanceof IntegerLiteral)
			return node.accept(this);
		if(node instanceof Temp) {
			String register = getRegister(node.accept(this));
			return register;
		}
		if(node instanceof Label)
			return procedureLabel ? node.accept(this) : getLabel(node.accept(this));
		/* never reached */
		return "SimpleExp";
	}

	/**
	 * Grammar production:
	 * f0 -> "TEMP"
	 * f1 -> IntegerLiteral()
	 */
	@Override
	public String visit(Temp n) throws Exception {
		return String.format("TEMP %s", n.f1.f0.tokenImage);
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

	public static final class KangaBuffer {
		StringBuilder s = new StringBuilder();

		public <T> void append(T... args) {
			for(T arg : args)
				s.append(arg).append(' ');
			s.append('\n');
		}

		@Override
		public String toString() {
			return s.toString();
		}
	}
}
