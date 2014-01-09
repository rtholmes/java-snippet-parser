import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.json.JSONArray;
import org.json.JSONObject;

@SuppressWarnings("rawtypes")
public class DumpIterator
{

	private static void registerShutdownHook(final GraphDatabase db)
	{
		Runtime.getRuntime().addShutdownHook( new Thread()
		{
			@Override
			public void run()
			{
				db.shutdown();
			}
		} );
	}

	public static void iterateOver(Element root, Connection connection, Parser parser,final int tolerance,final int max_cardinality, final GraphDatabase db) throws NullPointerException, IOException, ClassNotFoundException, SQLException, TimeoutException
	{

		int finished = 0;
		int count = 0;

		for (Iterator i = root.elementIterator(); i.hasNext(); ) 
		{

			Statement statement = connection.createStatement();
			count ++;
			Element post = (Element) i.next();
			//System.out.println(count);
			if(count>432)
			{
				String qid = post.attributeValue("qid");
				String aid = post.attributeValue("aid");
				String code = post.element("code").getText();
				String codeid = post.element("code").attributeValue("id");


				String initcode = code;
				code = code.replace("&lt;", "<");
				code = code.replace("&gt;", ">");
				code = code.replace("&amp;", "&");
				code = code.replace("&quot;", "\"");

				System.out.println(isXMLLike(code)+ "is XML LIKE");


				final CompilationUnit cu = parser.getCompilationUnitFromString(code);
				System.out.println("----- \n" + "Actual Code: \n" );
				System.out.println(code);
				System.out.println("----- \n" + "Generated Compilation Unit: \n" );
				System.out.println(cu.toString());
				final int cutype = parser.getCuType();


				initcode = StringEscapeUtils.escapeSql(initcode);

				String other_query1 = "delete from map where aid = '"+aid+"'";
				String other_query2 = "insert into map values('"+aid+"','"+qid+"','"+codeid+"','"+initcode+"','"+cutype+"')";
				statement.executeUpdate(other_query1);
				statement.executeUpdate(other_query2);

				JSONObject op = null;
				if(cu != null)
				{
					ExecutorService service = Executors.newCachedThreadPool();
					Callable<JSONObject> call = new Callable<JSONObject>() {
						public JSONObject call() 
						{
							JSONObject jsonObject = JavaBaker.vistAST(db, cu, cutype, tolerance, max_cardinality);
							return jsonObject;
						}
					};
					Future<JSONObject> ft = service.submit(call);
					try 
					{
						op = ft.get(60, TimeUnit.SECONDS); 

					} 
					catch (TimeoutException ex)
					{
						//ft.cancel(true);
						System.out.println("Timed out");
					}
					catch (InterruptedException e) 
					{
						System.out.println("interrupted");
					} 
					catch (ExecutionException e) 
					{
						System.out.println(e.getCause().toString());
						e.printStackTrace();
					} 
				}
				if(op!=null)
				{
					System.out.println(op.toString(3));;
					finished++;
					String q1 = "delete from types where aid = '"+aid+"'";
					String q2 = "delete from methods where aid = '"+aid+"'";
					statement.executeUpdate(q1);
					statement.executeUpdate(q2);
					if (op.get("api_elements") instanceof JSONObject)
					{
						JSONObject apielements = op.getJSONObject("api_elements");
						insert(apielements, statement, aid, qid, codeid, code, Integer.toString(cutype));
					}
					else if (op.get("api_elements") instanceof JSONArray)
					{
						JSONArray apielements = op.getJSONArray("api_elements");
						for(int j=0; j < apielements.length(); j++)
						{
							JSONObject obj = (JSONObject) apielements.get(j);
							insert(obj, statement, aid, qid, codeid, code, Integer.toString(cutype));
						}
					}
				}
				System.out.println(count+ ":"+ finished + ":"+qid+":"+aid+":"+codeid);
			}
		}
	}

