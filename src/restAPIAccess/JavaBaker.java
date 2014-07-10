package restAPIAccess;

import java.io.IOException;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.json.JSONObject;

import Node.IndexHits;
import Node.NodeJSON;
import RestAPI.GraphServerAccess;
import RestAPI.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;


public class JavaBaker
{
	public static void main(String args[]) throws IOException, NullPointerException, ClassNotFoundException, SQLException
	{
		long start = System.nanoTime();
		Logger logger = new Logger();
		String input_oracle = "http://localhost:7474/db/data";
		String input_file = "sample.txt";
		int tolerance = 2;
		int max_cardinality = 2000;
		Parser parser = null;
		if(args.length == 0)
			parser = new Parser(input_oracle, input_file, 0);
		else if(args.length == 1)
		{
			input_file = args[0];
			parser = new Parser(input_oracle, input_file, 0);
		}
		else
			System.out.println("Invalid arguments.");
		
		CompilationUnit cu = parser.getCompilationUnitFromFile();
		int cutype = parser.getCuType();
		GraphServerAccess graphServer = parser.getGraph();
		if(graphServer == null)
		{
			System.out.println("db locked");
		}
		
		/*if(args.length == 1)
		{
			HashSet<String> retSet = new HashSet<String>();
			IndexHits<NodeJSON> methods = graphServer.getCandidateMethodNodes("getSettings", new HashMap<String, IndexHits<NodeJSON>>());
			for(NodeJSON method : methods)
			{
				NodeJSON ret = graphServer.getMethodReturn(method, new HashMap<NodeJSON, NodeJSON>());
				retSet.add(ret.getProperty("id"));
			}
			for(String s : retSet)
			{
				System.out.println(s);
			}
			System.out.println(retSet.size());
		}*/
		//else
		//{
			System.out.println(vistAST(graphServer, cu, cutype, tolerance, max_cardinality).toString(3));
		//}
		
		long end = System.nanoTime();
		//logger.printAccessTime("JavaBaker total run: ", " ", end, start);
		//graphServer.logger.printMap();
	}


	private static void doWhatISay() 
	{
		// 
		
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
		
	    long endTime = System.nanoTime();
	    double time = (double)(endTime-startTime)/(1000000000);
	    //System.out.println(time);
	    
	    FirstASTVisitor first_visitor = new FirstASTVisitor(prefetch_visitor);
	    
		
		//FirstASTVisitor first_visitor = new FirstASTVisitor(db,cu,cutype, tolerance, max_cardinality);
		cu.accept(first_visitor);
		//System.out.println(first_visitor.printJson().toString(3));
		//first_visitor.printFields();

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
		current_visitor.updateBasedOnImports();
		//current_visitor.removeClustersIfAny();
		current_visitor.setJson();
		
		return current_visitor.getJson();
		//org.apache.http.impl.client.DefaultHttpClient, org.apache.http.impl.client.*, 
		//org.apache.http.protocol.BasicHttpContext, 
		//org.apache.http.client.*, org.apache.http.protocol.*, org.apache.http.impl.client.BasicCookieStore]
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