import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;


import org.eclipse.jdt.core.dom.ASTVisitor;


import com.google.common.collect.HashMultimap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.neo4j.graphdb.Node;

import sun.reflect.ReflectionFactory.GetReflectionFactoryAction;

class SubsequentASTVisitor extends ASTVisitor
{
	public HashMap<Node, Node> methodContainerCache;
	public HashMap<Node, Node> methodReturnCache;
	public HashMap<String, ArrayList<Node>> candidateClassNodesCache;
	public HashMap<String, ArrayList<Node>> candidateMethodNodesCache;
	public HashMap<Node, ArrayList<Node>> methodParameterCache;
	public HashMap<String, ArrayList<Node>> parentNodeCache;
	
	
	public GraphDatabase model;
	public CompilationUnit cu;
	public int cutype;
	public HashMap<String, HashMultimap<ArrayList<Integer>,Node>> methodReturnTypesMap;
	public HashMap<String, HashMultimap<ArrayList<Integer>,Node>> variableTypeMap;//holds variables, fields and method param types
	public HashMultimap<Integer, Node> printtypes;//holds node start loc and possible types
	public HashMultimap<Integer, Node> printmethods;//holds node start posns and possible methods they can be
	public HashMap<String, Integer> printTypesMap;//maps node start loc to variable names
	public HashMap<String, Integer> printMethodsMap;//holds node start locs with method names
	public Set<String> importList;
	public Stack<String> classNames;
	public String superclassname;
	public ArrayList<Object> interfaces;
	public int tolerance;
	public int MAX_CARDINALITY;
	private HashMultimap<String, String> localMethods;
	private JSONObject json;
	public void printFields()
	{
		System.out.println("methodReturnTypesMap: " + methodReturnTypesMap);
		System.out.println("variableTypeMap: " + variableTypeMap);
		System.out.println("printtypes: " + printtypes);
		System.out.println("printmethods: " + printmethods);
		System.out.println("printTypesMap: " + printTypesMap);
		System.out.println("printMethodsMap: " + printMethodsMap);
		System.out.println("possibleImportList: " + importList);
		System.out.println("localMethods: " + localMethods);
	}

	public SubsequentASTVisitor(FirstASTVisitor previousVisitor) 
	{
		model = previousVisitor.model;
		cu = previousVisitor.cu;
		cutype = previousVisitor.cutype;
		variableTypeMap = new HashMap<String, HashMultimap<ArrayList<Integer>,Node>>(previousVisitor.variableTypeMap);
		methodReturnTypesMap = new HashMap<String, HashMultimap<ArrayList<Integer>,Node>>(previousVisitor.methodReturnTypesMap);
		printtypes = HashMultimap.create(previousVisitor.printtypes);
		printmethods = HashMultimap.create(previousVisitor.printmethods);
		printTypesMap = new HashMap<String, Integer>(previousVisitor.printTypesMap);
		printMethodsMap = new HashMap<String, Integer>(previousVisitor.printMethodsMap);
		importList = new HashSet<String>(previousVisitor.importList);
		classNames = previousVisitor.classNames;
		superclassname = new String(previousVisitor.superclassname);
		interfaces = new ArrayList<Object>(previousVisitor.interfaces);
		methodContainerCache = new HashMap<Node, Node>(previousVisitor.methodContainerCache);
		methodReturnCache = new HashMap<Node, Node>(previousVisitor.methodReturnCache);
		parentNodeCache = new HashMap<String, ArrayList<Node>>(previousVisitor.parentNodeCache);
		//parentNodeCache = new HashMap<String, ArrayList<Node>>();
		tolerance = previousVisitor.tolerance;
		MAX_CARDINALITY = previousVisitor.MAX_CARDINALITY;
		localMethods = HashMultimap.create(previousVisitor.localMethods);
		removeClustersIfAny();
		updateImports();
		upDateBasedOnImports();
	}
	public SubsequentASTVisitor(SubsequentASTVisitor previousVisitor) 
	{
		model = previousVisitor.model;
		cu = previousVisitor.cu;
		cutype = previousVisitor.cutype;
		variableTypeMap = new HashMap<String, HashMultimap<ArrayList<Integer>,Node>>(previousVisitor.variableTypeMap);
		methodReturnTypesMap = new HashMap<String, HashMultimap<ArrayList<Integer>,Node>>(previousVisitor.methodReturnTypesMap);
		printtypes = HashMultimap.create(previousVisitor.printtypes);
		printmethods = HashMultimap.create(previousVisitor.printmethods);
		printTypesMap = new HashMap<String, Integer>(previousVisitor.printTypesMap);
		printMethodsMap = new HashMap<String, Integer>(previousVisitor.printMethodsMap);
		importList = new HashSet<String>(previousVisitor.importList);
		classNames = previousVisitor.classNames;
		superclassname = new String(previousVisitor.superclassname);
		interfaces = new ArrayList<Object>(previousVisitor.interfaces);
		methodContainerCache = new HashMap<Node, Node>(previousVisitor.methodContainerCache);
		methodReturnCache = new HashMap<Node, Node>(previousVisitor.methodReturnCache);
		parentNodeCache = new HashMap<String, ArrayList<Node>>(previousVisitor.parentNodeCache);
		tolerance = previousVisitor.tolerance;
		MAX_CARDINALITY = previousVisitor.MAX_CARDINALITY;
		localMethods = HashMultimap.create(previousVisitor.localMethods);
		removeClustersIfAny();
		updateImports();
		upDateBasedOnImports();
	}
	