	@SuppressWarnings("unused")
	public static void iterate(Element root, Connection connection, Parser parser,final int tolerance,final int max_cardinality, final GraphDatabase db) throws NullPointerException, IOException, ClassNotFoundException, SQLException  
	{
		HashSet<String> lru = new HashSet<String>();
		
		lru.add("gwt");
		lru.add("apache");
		lru.add("jodatime");
		lru.add("xstream");
		lru.add("httpclient");


		int count = 0;
		int lruCounter = 0;
		int finished = 0;


		TreeSet<String> alreadyParsed = new TreeSet<String>();
		/*BufferedReader br = new BufferedReader(new FileReader("/home/s23subra/workspace/Java Snippet Parser/alreadyInDb.txt"));
		String line = null;
		while((line = br.readLine())!=null)
		{
			alreadyParsed.add(line.trim());
		}
		 */

		for (@SuppressWarnings("unchecked")
		Iterator<Element> i = root.elementIterator(); i.hasNext(); ) 
		{
			count++;
			Element post = i.next();
			Statement statement = connection.createStatement();
			if(count>0)
			{
				String qid = post.attributeValue("qid");
				String aid = post.attributeValue("aid");
				if(alreadyParsed.contains(aid) == true)
				{
					
				}
				else
				{
					String tagString = post.attributeValue("tags");
					String[] tags = tagString.split("\\|");
					String code = post.element("code").getText();
					String codeid = post.element("code").attributeValue("id");

					System.out.println(isXMLLike(code)+ "is XML LIKE");
					
					String initcode = code;
					code = code.replace("&lt;", "<");
					code = code.replace("&gt;", ">");
					code = code.replace("&amp;", "&");
					code = code.replace("&quot;", "\"");
					String code1= code;
					int breakFlag = 0;
					int tagCount = 0;
					int matchCount =0;
					for(String tag : tags)
					{
						tagCount++;
						if(lru.contains(tag))
						{
							matchCount++;
						}
					}
					//if(matchCount<1 || code1.toLowerCase().contains("eclipse")==false &&  code1.toLowerCase().contains("gwt") == false))
					if(matchCount<1 )
					//if(false)
					{
						System.out.println("matchcount not exceeded");
					}
					else
					{
						final CompilationUnit cu = parser.getCompilationUnitFromString(code);
						System.out.println(code);
						final int cutype = parser.getCuType();
						if(aid!=null && qid!=null && codeid!=null && initcode!=null)
						{
							initcode = StringEscapeUtils.escapeSql(initcode);
							String other_query1 = "delete from map where aid = '"+aid+"'";

							String other_query2 = "insert into map values('"+aid+"','"+qid+"','"+codeid+"','"+initcode+"','"+cutype+"')";
							//System.out.println(other_query2);
							//statement.executeUpdate(other_query1);
							//statement.executeUpdate(other_query2);
						}
						JSONObject op = null;
						if(cu != null)
						{
							ExecutorService service = Executors.newCachedThreadPool();
							Callable<JSONObject> call = new Callable<JSONObject>() {
								public JSONObject call() 
								{
									JSONObject jsonObject = JavaBaker.vistAST(db, cu, cutype, tolerance, max_cardinality);
									return jsonObject;
								}
							};
							Future<JSONObject> ft = service.submit(call);
							try 
							{
								op = ft.get(60, TimeUnit.SECONDS); 

							} 
							catch (TimeoutException ex)
							{
								//ft.cancel(true);
								System.out.println("Timed out");
							}
							catch (InterruptedException e) 
							{
								System.out.println("interrupted");
							} 
							catch (ExecutionException e) 
							{
								System.out.println(e.getCause().toString());
								e.printStackTrace();
							} 
						}
						if(op!=null)
						{
							System.out.println(op.toString(3));;
							finished++;
							String q1 = "delete from types where aid = '"+aid+"'";
							String q2 = "delete from methods where aid = '"+aid+"'";
							statement.executeUpdate(q1);
							statement.executeUpdate(q2);
							if (op.get("api_elements") instanceof JSONObject)
							{
								JSONObject apielements = op.getJSONObject("api_elements");
								//insert(apielements, statement, aid, qid, codeid, code, Integer.toString(cutype));
							}
							else if (op.get("api_elements") instanceof JSONArray)
							{
								JSONArray apielements = op.getJSONArray("api_elements");
								for(int j=0; j < apielements.length(); j++)
								{
									JSONObject obj = (JSONObject) apielements.get(j);
									//insert(obj, statement, aid, qid, codeid, code, Integer.toString(cutype));
								}
							}
						}

						for(int p=0; p<tags.length;p++)
						{
							//System.out.println(tags[p]);
							if(tags[p].equals("java")==false)
							{
								lru.add(tags[p]);
							}
						}
						if(lruCounter<10)
							lruCounter=0;
						else
						{
							lruCounter=0;
							lru = new HashSet<String>();
						}
						System.out.println(count+ ":"+ finished + ":"+qid+":"+aid+":"+codeid);
					}

				}
			}
		}
		//tx0.success();
		//}
		//finally
		//{
		//	tx0.finish();
		//}
	}

