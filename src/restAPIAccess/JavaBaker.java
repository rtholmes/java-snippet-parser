package restAPIAccess;

import java.io.IOException;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.json.JSONObject;

import RestAPI.GraphServerAccess;
import RestAPI.Logger;

import java.sql.SQLException;


public class JavaBaker
{
	public static void main(String args[]) throws IOException, NullPointerException, ClassNotFoundException, SQLException
	{
		long start = System.nanoTime();
		Logger logger = new Logger();
		String input_oracle = "http://gadget.cs:7474/db/data";
		String input_file = "sample.txt";
		int tolerance = 3;
		int max_cardinality = 20;
		Parser parser = new Parser(input_oracle, input_file);
		CompilationUnit cu = parser.getCompilationUnitFromFile();
		int cutype = parser.getCuType();
		GraphServerAccess graphServer = parser.getGraph();
		if(graphServer == null)
		{
			System.out.println("db locked");
		}
		
		System.out.println(vistAST(graphServer, cu, cutype, tolerance, max_cardinality).toString(3));
		
		long end = System.nanoTime();
		logger.printAccessTime("JavaBaker total run: ", " ", end, start);
		graphServer.logger.printMap();
	}


	static JSONObject vistAST(GraphServerAccess db, CompilationUnit cu, int cutype, int tolerance, int max_cardinality)
	{
		
		long startTime = System.nanoTime();
		PrefetchCandidates prefetch_visitor = new PrefetchCandidates(db,cu,cutype, tolerance, max_cardinality);
		cu.accept(prefetch_visitor);
		prefetch_visitor.classFetchExecutor.shutdown();
		prefetch_visitor.methodFetchExecutor.shutdown();
		while(prefetch_visitor.classFetchExecutor.isTerminated() == false || prefetch_visitor.methodFetchExecutor.isTerminated() == false)
		{
			
		}
		
		System.out.println("@@worked!");
		System.out.println(prefetch_visitor.candidateClassNodesCache.keySet().size() + " : " + prefetch_visitor.candidateMethodNodesCache.keySet().size());
	    System.out.println("Finished all threads");
	    long endTime = System.nanoTime();
	    double time = (double)(endTime-startTime)/(1000000000);
	    System.out.println(time);
	    
	    FirstASTVisitor first_visitor = new FirstASTVisitor(prefetch_visitor);
	    
		
		//FirstASTVisitor first_visitor = new FirstASTVisitor(db,cu,cutype, tolerance, max_cardinality);
		cu.accept(first_visitor);
		System.out.println(first_visitor.printJson().toString(3));
		first_visitor.printFields();

		SubsequentASTVisitor second_visitor = new SubsequentASTVisitor(first_visitor);
		cu.accept(second_visitor);
		//System.out.println(second_visitor.printJson().toString(3));
		//second_visitor.printFields();

		SubsequentASTVisitor third_visitor = new SubsequentASTVisitor(second_visitor);
		cu.accept(third_visitor);
		//System.out.println(third_visitor.printJson().toString(3));
		//third_visitor.printFields();

		SubsequentASTVisitor previous_visitor = second_visitor;
		SubsequentASTVisitor current_visitor = third_visitor;

		while(compareMaps(current_visitor, previous_visitor) == false)
		{
			SubsequentASTVisitor new_visitor = new SubsequentASTVisitor(current_visitor);
			cu.accept(new_visitor);
			//System.out.println(new_visitor.printJson().toString(3));
			//new_visitor.printFields();
			previous_visitor = current_visitor;
			current_visitor = new_visitor;
		}
		//current_visitor.printFields();
		current_visitor.setJson();
		return current_visitor.getJson();
	}
	
	private static boolean compareMaps(SubsequentASTVisitor curr, SubsequentASTVisitor prev) 
	{
		if(curr.variableTypeMap.equals(prev.variableTypeMap) && 
				curr.methodReturnTypesMap.equals(prev.methodReturnTypesMap) &&
				curr.printtypes.equals(prev.printtypes) &&
				curr.printmethods.equals(prev.printmethods) &&
				curr.printTypesMap.equals(prev.printTypesMap) &&
				curr.printMethodsMap.equals(prev.printMethodsMap))
			return true;
		else
			return false;
	}
}