	public void updateImports()
	{
		for(Integer key : printtypes.keySet())
		{
			if(printtypes.get(key).size() < tolerance)
			{
				Set<Node> temp = printtypes.get(key);
				for(Node node : temp)
				{
					importList.add(getCorrespondingImport((String)node.getProperty("id")));
				}
			}
		}
		
		for(Integer key : printmethods.keySet())
		{
			HashSet<String> repl = new HashSet<String>();
			Set<Node> temp = printmethods.get(key);
			for(Node node : temp)
			{
				Node parent = model.getMethodContainer(node, methodContainerCache);
				repl.add((String) parent.getProperty("id"));
			}
			if(repl.size() < tolerance)
			{
				importList.addAll(repl);
			}
		}
	}
	
	private void upDateBasedOnImports()
	{
		//Update variableTypeMap to hold only a possible import if one exists. Else leave untouched.
		HashMultimap<Integer, Node> tempprinttypes = HashMultimap.create();
		for(Integer key : printtypes.keySet())
		{
			Set<Node> list = printtypes.get(key);
			HashSet<Node> newList = getNewClassElementsList(list);
			if(!newList.isEmpty())
			{
				tempprinttypes.putAll(key, newList);
			}
			else
			{
				tempprinttypes.putAll(key, list);
			}
		}
		printtypes = tempprinttypes;
		
		
		HashMultimap<Integer, Node> tempprintmethods = HashMultimap.create();
		for(Integer key : printmethods.keySet())
		{
			Set<Node> list = printmethods.get(key);
			HashSet<Node> temp = new HashSet<Node>();
			for(Node method : list)
			{
				temp.add(model.getMethodContainer(method, methodContainerCache));
			}
			
			HashSet<Node> replacementList = getNewClassElementsList(temp);
			
			HashSet<Node> newList = new HashSet<Node>();
			for(Node method : list)
			{
				if(replacementList.contains(model.getMethodContainer(method, methodContainerCache)))
				{
					newList.add(method);
				}
			}
			
			if(!newList.isEmpty())
			{
				tempprintmethods.putAll(key, newList);
			}
			else
			{
				tempprintmethods.putAll(key, list);
			}
		}
		printmethods = tempprintmethods;
	}
	
	private HashSet<Node> getNewClassElementsList(Set<Node> candidateClassNodes)
	{
		HashSet<Node> templist = new HashSet<Node>();
		int flagVar2 = 0;
		int flagVar3 = 0;
		for(Node ce: candidateClassNodes)
		{
			String name = (String) ce.getProperty("id");
			int flagVar1 = 0;
			if(importList.isEmpty() == false)
			{
				for(String importItem : importList)
				{
					if(importItem != null)
					{
						if(importItem.contains(".*"))
						{
							importItem = importItem.substring(0, importItem.indexOf(".*"));
						}
						if(name.startsWith(importItem) || name.startsWith("java.lang"))
						{
							templist.clear();
							templist.add(ce);
							flagVar1 = 1;
							break;
						}
					}
				}
			}
			if(flagVar1==1)
				break;
			//else if(name.startsWith("java."))
			else if(false)
			{
				if(flagVar2==0)
				{
					templist.clear();
					flagVar2 =1;
				}
				templist.add(ce);
				flagVar3 = 1;
			}
			else
			{
				if(flagVar3 == 0)
					templist.add(ce);
			}
		}
		return templist;
	}

