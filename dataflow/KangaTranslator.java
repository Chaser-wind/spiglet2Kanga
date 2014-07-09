package dataflow;

import syntaxtree.*;
import visitor.GJNoArguDepthFirst;

/**
 * Created by ek on 7/4/14.
 */
public class KangaTranslator extends GJNoArguDepthFirst<String> {
	private final KangaBuffer b;
	private final ControlFlowGraph cfg;
	private Procedure procedure;

	public static final class KangaBuffer {
		StringBuilder s = new StringBuilder();

		public <T> void append(T ... args){
			for(T arg : args)
				s.append(arg).append(' ');
			s.append('\n');
		}

		@Override
		public String toString() {
			return s.toString();
		}
	}

	public KangaTranslator(ControlFlowGraph cfg) {
		this.b = new KangaBuffer();
		this.cfg = cfg;
	}

	@Override
	public String visit(NodeList n) throws Exception {
		return super.visit(n);
	}

	@Override
	public String visit(NodeListOptional n) throws Exception {
		return super.visit(n);
	}

	@Override
	public String visit(NodeOptional n) throws Exception {
		return super.visit(n);
	}

	@Override
	public String visit(NodeSequence n) throws Exception {
		return super.visit(n);
	}

	@Override
	public String visit(NodeToken n) throws Exception {
		return super.visit(n);
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
		procedure = cfg.getProcedure(n.f0.tokenImage);
		b.append(procedure.getName(),
				'[',procedure.getArguments(),']',
				'[',procedure.getSpillArguments(),']',
				'[',procedure.getMaxArguments(),']');
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
		procedure = cfg.getProcedure(n.f0.f0.tokenImage);
		b.append(procedure.getName(),
				'[',procedure.getArguments(),']',
				'[',procedure.getSpillArguments(),']',
				'[',procedure.getMaxArguments(),']');
		n.f4.accept(this);
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
		n.f0.accept(this);
		return "Stmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "NOOP"
	 */
	@Override
	public String visit(NoOpStmt n) throws Exception {
		b.append("NOOP");
		return "NoOpStmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "ERROR"
	 */
	@Override
	public String visit(ErrorStmt n) throws Exception {
		b.append("ERROR");
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
//		String label = cfg.label(procedure, n.f2.f0.tokenImage);
//		b.append("CJUMP","REG",label);
		return "CJumpStmt";
	}

	/**
	 * Grammar production:
	 * f0 -> "JUMP"
	 * f1 -> Label()
	 */
	@Override
	public String visit(JumpStmt n) throws Exception {
//		String label = cfg.label(procedure, n.f1.f0.tokenImage);
//		b.append("JUMP",label);
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
		return super.visit(n);
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
		return super.visit(n);
	}

	/**
	 * Grammar production:
	 * f0 -> "MOVE"
	 * f1 -> Temp()
	 * f2 -> Exp()
	 */
	@Override
	public String visit(MoveStmt n) throws Exception {
		return super.visit(n);
	}

	/**
	 * Grammar production:
	 * f0 -> "PRINT"
	 * f1 -> SimpleExp()
	 */
	@Override
	public String visit(PrintStmt n) throws Exception {
		return super.visit(n);
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
		return super.visit(n);
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
		return super.visit(n);
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
		return super.visit(n);
	}

	/**
	 * Grammar production:
	 * f0 -> "HALLOCATE"
	 * f1 -> SimpleExp()
	 */
	@Override
	public String visit(HAllocate n) throws Exception {
		return super.visit(n);
	}

	/**
	 * Grammar production:
	 * f0 -> Operator()
	 * f1 -> Temp()
	 * f2 -> SimpleExp()
	 */
	@Override
	public String visit(BinOp n) throws Exception {
		return super.visit(n);
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
		return super.visit(n);
	}

	/**
	 * Grammar production:
	 * f0 -> "TEMP"
	 * f1 -> IntegerLiteral()
	 */
	@Override
	public String visit(Temp n) throws Exception {
		return super.visit(n);
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