	private static void insert(JSONObject obj, Statement statement, String aid, String qid, String codeid, String code, String cutype) throws SQLException 
	{

		String line_no = obj.getString("line_number");
		String type = obj.getString("type");
		String character = obj.getString("character");
		ArrayList<String> elements = (ArrayList<String>) obj.get("elements");

		if(elements.size() < 30)
		{
			TreeSet<String> elements2 = new TreeSet<String>();
			for(int k =0; k< elements.size(); k++)
			{
				String element = elements.get(k);
				elements2.add(element);
			}
			//elements2 =  new ArrayList<String>(elements);
			int precision = elements2.size();
			for(String element : elements2)
			{
				//String element;
				//System.out.println(element);
				String query = null;
				if(type.equals("api_type"))
				{
					query="insert into types values('"+aid+"','"+codeid+"','"+element+"','"+character+"','"+line_no+"','"+Integer.toString(precision)+"')";
					//if(precision.equals("1"))
					//System.out.println(query);
				}
				else if(type.equals("api_method"))
				{
					query="insert into methods values('"+aid+"','"+codeid+"','"+element+"','"+character+"','"+line_no+"','"+Integer.toString(precision)+"')";
					//if(precision.equals("1"))
					//System.out.println(query);
				}
				//System.out.println(query);
				statement.executeUpdate(query);
			}
		}
	}

	private static Element getCodeXML(String fname) throws FileNotFoundException, DocumentException
	{
		FileInputStream fis = new FileInputStream(fname);
		DataInputStream in = new DataInputStream(fis);

		SAXReader reader = new SAXReader();
		Document document = reader.read(in);
		Element root = document.getRootElement();
		return root;
	}

	private static Connection getDatabase(String fname) throws ClassNotFoundException
	{
		try
		{
			Class.forName("org.sqlite.JDBC");
			Connection connection = null;
			connection = DriverManager.getConnection("jdbc:sqlite:"+fname);
			Statement statement = connection.createStatement();
			statement.setQueryTimeout(30);
			/*statement.executeUpdate("drop table if exists types");
			statement.executeUpdate("drop table if exists methods");
			statement.executeUpdate("drop table if exists map");
			statement.executeUpdate("create table types (aid string, codeid int, tname string, charat int, line int, prob int)");
			statement.executeUpdate("create table methods (aid string, codeid int, mname string, charat int, line int, prob int)");
			statement.executeUpdate("create table map (aid string, qid string, codeid int, code string, cutype int, PRIMARY KEY (aid, qid, codeid))");
			 */			return connection;
		}
		catch(SQLException e)
		{
			System.err.println(e.getMessage());
			return null;
		}
	}

	public static boolean isXMLLike(String inXMLStr) 
	{

		boolean retBool = false;
		Pattern pattern;
		Matcher matcher;

		// REGULAR EXPRESSION TO SEE IF IT AT LEAST STARTS AND ENDS
		// WITH THE SAME ELEMENT
		final String XML_PATTERN_STR = "<(\\S+?)(.*?)>(.*?)</\\1>";



		// IF WE HAVE A STRING
		if (inXMLStr != null && inXMLStr.trim().length() > 0) 
		{

			// IF WE EVEN RESEMBLE XML
			if (inXMLStr.trim().startsWith("<")) {

				pattern = Pattern.compile(XML_PATTERN_STR,
						Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

				// RETURN TRUE IF IT HAS PASSED BOTH TESTS
				matcher = pattern.matcher(inXMLStr);
				retBool = matcher.matches();
			}
			// ELSE WE ARE FALSE
		}
		
		if(inXMLStr.contains("<!--") || (inXMLStr.contains(":") && !inXMLStr.contains(";")))
		{
			retBool = true;
		}

		return retBool;
	}

	public static void main(String[] args) throws IOException, NullPointerException, ClassNotFoundException, SQLException, DocumentException, TimeoutException 
	{
		long start = System.nanoTime();
		Logger logger = new Logger();
		String input_oracle = "/home/s23subra/workspace/model-generator/maven-graph-database/";
		String input_file = "sample.txt";
		int tolerance = 3;
		int max_cardinality = 10;
		Parser parser = new Parser(input_oracle, input_file);
		GraphDatabase db = parser.getGraph();
		registerShutdownHook(db);
		if(db == null)
		{
			System.out.println("db locked");
		}

		Connection connection = getDatabase("/home/s23subra/workspace/Java Snippet Parser/javadb_rerun.db");
		Element root = getCodeXML("/home/s23subra/workspace/stackoverflow/java_codes_tags.xml");





		iterateOver(root, connection, parser, tolerance, max_cardinality, db);
		//iterate(root, connection, parser, tolerance, max_cardinality, db);



		long end = System.nanoTime();
		logger.printAccessTime("Total JavaBaker time: ", "", end, start);
	}

}
