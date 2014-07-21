import dataflow.ControlFlowGraph;
import dataflow.KangaTranslator;
import dataflow.PopulateControlFlowGraph;
import exception.MyException;
import parser.ParseException;
import parser.SpigletParser;
import syntaxtree.Goal;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by ek on 7/2/14.
 */
public class Driver {
	private static FileInputStream stream = null;
	private static SpigletParser parser;
	private static Goal tree;
	private static String target, kanga;
	private static int dotIndex;
	private static PrintWriter out;
	private static ControlFlowGraph cfg;

	public static void main(String[] args) {
		for(String arg : args) {
			try {
				System.out.println("Trying \'" + arg + "\' ...");
				if((dotIndex = arg.lastIndexOf(".spg")) == -1)
					throw new MyException("invalid file type, \'.spg\' expected.");

				target = arg.substring(0, dotIndex) + ".kg";
				System.out.println("Output set to \'" + target + "\'.");
				stream = new FileInputStream(arg);
				parser = new SpigletParser(stream);
				tree = parser.Goal();
				cfg = new ControlFlowGraph();
				tree.accept(new PopulateControlFlowGraph(cfg));
				cfg.compute();
				kanga = tree.accept(new KangaTranslator(cfg));
				System.out.println(kanga);

				out = new PrintWriter(target);
				out.print(kanga);
				out.flush();
				out.close();

			} catch (FileNotFoundException e) {
				System.err.println(e.getMessage());
			} catch (ParseException e) {
				System.err.println(e.getMessage());
			} catch (MyException e) {
				System.err.println(e.getMessage());
				//e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					if(stream != null)
						stream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