	private ArrayList<Integer> getScopeArray(ASTNode treeNode)
	{
		ASTNode parentNode;
		ArrayList<Integer> parentList = new ArrayList<Integer>();
		while((parentNode =treeNode.getParent())!=null)
		{
			parentList.add(parentNode.getStartPosition());
			treeNode = parentNode;
		}
		return parentList;
	}

	public boolean isLocalMethod(String methodName)
	{
		return false;
	}

	public void endVisit(MethodInvocation treeNode)
	{
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		Expression expression=treeNode.getExpression();
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getName().getStartPosition();
		if(expression==null)
		{
			HashMultimap<ArrayList<Integer>, Node> temporaryMap2 = methodReturnTypesMap.get(treeNodeString);
			if(temporaryMap2 == null)
				return;
			ArrayList<Integer> rightScopeArray2 = getNodeSet(temporaryMap2, scopeArray);
			if(rightScopeArray2 == null)
				return;
			Set<Node> candidateReturnNodes = temporaryMap2.get(rightScopeArray2);
			Set<Node> currentMethods = printmethods.get(startPosition);
			
			Set<Node> newMethodNodes = new HashSet<Node>();
			Set<Node> newReturnNodes = new HashSet<Node>();
			for(Node method : currentMethods)
			{
				Node returnNode = model.getMethodReturn(method, methodReturnCache);
				if(candidateReturnNodes.contains(returnNode) == true)
				{
					newMethodNodes.add(method);
					newReturnNodes.add(returnNode);
				}
			}
			printmethods.removeAll(startPosition);
			printmethods.putAll(startPosition, newMethodNodes);
			temporaryMap2.removeAll(rightScopeArray2);
			temporaryMap2.putAll(rightScopeArray2, newReturnNodes);
		}
		else if(expression.toString().contains("System."))
		{
			
		}
		else if(expression.getNodeType() == 2)
		{
		}
		else if(variableTypeMap.containsKey(expression.toString()))
		{
			HashMultimap<ArrayList<Integer>, Node> temporaryMap1 = variableTypeMap.get(expression.toString());
			if(temporaryMap1 == null)
				return;
			ArrayList<Integer> rightScopeArray1 = getNodeSet(temporaryMap1, scopeArray);
			if(rightScopeArray1 == null)
				return;
			Set<Node> candidateClassNodes = temporaryMap1.get(rightScopeArray1);
			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			HashMultimap<ArrayList<Integer>, Node> temporaryMap2 = methodReturnTypesMap.get(treeNodeString);
			if(temporaryMap2 == null)
				return;
			ArrayList<Integer> rightScopeArray2 = getNodeSet(temporaryMap2, scopeArray);
			if(rightScopeArray2 == null)
				return;
			Set<Node> candidateReturnNodes = temporaryMap2.get(rightScopeArray2);
			Set<Node> currentMethods = printmethods.get(startPosition);
			
			Set<Node> newMethodNodes = new HashSet<Node>();
			Set<Node> newReturnNodes = new HashSet<Node>();
			Set<Node> newClassNodes = new HashSet<Node>();
			for(Node method : currentMethods)
			{
				//System.out.println("here--");
				Node returnNode = model.getMethodReturn(method, methodReturnCache);
				Node parentNode = model.getMethodContainer(method, methodContainerCache);
				if(candidateClassNodes.contains(parentNode) == true && candidateReturnNodes.contains(returnNode) == true)
				{
					//System.out.println("here too -----");
					newMethodNodes.add(method);
					newReturnNodes.add(returnNode);
					newClassNodes.add(parentNode);
				}
			}
			
			if(newClassNodes.size() < tolerance)
			{
				for(Node newClassNode : newClassNodes)
				{
					String possibleImport = getCorrespondingImport(newClassNode.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
			}
			temporaryMap1.removeAll(rightScopeArray1);
			temporaryMap1.putAll(rightScopeArray1, newClassNodes);
			printmethods.removeAll(startPosition);
			printmethods.putAll(startPosition, newMethodNodes);
			temporaryMap2.removeAll(rightScopeArray2);
			temporaryMap2.putAll(rightScopeArray2, newReturnNodes);
		}
		else if(methodReturnTypesMap.containsKey(expression.toString()))
		{
			HashMultimap<ArrayList<Integer>, Node> temporaryMap1 = methodReturnTypesMap.get(expression.toString());
			if(temporaryMap1 == null)
				return;
			ArrayList<Integer> rightScopeArray1 = getNodeSet(temporaryMap1, scopeArray);
			if(rightScopeArray1 == null)
				return;
			Set<Node> candidateClassNodes = temporaryMap1.get(rightScopeArray1);
			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			
			HashMultimap<ArrayList<Integer>, Node> temporaryMap2 = methodReturnTypesMap.get(treeNodeString);
			if(temporaryMap2 == null)
				return;
			ArrayList<Integer> rightScopeArray2 = getNodeSet(temporaryMap2, scopeArray);
			if(rightScopeArray2 == null)
				return;
			Set<Node> candidateReturnNodes = temporaryMap2.get(rightScopeArray2);
			//System.out.println("candidateReturnNodes " + scopeArray + candidateReturnNodes);
			Set<Node> currentMethods = printmethods.get(startPosition);
			//System.out.println("currentMethods " + currentMethods);
			Set<Node> newMethodNodes = new HashSet<Node>();
			Set<Node> newReturnNodes = new HashSet<Node>();
			Set<Node> newClassNodes = new HashSet<Node>();
			
			for(Node method : currentMethods)
			{
				//System.out.println("here -- ");
				Node returnNode = model.getMethodReturn(method, methodReturnCache);
				Node parentNode = model.getMethodContainer(method, methodContainerCache);
				if(candidateClassNodes.contains(parentNode) == true && candidateReturnNodes.contains(returnNode) == true)
				{
					//System.out.println("-- here too");
					newMethodNodes.add(method);
					newReturnNodes.add(returnNode);
					newClassNodes.add(parentNode);
				}
			}
			if(newClassNodes.size() < tolerance)
			{
				for(Node newClassNode : newClassNodes)
				{
					String possibleImport = getCorrespondingImport(newClassNode.getProperty("id").toString());
					if(possibleImport!=null)
						importList.add(possibleImport);
				}
			}
			temporaryMap1.removeAll(rightScopeArray1);
			temporaryMap1.putAll(rightScopeArray1, newClassNodes);
			printmethods.removeAll(startPosition);
			printmethods.putAll(startPosition, newMethodNodes);
			temporaryMap2.removeAll(rightScopeArray2);
			temporaryMap2.putAll(rightScopeArray2, newReturnNodes);
		}
	}

	private String getCorrespondingImport(String classID) 
	{
		int loc = classID.indexOf('.');
		if(loc == -1)
			return null;
		else
		{
			return(classID.substring(0, classID.lastIndexOf("."))+".*") ;
		}
	}

	private ArrayList<Integer> getNodeSet(HashMultimap<ArrayList<Integer>, Node> celist2, ArrayList<Integer> scopeArray) 
	{
		for(ArrayList<Integer> test : celist2.keySet())
		{
			if(isSubset(test, scopeArray))
				return test;
		}
		return null;
	}

	private boolean isSubset(ArrayList<Integer> test,ArrayList<Integer> scopeArray) 
	{
		if(scopeArray.containsAll(test))
			return true;
		/*else if(scopeArray.containsAll(test.subList(1, test.size())))
			return true;*/
		else
			return false;
	}

	public void endVisit(ConstructorInvocation treeNode)
	{	
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getStartPosition();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		
		Set<Node> candidateReturnNodes = methodReturnTypesMap.get(treeNodeString).get(scopeArray);
		
		Set<Node> currentMethods = printmethods.get(startPosition);
		
		Set<Node> newMethodNodes = new HashSet<Node>();
		
		for(Node method : currentMethods)
		{
			Node returnNode = model.getMethodReturn(method, methodReturnCache);
			if(candidateReturnNodes.contains(returnNode) == true)
			{
				newMethodNodes.add(method);
			}
		}
		printmethods.removeAll(startPosition);
		printmethods.putAll(startPosition, newMethodNodes);
	}

	public void endVisit(SuperConstructorInvocation treeNode)
	{	
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getStartPosition();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		
		Set<Node> candidateReturnNodes = methodReturnTypesMap.get(treeNodeString).get(scopeArray);
		
		Set<Node> currentMethods = printmethods.get(startPosition);
		
		Set<Node> newMethodNodes = new HashSet<Node>();
		
		for(Node method : currentMethods)
		{
			Node returnNode = model.getMethodReturn(method, methodReturnCache);
			if(candidateReturnNodes.contains(returnNode) == true)
			{
				newMethodNodes.add(method);
			}
		}
		printmethods.removeAll(startPosition);
		printmethods.putAll(startPosition, newMethodNodes);
	}

	public void endVisit(SuperMethodInvocation treeNode)
	{
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getStartPosition();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		//System.out.println(treeNodeString);
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			Set<Node> candidateReturnNodes = methodReturnTypesMap.get(treeNodeString).get(scopeArray);
			
			Set<Node> currentMethods = printmethods.get(startPosition);
			
			Set<Node> newMethodNodes = new HashSet<Node>();
			
			for(Node method : currentMethods)
			{
				Node returnNode = model.getMethodReturn(method, methodReturnCache);
				if(candidateReturnNodes.contains(returnNode) == true)
				{
					newMethodNodes.add(method);
				}
			}
			printmethods.removeAll(startPosition);
			printmethods.putAll(startPosition, newMethodNodes);
		}
	}

	public void removeClustersIfAny()
	{
		HashMultimap<Integer, Node> tempprinttypes = HashMultimap.create();
		HashMultimap<Integer, Node> tempprintmethods = HashMultimap.create();
		for(Integer key : printtypes.keySet())
		{
			Node node = model.returnRightNodeIfCluster(printtypes.get(key));
			if(node != null)
			{
				tempprinttypes.put(key, node);
			}
			else
				tempprinttypes.putAll(key, printtypes.get(key));
		}
		for(Integer key : printmethods.keySet())
		{
			Set<Node> parentSet = new HashSet<Node>();
			for(Node method : printmethods.get(key))
			{
				Node parent = model.getMethodContainer(method, methodContainerCache);
				parentSet.add(parent);
			}
			Node node = model.returnRightNodeIfCluster(parentSet);
			if(node != null)
			{
				for(Node method : printmethods.get(key))
				{
					Node parent = model.getMethodContainer(method, methodContainerCache);
					if(parent.equals(node))
					{
						tempprintmethods.put(key, method);
					}
				}
				if(!tempprintmethods.containsKey(key))
					tempprintmethods.putAll(key, printmethods.get(key));
			}
			else
			{
				tempprintmethods.putAll(key, printmethods.get(key));
			}
		}
		
		printtypes = tempprinttypes;
		printmethods = tempprintmethods;
		
	}
	
	public void setJson()
	{
		checkForNull();
		
		//Add to primitive and uncomment to remove unwanted elements
		String[] primitive = {"int","float","char","long","boolean","String","byte[]","String[]","int[]","float[]","char[]","long[]","byte"};
		//String[] primitive={};

		JSONObject main_json=new JSONObject();
		
		
		for(Integer key : printtypes.keySet())
		{
			int flag = 0;
			String cname = null;
			ArrayList<String> namelist = new ArrayList<String>();
			if(printtypes.get(key).size() < MAX_CARDINALITY)
			{
				Set<Node> prunedValueSet = removeInheritedRetainParentType(printtypes.get(key));
				for(Node type_name:prunedValueSet)
				{
					int isprimitive=0;
					for(String primitive_type : primitive)
					{
						if(((String)type_name.getProperty("id")).equals(primitive_type) == true)
						{
							isprimitive = 1;
							break;
						}
					}
					if(isprimitive == 0)
					{
						String nameOfClass = (String)type_name.getProperty("id");
						nameOfClass = JSONObject.quote(nameOfClass);
						namelist.add("\""+nameOfClass+"\"");
						if(flag == 0)
						{
							cname = (String) type_name.getProperty("exactName");
							flag = 1;
						}
					}
	
				}
				if(namelist.isEmpty() == false)
				{
					JSONObject json = new JSONObject();
					json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
					json.accumulate("precision", Integer.toString(namelist.size()));
					json.accumulate("name",cname);
					json.accumulate("elements",namelist);
					json.accumulate("type","api_type");
					json.accumulate("character", Integer.toString(key));
					main_json.accumulate("api_elements", json);
				}
				
			}
		}
		
		for(Integer key : printmethods.keySet())
		{
			ArrayList<String> namelist = new ArrayList<String>();
			String mname = null;
			if(printmethods.get(key).size() < MAX_CARDINALITY)
			{
				Set<Node> prunedValueSet = removeInheritedRetainParentMethod(printmethods.get(key));
				for(Node method_name : prunedValueSet)
				{
					String nameOfMethod = (String)method_name.getProperty("id");
					nameOfMethod = JSONObject.quote(nameOfMethod);
					namelist.add("\""+nameOfMethod+"\"");
					mname=(String) method_name.getProperty("exactName");
				}
				
				
				if(namelist.isEmpty() == false)
				{
					JSONObject json = new JSONObject();
					json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
					json.accumulate("precision", Integer.toString(namelist.size()));
					json.accumulate("name",mname);
					json.accumulate("elements",namelist);
					json.accumulate("type","api_method");
					json.accumulate("character", Integer.toString(key));
					main_json.accumulate("api_elements", json);
				}
			}
		}
		if(main_json.isNull("api_elements"))
		{
			String emptyJSON = "{\"api_elements\": []}" ;
			JSONObject ret = new JSONObject();
			try 
			{
				ret = new JSONObject(emptyJSON);
			} 
			catch (ParseException e) 
			{
				e.printStackTrace();
			}
			this.json = ret;
		}
		else
		{
			this.json = main_json;
		}
	}
	private Set<Node> removeInheritedRetainParentMethod(Set<Node> set) 
	{
		Collection<Node> removeSet = new ArrayList<Node>();
		for(Node parent : set)
		{
			Node pclass = model.getMethodContainer(parent, methodContainerCache);
			for(Node child : set)
			{
				Node cclass = model.getMethodContainer(child, methodContainerCache);
				if(!pclass.equals(cclass))
				{
					if(model.checkIfParentNode(pclass, (String)cclass.getProperty("id"), parentNodeCache))
						removeSet.add(child);
				}
			}
		}
		for(Node s : removeSet)
		{
			//System.out.println(s.getProperty("id"));
		}
		set.removeAll(removeSet);
		return set;
	}

	
	private Set<Node> removeInheritedRetainParentType(Set<Node> set) 
	{
		Collection<Node> removeSet = new ArrayList<Node>();
		for(Node parent : set)
		{
			for(Node child : set)
			{
				if(!parent.equals(child))
				{
					if(model.checkIfParentNode(parent, (String)child.getProperty("id"), parentNodeCache))
						removeSet.add(child);
				}
			}
		}
		for(Node s : removeSet)
		{
			//System.out.println(s.getProperty("id"));
		}
		set.removeAll(removeSet);
		return set;
	}
	
	public JSONObject getJson()
	{
		if (this.json.get("api_elements") instanceof JSONObject)
		{
			return sortJSON(this.json);
		}
		else if (this.json.get("api_elements") instanceof JSONArray)
		{
			JSONArray elements = (JSONArray) this.json.get("api_elements");
			if(elements.length() == 0)
			{
				return this.json;
			}
			else
			{
				return sortJSON(this.json);
			}
		}
		else
			return this.json;
	}

	private JSONObject sortJSON(JSONObject json) 
	{
		Comparator<JSONObject> comprator = new Comparator<JSONObject>() {
			@Override
			public int compare(JSONObject o1, JSONObject o2)
			{
				if(o1.getInt("character") != o2.getInt("character"))
					return(o1.getInt("character") - o2.getInt("character"));
				else
				{
					return 1;
				}
			}
		};
		TreeSet<JSONObject> set = new TreeSet<JSONObject>(comprator);
		//System.out.println(json.toString(2));
		if(json.get("api_elements") instanceof JSONArray)
		{
			JSONArray array = (JSONArray) json.get("api_elements");
			for(int i=0; i<array.length(); i++)
			{
				JSONObject entry = (JSONObject) array.get(i);
				set.add(entry);
			}
		}
		else if(json.get("api_elements") instanceof JSONObject)
		{
			JSONObject array = (JSONObject) json.get("api_elements");
			set.add(array);
		}
		JSONObject newObj = new JSONObject();
		for(JSONObject obj : set)
		{
			newObj.accumulate("api_elements", obj);
		}
		
		return newObj;
	}

	public void checkForNull()
	{
		printtypes.removeAll(null);
		printmethods.removeAll(null);
		for(Integer key : printtypes.keySet())
		{
			for(Node type_name : printtypes.get(key))
			{
				if(type_name==null)
					printtypes.remove(key, type_name);
			}
		}
		
		for(Integer key : printmethods.keySet())
		{
			for(Node method_name : printmethods.get(key))
			{
				if(method_name==null)
					printmethods.remove(key, method_name);
			}
		}
	}
}