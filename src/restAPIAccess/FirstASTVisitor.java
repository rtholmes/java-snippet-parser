package restAPIAccess;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;


import org.eclipse.jdt.core.dom.ASTVisitor;


import Node.IndexHits;
import Node.NodeJSON;
import RestAPI.GraphServerAccess;

import com.google.common.collect.HashMultimap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.json.JSONObject;

@SuppressWarnings("unchecked")
class FirstASTVisitor extends ASTVisitor
{
	public GraphServerAccess model;
	public CompilationUnit cu;
	public int cutype;
	
	public HashMap<String, IndexHits<NodeJSON>> candidateClassNodesCache;
	public HashMap<String, IndexHits<NodeJSON>> candidateMethodNodesCache;
	public HashMap<NodeJSON, NodeJSON> methodContainerCache;
	public HashMap<NodeJSON, NodeJSON> methodReturnCache;
	public HashMap<NodeJSON, ArrayList<NodeJSON>> methodParameterCache;
	public HashMap<String, ArrayList<NodeJSON>> parentNodeCache;
	
	public HashMultimap<String, String> localMethods;
	
	public HashMap<String, HashMultimap<ArrayList<Integer>,NodeJSON>> methodReturnTypesMap;
	public HashMap<String, HashMultimap<ArrayList<Integer>,NodeJSON>> variableTypeMap;//holds variables, fields and method param types
	public HashMultimap<Integer, NodeJSON> printtypes;//holds node start loc and possible types
	public HashMultimap<Integer, NodeJSON> printmethods;//holds node start posns and possible methods they can be
	public HashMap<String, Integer> printTypesMap;//maps node start loc to variable names
	public HashMap<String, Integer> printMethodsMap;//holds node start locs with method names
	
	public Set<String> importList;
	public Stack<String> classNames; 
	public String superclassname;
	public ArrayList<Object> interfaces;
	public int tolerance;
	public int MAX_CARDINALITY;

	public FirstASTVisitor(GraphServerAccess db, CompilationUnit cu, int cutype, int tolerance, int max_cardinality) 
	{
		this.model = db;
		this.cu = cu;
		this.cutype = cutype;
		this.tolerance = tolerance;
		MAX_CARDINALITY = max_cardinality;
		initializeFields();
		fetchLocalMethods(cu);
	}

	private void fetchLocalMethods(CompilationUnit cu) 
	{
		cu.accept(new ASTVisitor() {
			Stack<String> classNames = new Stack<String>(); 
			public boolean visit(TypeDeclaration treeNode)
			{
				classNames.push(treeNode.getName().toString());
				return true;
			}
			
			public boolean visit(final ClassInstanceCreation treeNode)
			{
				ASTNode anon = treeNode.getAnonymousClassDeclaration();
				if(anon!=null)
				{
					anon.accept(new ASTVisitor(){
						public void endVisit(MethodDeclaration methodNode)
						{
							String className = treeNode.getType().toString();
							String methodName = methodNode.getName().toString();
							localMethods.put(methodName, className);
							//System.out.println("-- -- " + className + "  :  " + methodName);
						}
					});
				}
				return true;
			}
			
			public void endVisit(TypeDeclaration treeNode)
			{
				classNames.pop();
			}
			
			public boolean visit(MethodDeclaration treeNode)
			{
				String methodName = treeNode.getName().toString();
				//System.out.println("-- -- " + classNames.peek() + "  :  " + methodName);
				localMethods.put(methodName, classNames.peek());
				return true;
			}
		});
	}

	private void initializeFields()
	{
		localMethods = HashMultimap.create();
		variableTypeMap = new HashMap<String, HashMultimap<ArrayList<Integer>,NodeJSON>>();
		methodReturnTypesMap = new HashMap<String, HashMultimap<ArrayList<Integer>,NodeJSON>>();
		printtypes = HashMultimap.create();
		printmethods = HashMultimap.create();
		printTypesMap = new HashMap<String, Integer>();
		printMethodsMap = new HashMap<String, Integer>();
		importList = new HashSet<String>();
		
		candidateClassNodesCache = new HashMap<String, IndexHits<NodeJSON>>();
		candidateMethodNodesCache = new HashMap<String, IndexHits<NodeJSON>>();
		methodContainerCache = new HashMap<NodeJSON, NodeJSON>();
		methodReturnCache = new HashMap<NodeJSON, NodeJSON>();
		methodParameterCache = new HashMap<NodeJSON, ArrayList<NodeJSON>>();
		parentNodeCache = new HashMap<String, ArrayList<NodeJSON>>();
		
		importList = new HashSet<String>();
		classNames = new Stack<String>();
		superclassname = new String();
		interfaces = new ArrayList<Object>();
	}
	
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
	
	private ArrayList<NodeJSON> getNewClassElementsList(ArrayList<NodeJSON> celist)
	{
		ArrayList<NodeJSON> templist = new ArrayList<NodeJSON>();
		int flagVar2 = 0;
		int flagVar3 = 0;
		for(NodeJSON ce: celist)
		{
			String name = (String) ce.getProperty("id");
			int flagVar1 = 0;
			if(importList.isEmpty() == false)
			{
				for(String importItem : importList)
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
						//break;
					}
				}
			}
			if(flagVar1==1)
				break;
			//else if(name.startsWith("java.") || name.startsWith("javax."))
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

	private ArrayList<Integer> getScopeArray(ASTNode treeNode)
	{
		ASTNode parentNode;
		ArrayList<Integer> parentList = new ArrayList<Integer>();
		while((parentNode =treeNode.getParent())!=null)
		{
			//do not include parenthesised type nodes in the list.
			if(parentNode.getNodeType() != 36)
				parentList.add(parentNode.getStartPosition());
			/*else
				System.out.println("paranthesised");*/
			treeNode = parentNode;
		}
		return parentList;
	}

	public boolean visit(VariableDeclarationStatement treeNode)
	{
		ArrayList<Integer> variableScopeArray = getScopeArray(treeNode);
		String treeNodeType = treeNode.getType().toString();
		if(treeNode.getType().getNodeType() == 74)
			treeNodeType = ((ParameterizedType)treeNode.getType()).getType().toString();
		//System.out.println(" +++ "+treeNodeJSON.getType().getNodeJSONType() + " : " + treeNodeJSONType);
		
		for(int j=0; j < treeNode.fragments().size(); j++)
		{
			HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
			String variableName = ((VariableDeclarationFragment)treeNode.fragments().get(j)).getName().toString();
			int startPosition = treeNode.getType().getStartPosition();
			if(variableTypeMap.containsKey(variableName))
			{
				candidateAccumulator = variableTypeMap.get(variableName);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}

			ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(treeNodeType, candidateClassNodesCache);

			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			for(NodeJSON candidateClass : candidateClassNodes)
			{
				//System.out.println(candidateClass.getProperty("id"));
				candidateAccumulator.put(variableScopeArray, candidateClass);
				if(candidateClassNodes.size() < tolerance)
				{
					String possibleImport = getCorrespondingImport(candidateClass.getProperty("id").toString());
					if(possibleImport!=null)
						importList.add(possibleImport);
				}
				printtypes.put(startPosition, candidateClass);
				printTypesMap.put(variableName, startPosition);
			}
			variableTypeMap.put(variableName, candidateAccumulator);
		}
		return true;
	}

	public boolean visit(EnhancedForStatement treeNode)
	{
		ArrayList<Integer> variableScopeArray = getScopeArray(treeNode.getParent());
		HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
		String variableType = treeNode.getParameter().getType().toString();
		if(treeNode.getParameter().getType().getNodeType() == 74)
			variableType = ((ParameterizedType)treeNode.getParameter().getType()).getType().toString();
		String variableName = treeNode.getParameter().getName().toString();
		if(variableTypeMap.containsKey(treeNode.getParameter().getName().toString()))
		{
			candidateAccumulator = variableTypeMap.get(variableName);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		ArrayList<NodeJSON> candidateClassNodes=model.getCandidateClassNodes(variableType, candidateClassNodesCache);
		candidateClassNodes = getNewClassElementsList(candidateClassNodes);
		for(NodeJSON candidateClass : candidateClassNodes)
		{
			int startPosition = treeNode.getParameter().getType().getStartPosition();
			candidateAccumulator.put(variableScopeArray, candidateClass);
			if(candidateClassNodes.size() < tolerance)
			{
				String possibleImport = getCorrespondingImport(candidateClass.getProperty("id").toString());
				if(possibleImport!=null)
				{
					importList.add(possibleImport);
				}
			}
			printtypes.put(startPosition, candidateClass);
			printTypesMap.put(variableName, startPosition);
		}
		variableTypeMap.put(variableName, candidateAccumulator);
		return true;
	}

	public void endVisit(ForStatement node)
	{
		/*for(int j=0;j<node.initializers().size();j++)
		{
			//System.out.println(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString() + " : "+getScopeArray(node).toString() + "$$$$$");
			ArrayList<Integer> scopeArray = getScopeArray(node);
			HashMultimap<ArrayList<Integer>, NodeJSON> temp = null;
			if(globaltypes2.containsKey(((VariableDeclarationFragment)node.initializers().get(j)).getName().toString()))
			{
				temp = globaltypes2.get(((VariableDeclarationFragment)node.initializers().get(j)).getName().toString());
			}
			else
			{
				temp = HashMultimap.create();
			}
			Collection<NodeJSON> celist=
getCandidateClassNodes(((VariableDeclarationFragment)node.initializers().get(j)).getType().toString());
			celist = getNewCeList(celist);
			for(NodeJSON ce : celist)
			{
						temp.put(scopeArray, ce);
						printtypes.put(((VariableDeclarationFragment)node.initializers().get(j)).getStartPosition(), ce);
						printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
			}
			globaltypes2.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), temp);
		}*/
	}

	public void endVisit(FieldDeclaration treeNode) 
	{
		int startPosition = treeNode.getType().getStartPosition();
		for(int j=0; j < treeNode.fragments().size(); j++)
		{
			String fieldName = ((VariableDeclarationFragment)treeNode.fragments().get(j)).getName().toString();
			ArrayList<Integer> variableScopeArray = getScopeArray(treeNode);
			HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
			if(variableTypeMap.containsKey(fieldName))
			{
				candidateAccumulator = variableTypeMap.get(fieldName);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			
			String treeNodeType = null;
			if(treeNode.getType().getNodeType()==74)
				treeNodeType = ((ParameterizedType)treeNode.getType()).getType().toString();
			else
				treeNodeType = treeNode.getType().toString();
			ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(treeNodeType, candidateClassNodesCache);
			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			for(NodeJSON candidateClass : candidateClassNodes)
			{
				candidateAccumulator.put(variableScopeArray, candidateClass);
				if(candidateClassNodes.size() < tolerance)
				{
					String possibleImport = getCorrespondingImport(candidateClass.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
				printtypes.put(startPosition, candidateClass);
				printTypesMap.put(fieldName, startPosition);
			}
			variableTypeMap.put(fieldName, candidateAccumulator);
		}
	}

	public boolean isLocalMethod(String methodName, Expression expression)
	{
		if(expression == null)
		{
			if(localMethods.containsKey(methodName))
			{
				//System.out.println("1");
				if(localMethods.get(methodName).contains(classNames.peek()))
					return true;
			}
		}
		else
		{
			if(localMethods.containsKey(methodName))
			{
				//System.out.println("2");
				if(expression.toString().equals("this"))
					return true;
			}
			
		}
		return false;
	}

	public void endVisit(MethodInvocation treeNode)
	{
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		Expression expression = treeNode.getExpression();
		String treeNodeMethodExactName = treeNode.getName().toString();
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getName().getStartPosition();
		String expressionString = null;
		
		if(isLocalMethod(treeNodeMethodExactName, expression) == true)
		{
			//System.out.println("local method: " + treeNodeMethodExactName + " - ");
			return;
		}
		
		if(expression != null)
		{
			expressionString = expression.toString();
			if(expressionString.startsWith("(") && expressionString.endsWith(")"))
			{
				
				expressionString = expressionString.substring(1, expressionString.length()-1);
				//System.out.println(expressionString + " !!!!!");
			}
		}
		
		if(expression==null)
		{
			//System.out.println("Here "+ treeNodeString);
			if(superclassname.isEmpty() == false)
			{	
				/*
				 * Handles inheritance, where methods from Superclasses can be directly called
				 */
				printTypesMap.put(treeNodeString, startPosition);
				printMethodsMap.put(treeNodeString, startPosition);
				HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
				if(methodReturnTypesMap.containsKey(treeNodeString))
				{
					candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
				}
				else
				{
					candidateAccumulator = HashMultimap.create();
				}
				ArrayList<NodeJSON> candidateSuperClassNodes = model.getCandidateClassNodes(superclassname, candidateClassNodesCache);
				
				
				candidateSuperClassNodes = getNewClassElementsList(candidateSuperClassNodes);
				for(NodeJSON candidateSuperClass : candidateSuperClassNodes)
				{
					ArrayList<NodeJSON> candidateMethods = new ArrayList<NodeJSON>();
					ArrayList<NodeJSON> candidateSuperClassMethods = model.getMethodNodesInClassNode(candidateSuperClass, treeNodeMethodExactName, candidateMethodNodesCache);
					for(NodeJSON node : candidateSuperClassMethods)
					{
						candidateMethods.add(node);
					}
					ArrayList<NodeJSON> candidateSuperClassParents = model.getParents(candidateSuperClass, parentNodeCache);
					for(NodeJSON candidateSuperClassParent : candidateSuperClassParents)
					{
						//System.out.println("here" + treeNodeString);
						ArrayList<NodeJSON> candidateSuperClassParentsMethods = model.getMethodNodesInClassNode(candidateSuperClassParent, treeNodeMethodExactName, candidateMethodNodesCache);
						for(NodeJSON node : candidateSuperClassParentsMethods)
						{
							candidateMethods.add(node);
						}
					}
					
					for(NodeJSON candidateSuperClassMethod : candidateMethods)
					{
						
						String candidateMethodExactName = (String)candidateSuperClassMethod.getProperty("exactName");
						if(candidateMethodExactName.equals(treeNodeMethodExactName))
						{
							if(matchParams(candidateSuperClassMethod, treeNode.arguments())==true)
							{
								if(candidateSuperClassNodes.size() < tolerance)
								{
									String possibleImport = getCorrespondingImport(candidateSuperClass.getProperty("id").toString());
									if(possibleImport!=null)
									{
										importList.add(possibleImport);
									}
								}

								printtypes.put(startPosition, candidateSuperClass);
								printmethods.put(startPosition, candidateSuperClassMethod);
								NodeJSON retElement = model.getMethodReturn(candidateSuperClassMethod, methodReturnCache);
								if(retElement!=null)
								{
									candidateAccumulator.put(scopeArray, retElement);
								}
							}
						}
					}
				}
				methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
			}
			else
			{
				/*
				 * Might be user declared helper functions or maybe object reference is assumed to be obvious in the snippet
				 */
				printTypesMap.put(treeNodeString, startPosition);
				printMethodsMap.put(treeNodeString, startPosition);
				HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
				if(methodReturnTypesMap.containsKey(treeNodeString))
				{
					candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
				}
				else
				{
					candidateAccumulator = HashMultimap.create();
				}
				ArrayList<NodeJSON> candidateMethodNodes = model.getCandidateMethodNodes(treeNode.getName().toString(), candidateMethodNodesCache);
				for(NodeJSON candidateMethodNode : candidateMethodNodes)
				{
					if(matchParams(candidateMethodNode, treeNode.arguments())==true)
					{
						NodeJSON methodContainerClassNode = model.getMethodContainer(candidateMethodNode, methodContainerCache);
						//System.out.println("++ " + methodContainerClassNode.getProperty("id") + " : " + candidateMethodNodeJSON.getProperty("id"));
						if(candidateMethodNodes.size() < tolerance)
						{
							//System.out.println("yeah");
							String possibleImport = getCorrespondingImport(methodContainerClassNode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(startPosition, methodContainerClassNode);
						//System.out.println(printtypes.get(startPosition) + Integer.toString(startPosition));
						printmethods.put(startPosition, candidateMethodNode);
						NodeJSON retElement = model.getMethodReturn(candidateMethodNode, methodReturnCache);
						if(retElement!=null)
						{
							candidateAccumulator.put(scopeArray, retElement);
						}
					}
				}
				methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
			}
		}
		else if(expressionString.contains("System."))
		{

		}
		else if(expression.getNodeType() == 2)
		{
			//System.out.println("array method");
		}
		else if(variableTypeMap.containsKey(expressionString))
		{
			printTypesMap.put(treeNodeString, startPosition);
			printMethodsMap.put(treeNodeString, startPosition);

			ArrayList<NodeJSON> replacementClassNodesList = new ArrayList<NodeJSON>();
			HashMultimap<ArrayList<Integer>, NodeJSON> temporaryMap = variableTypeMap.get(expressionString);
			ArrayList<Integer> rightScopeArray = getNodeSet(temporaryMap, scopeArray);
			if(rightScopeArray == null)
				return;

			Set<NodeJSON> candidateClassNodes = temporaryMap.get(rightScopeArray);

			HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
			if(methodReturnTypesMap.containsKey(treeNodeString))
			{
				candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}

			for(NodeJSON candidateClassNode : candidateClassNodes)
			{
				ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode,treeNodeMethodExactName, candidateMethodNodesCache);
				int hasCandidateFlag = 0;
				for(NodeJSON candidateMethodNode : candidateMethodNodes)
				{
					String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
					if((candidateMethodExactName).equals(treeNodeMethodExactName))
					{
						if(matchParams(candidateMethodNode, treeNode.arguments())==true)
						{
							printmethods.put(startPosition, candidateMethodNode);
							NodeJSON fcname=model.getMethodContainer(candidateMethodNode, methodContainerCache);
							if(fcname!=null)
								replacementClassNodesList.add(fcname);
							NodeJSON retElement = model.getMethodReturn(candidateMethodNode, methodReturnCache);
							if(retElement!=null)
							{
								candidateAccumulator.put(scopeArray, retElement);
							}
							//hasCandidateFlag = 1;
						}
					}
				}
				if(hasCandidateFlag == 0)
				{
					ArrayList<NodeJSON> parentNodeList = model.getParents(candidateClassNode, parentNodeCache);
					parentNodeList = getNewClassElementsList(parentNodeList);
					for(NodeJSON parentNode: parentNodeList)
					{
						//ArrayList<NodeJSON> methodNodes = model.getMethodNodes(parentNode, methodNodesInClassNode);
						ArrayList<NodeJSON> methodNodes = model.getMethodNodesInClassNode(parentNode, treeNodeMethodExactName, candidateMethodNodesCache);
						for(NodeJSON methodNode : methodNodes)
						{
							String candidateMethodExactName = (String)methodNode.getProperty("exactName");
							if(candidateMethodExactName.equals(treeNodeMethodExactName))
							{
								if(matchParams(methodNode, treeNode.arguments())==true)
								{
									printmethods.put(startPosition, methodNode);
									NodeJSON fcname = model.getMethodContainer(methodNode, methodContainerCache);
									if(fcname != null)
										replacementClassNodesList.add(fcname);
									NodeJSON retElement = model.getMethodReturn(methodNode, methodReturnCache);
									if(retElement != null)
									{
										candidateAccumulator.put(scopeArray, retElement);
									}
								}
							}
						}
					}
				}
			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);

			if(replacementClassNodesList.isEmpty() == false)
			{
				variableTypeMap.get(expressionString).replaceValues(rightScopeArray,replacementClassNodesList);
				printtypes.removeAll(printTypesMap.get(expressionString));
				printtypes.putAll(printTypesMap.get(expressionString), replacementClassNodesList);
			}
		}
		else if(expressionString.matches("[A-Z][a-zA-Z]*"))
		{
			printTypesMap.put(treeNodeString, startPosition);
			printMethodsMap.put(treeNodeString, startPosition);
			HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
			if(methodReturnTypesMap.containsKey(treeNodeString))
			{
				candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			ArrayList <NodeJSON> replacementClassNodesList = new ArrayList<NodeJSON>();
			
			ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(expressionString, candidateClassNodesCache);
			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			for(NodeJSON candidateClassNode : candidateClassNodes)
			{
				//ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
				ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, treeNodeMethodExactName, candidateMethodNodesCache);
				for(NodeJSON candidateMethodNode : candidateMethodNodes)
				{
					String candidateMetodExactName = (String)candidateMethodNode.getProperty("exactName");
					if(candidateMetodExactName.equals(treeNodeMethodExactName))
					{
						if(matchParams(candidateMethodNode, treeNode.arguments())==true)
						{
							printmethods.put(startPosition, candidateMethodNode);
							replacementClassNodesList.add(candidateClassNode);
							NodeJSON retElement = model.getMethodReturn(candidateMethodNode, methodReturnCache);
							if(retElement!=null)
							{
								candidateAccumulator.put(scopeArray, retElement);
							}
						}
					}
				}	
			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);

			if(replacementClassNodesList.isEmpty()==false)
			{ 
				if(variableTypeMap.containsKey(expressionString))
				{
					variableTypeMap.get(expressionString).replaceValues(scopeArray,replacementClassNodesList);
					printtypes.replaceValues(printTypesMap.get(expressionString), replacementClassNodesList);
				}
			}
		}
		else if(methodReturnTypesMap.containsKey(expressionString))
		{
			printTypesMap.put(treeNodeString, startPosition);
			printMethodsMap.put(treeNodeString, startPosition);

			HashMultimap<ArrayList<Integer>, NodeJSON> nodeInMap = methodReturnTypesMap.get(expressionString);
			//System.out.println(nodeInMap);
			//System.out.println(scopeArray);
			HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
			if(methodReturnTypesMap.containsKey(treeNodeString))
			{
				candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			ArrayList<NodeJSON> replacementClassNodesList = new ArrayList<NodeJSON>();
			ArrayList<Integer> newscopeArray = getNodeSet(nodeInMap, scopeArray);
			Set<NodeJSON> candidateClassNodes = nodeInMap.get(newscopeArray);
			//System.out.println(expressionString + " " + treeNodeString +" : "+candidateClassNodes);
			for(NodeJSON candidateClassNode : candidateClassNodes)
			{
				//System.out.println(candidateClassNode.getProperty("id"));
				String candidateClassExactName = (String) candidateClassNode.getProperty("exactName");
				//ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
				ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, treeNodeMethodExactName, candidateMethodNodesCache);
				for(NodeJSON candidateMethodNode : candidateMethodNodes)
				{
					String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
					if(candidateMethodExactName.equals(treeNodeMethodExactName))
					{	
						if(matchParams(candidateMethodNode, treeNode.arguments())==true)
						{
							//System.out.println(treeNode.getName() + " : " + candidateMethodNode.getProperty("id"));
							printmethods.put(startPosition, candidateMethodNode);
							NodeJSON fcname = model.getMethodContainer(candidateMethodNode,methodContainerCache);
							if(fcname!=null && ((String)fcname.getProperty("exactName")).equals(candidateClassExactName)==true)
							{
								//System.out.println("-- " + fcname.getProperty("id") + " : " + candidateMethodNode.getProperty("id"));
								replacementClassNodesList.add(fcname);
							}
							NodeJSON retElement = model.getMethodReturn(candidateMethodNode, methodReturnCache);
							if(retElement!=null)
							{
								candidateAccumulator.put(scopeArray, retElement);
							}
						}

					}
				}

			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);

			if(replacementClassNodesList.isEmpty()==false)
			{
				HashMultimap<ArrayList<Integer>, NodeJSON> replacer = HashMultimap.create();
				replacer.putAll(newscopeArray, replacementClassNodesList);
				methodReturnTypesMap.put(expressionString, replacer);
				
			}
		}
		else
		{
			//System.out.println("here" + getScopeArray(treeNode.getParent()));
			ArrayList <NodeJSON> replacementClassNodesList= new ArrayList<NodeJSON>();
			HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
			if(methodReturnTypesMap.containsKey(treeNodeString))
			{
				candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			printMethodsMap.put(treeNodeString, startPosition);
			ArrayList<NodeJSON> candidateMethodNodes = model.getCandidateMethodNodes(treeNodeMethodExactName, candidateMethodNodesCache);
			for(NodeJSON candidateMethodNode : candidateMethodNodes)
			{
				if(matchParams(candidateMethodNode, treeNode.arguments())==true)
				{
					NodeJSON fcname = model.getMethodContainer(candidateMethodNode, methodContainerCache);
					if(fcname!=null)
					{
						replacementClassNodesList.add(fcname);
					}
					printmethods.put(startPosition, candidateMethodNode);
					NodeJSON retElement = model.getMethodReturn(candidateMethodNode, methodReturnCache);
					if(retElement!=null)
					{
						candidateAccumulator.put(scopeArray, retElement);
					}
				}
			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
			if(replacementClassNodesList.isEmpty()==false)
			{
				HashMultimap<ArrayList<Integer>, NodeJSON> replacer = HashMultimap.create();
				replacer.putAll(getScopeArray(treeNode.getParent()), replacementClassNodesList);
				variableTypeMap.put(expressionString,replacer);
				
				printTypesMap.put(expressionString, startPosition);
				
				printtypes.removeAll(printTypesMap.get(expressionString));
				printtypes.putAll(printTypesMap.get(expressionString), replacementClassNodesList);
			}
		}
	}

	private ArrayList<Integer> getNodeSet(HashMultimap<ArrayList<Integer>, NodeJSON> celist2, ArrayList<Integer> scopeArray) 
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
		if(scopeArray.containsAll(test.subList(1, test.size())))
			return true;
		/*else if(scopeArray.containsAll(test.subList(1, test.size())))
			return true;*/
		else
			return false;
	}

	private boolean matchParams(NodeJSON me, List<ASTNode> params) 
	{
		ArrayList<HashSet<String>> nodeArgs = new ArrayList<HashSet<String>>();
		ArrayList<NodeJSON>graphNodes = new ArrayList<NodeJSON>();
		graphNodes = model.getMethodParams(me, methodParameterCache);
		
		//System.out.println("++ " + me.getProperty("id") + " "  + graphNodes.size() + " " + params.size());
		
		if(graphNodes.size() != params.size())
			return false;
		if(params.size()==0 && graphNodes.size()==0)
		{
			return true;
		}
		for(ASTNode param : params)
		{
			HashSet<String> possibleTypes = new HashSet<String>();
			if(param.getNodeType()==34)
			{
				possibleTypes.add("int");
				possibleTypes.add("byte");
				possibleTypes.add("float");
				possibleTypes.add("double");
				possibleTypes.add("long");
				possibleTypes.add("short");
			}
			else if(param.getNodeType()==9)
			{
				possibleTypes.add("boolean");
			}
			else if(param.getNodeType()==13)
			{
				possibleTypes.add("char");
			}
			else if(param.getNodeType()==27)
			{
				InfixExpression tempNode = (InfixExpression) param;
				if(tempNode.getLeftOperand().getNodeType() == 45 || tempNode.getRightOperand().getNodeType() == 45)
					possibleTypes.add("String");
				else if(tempNode.getLeftOperand().getNodeType() == 34 || tempNode.getRightOperand().getNodeType() == 34)
				{
					possibleTypes.add("int");
					possibleTypes.add("byte");
					possibleTypes.add("float");
					possibleTypes.add("double");
					possibleTypes.add("long");
					possibleTypes.add("short");
				}
				else
					possibleTypes.add("UNKNOWN");
			}
			else if(param.getNodeType()==45)
			{
				possibleTypes.add("String");
			}
			else if (param.getNodeType()==42)
			{
				if(variableTypeMap.containsKey(param.toString()))
				{
					HashMultimap<ArrayList<Integer>, NodeJSON> celist_temp = variableTypeMap.get(param.toString());
					ArrayList<Integer> intermediate = getNodeSet(celist_temp, getScopeArray(param));
					if(intermediate!=null)
					{
						Set<NodeJSON> localTypes = celist_temp.get(intermediate);
						for(NodeJSON localType : localTypes)
						{
							possibleTypes.add((String) localType.getProperty("id"));
						}
					}
				}
				else
				{
					possibleTypes.add("UNKNOWN");
					//System.out.println("UNKNOWN");
				}
			}
			else if(param.getNodeType()==32)
			{
				if(methodReturnTypesMap.containsKey(param.toString()))
				{
					HashMultimap<ArrayList<Integer>, NodeJSON> temporaryMap = methodReturnTypesMap.get(param.toString());
					ArrayList<Integer> scopeArray = getScopeArray(param);
					ArrayList<Integer> rightScopeArray = getNodeSet(temporaryMap, scopeArray);
					if(rightScopeArray == null)
						return false;
					Set<NodeJSON> candidateClassNodes = temporaryMap.get(rightScopeArray);
					for(NodeJSON localType : candidateClassNodes)
					{
						possibleTypes.add((String) localType.getProperty("id"));
					}
				}
				else
				{
					possibleTypes.add("UNKNOWN");
					//System.out.println("UNKNOWN");
				}
			}
			else if(param.getNodeType()==14)
			{
				ClassInstanceCreation tempNode = (ClassInstanceCreation) param;
				possibleTypes.add(tempNode.getType().toString());
				//System.out.println("14:  "+tempNode.getType().toString());
			}
			else
			{
				possibleTypes.add("UNKNOWN");
			}
			nodeArgs.add(possibleTypes);
		}
		
		
		Iterator<NodeJSON> iter1 = graphNodes.iterator();
		Iterator<HashSet<String>> iter2 = nodeArgs.iterator();
		while(iter1.hasNext())
		{
			NodeJSON graphParam = iter1.next();
			HashSet<String> args = iter2.next();
			int flag=0;
			for(String arg : args)
			{
				if(((String)graphParam.getProperty("exactName")).equals(arg)== true || ((String)graphParam.getProperty("id")).equals(arg)==true)
				{
					flag=0;
					break;
				}
				else if(arg.equals("UNKNOWN"))
				{
					flag=0;
					break;
				}
				else if(model.checkIfParentNode(graphParam, arg, parentNodeCache))
				{
					flag=0;
					break;
				}
				else
					flag=1;
			}
			if(flag==1)
				return false;
		}
		
		return true;
		}

	public boolean visit(TypeDeclaration treeNode)
	{
		classNames.push(treeNode.getName().toString());
		if(treeNode.getSuperclassType()!=null)
		{
			if(treeNode.getSuperclassType().getNodeType()==74)
			{
				superclassname = ((ParameterizedType)treeNode.getSuperclassType()).getType().toString();
			}
			else
			{
				superclassname = treeNode.getSuperclassType().toString();
			}
		}

		for(Object ob : treeNode.superInterfaceTypes())
		{	
			interfaces.add(ob);
		}
		return true;
	}
	
	public void endVisit(TypeDeclaration treeNode)
	{
		classNames.pop();
	}

	public boolean visit(MethodDeclaration treeNode)
	{
		int startPosition = treeNode.getStartPosition();
		List<SingleVariableDeclaration> param = treeNode.parameters();
		for(int i=0;i<param.size();i++)
		{
			ArrayList<Integer> scopeArray = getScopeArray(treeNode);
			HashMultimap<ArrayList<Integer>,NodeJSON> temporaryMap = null;
			if(variableTypeMap.containsKey(param.get(i).getName().toString()))
			{
				temporaryMap = variableTypeMap.get(param.get(i).getName().toString());
			}
			else
			{
				temporaryMap = HashMultimap.create();
			}
			
			String parameterType = null;
			if(param.get(i).getType().getNodeType()==74)
				parameterType = ((ParameterizedType)param.get(i).getType()).getType().toString();
			else
				parameterType = param.get(i).getType().toString();
			ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(parameterType, candidateClassNodesCache);
			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			
			for(NodeJSON candidateClassNode : candidateClassNodes)
			{
				temporaryMap.put(scopeArray, candidateClassNode);
				if(candidateClassNodes.size() < tolerance)
				{
					String possibleImport = getCorrespondingImport(candidateClassNode.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
				printtypes.put(param.get(i).getType().getStartPosition(),candidateClassNode);
				printTypesMap.put(param.get(i).getName().toString(), param.get(i).getType().getStartPosition());
			}
			variableTypeMap.put(param.get(i).getName().toString(), temporaryMap);
		}

		if(superclassname!=null)
		{
			ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(superclassname, candidateClassNodesCache);
			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			for(NodeJSON candidateClassNode : candidateClassNodes)
			{
				//Collection<NodeJSON> candidateMethodNodes=model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
				ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, treeNode.getName().toString(), candidateMethodNodesCache);
				for(NodeJSON candidateMethodNode : candidateMethodNodes)
				{
					String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
					if(candidateMethodExactName.equals(treeNode.getName().toString()))
					{
						if(matchParams(candidateMethodNode, treeNode.parameters())==true)
						{
							NodeJSON parentNode = model.getMethodContainer(candidateMethodNode, methodContainerCache);
							if(candidateMethodNodes.size() < tolerance)
							{
								String possibleImport = getCorrespondingImport(parentNode.getProperty("id").toString());
								if(possibleImport!=null)
								{
									importList.add(possibleImport);
								}
							}
							printtypes.put(startPosition, parentNode);
							printmethods.put(startPosition, candidateMethodNode);
						}	
					}
				}
			}
		}

		if(!interfaces.isEmpty())
		{
			for(int i=0;i<interfaces.size();i++)
			{
				ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(interfaces.get(i).toString(), candidateClassNodesCache);
				candidateClassNodes = getNewClassElementsList(candidateClassNodes);
				for(NodeJSON candidateClassNode : candidateClassNodes)
				{
					//Collection<NodeJSON> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
					ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, treeNode.getName().toString(), candidateMethodNodesCache);
					for(NodeJSON candidateMethodNode : candidateMethodNodes)
					{
						String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
						if(candidateMethodExactName.equals(treeNode.getName().toString()))
						{
							if(matchParams(candidateMethodNode, treeNode.parameters())==true)
							{
								NodeJSON parentNode = model.getMethodContainer(candidateMethodNode, methodContainerCache);
								if(candidateMethodNodes.size() < tolerance)
								{
									String possibleImport = getCorrespondingImport(parentNode.getProperty("id").toString());
									if(possibleImport!=null)
									{
										importList.add(possibleImport);
									}
								}
								printtypes.put(startPosition, parentNode);
								printmethods.put(startPosition, candidateMethodNode);
							}
						}
					}
				}
			}
		}
		return true;
	}

	public void endVisit(ConstructorInvocation treeNode)
	{	
		//System.out.println("here constructr");
		String treeNodeString = treeNode.toString();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		int startPosition = treeNode.getStartPosition();
		ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(classNames.peek(), candidateClassNodesCache);
		candidateClassNodes = getNewClassElementsList(candidateClassNodes);
		for(NodeJSON candidateClassNode : candidateClassNodes)
		{
			//ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
			ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, "<init>", candidateMethodNodesCache);
			for(NodeJSON candidateMethodNode : candidateMethodNodes)
			{
				String candidateMethodExactname = (String)candidateMethodNode.getProperty("exactName");
				if(candidateMethodExactname.equals("<init>"))
				{
					if(matchParams(candidateMethodNode, treeNode.arguments())==true)
					{
						printmethods.put(startPosition, candidateMethodNode);
						NodeJSON parentNode = model.getMethodContainer(candidateMethodNode, methodContainerCache);
						if(candidateMethodNodes.size() < tolerance)
						{
							String possibleImport = getCorrespondingImport(parentNode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(startPosition, parentNode);
						NodeJSON returnNode = model.getMethodReturn(candidateMethodNode, methodReturnCache);
						if(returnNode != null)
						{
							candidateAccumulator.put(scopeArray, returnNode);
						}

					}
				}
			}
		}
		methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
	}

	public boolean visit(CatchClause node)
	{
		int startPosition = node.getException().getType().getStartPosition();
		ArrayList<Integer> scopeArray = getScopeArray(node);

		HashMultimap<ArrayList<Integer>, NodeJSON> temporaryMap = null;
		if(variableTypeMap.containsKey(node.getException().getName().toString()))
		{
			temporaryMap = variableTypeMap.get(node.getException().getName().toString());
		}
		else
		{
			temporaryMap = HashMultimap.create();
		}
		//System.out.println("catch inv 1");
		ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(node.getException().getType().toString(), candidateClassNodesCache);
		candidateClassNodes = getNewClassElementsList(candidateClassNodes);
		for(NodeJSON candidateClassNode : candidateClassNodes)
		{
			temporaryMap.put(scopeArray, candidateClassNode);
			if(candidateClassNodes.size() < tolerance)
			{
				String possibleImport = getCorrespondingImport(candidateClassNode.getProperty("id").toString());
				if(possibleImport!=null)
				{
					importList.add(possibleImport);
				}
			}
			printtypes.put(startPosition, candidateClassNode);
			printTypesMap.put(node.getException().getName().toString(), startPosition);
		}
		variableTypeMap.put(node.getException().getName().toString(), temporaryMap);
		return true;
	}

	public void endVisit(SuperConstructorInvocation treeNode)
	{	
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		int startPosition = treeNode.getStartPosition();
		String treeNodeString = treeNode.toString();
		HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(superclassname, candidateClassNodesCache);
		candidateClassNodes = getNewClassElementsList(candidateClassNodes);
		for(NodeJSON candidateClassNode : candidateClassNodes)
		{
			//Collection<NodeJSON>candidateMethodElements = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
			ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, "<init>", candidateMethodNodesCache);
			for(NodeJSON candidateMethodElement : candidateMethodNodes)
			{
				String candidateMethodExactName = (String)candidateMethodElement.getProperty("exactName");
				if(candidateMethodExactName.equals("<init>"))
				{
					if(matchParams(candidateMethodElement, treeNode.arguments())==true)
					{
						printmethods.put(startPosition,candidateMethodElement);
						NodeJSON parentNode = model.getMethodContainer(candidateMethodElement, methodContainerCache);
						if(candidateMethodNodes.size() < tolerance)
						{
							String possibleImport = getCorrespondingImport(parentNode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(startPosition, parentNode);
						NodeJSON methodReturnNode = model.getMethodReturn(candidateMethodElement, methodReturnCache);
						if(methodReturnNode != null)
						{
							candidateAccumulator.put(scopeArray, methodReturnNode);
						}
					}
				}	
			}
		}
		methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
	}

	public void endVisit(SuperMethodInvocation treeNode)
	{
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		int startPosition = treeNode.getStartPosition();
		String treeNodeString = treeNode.toString();
		HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		String treeNodeName = treeNode.getName().toString();
		ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(superclassname, candidateClassNodesCache);
		candidateClassNodes = getNewClassElementsList(candidateClassNodes);
		ArrayList <NodeJSON> clist= new ArrayList<NodeJSON>();

		printMethodsMap.put(treeNode.toString(), startPosition);

		for(NodeJSON candidateClassNode : candidateClassNodes)
		{
			//Collection<NodeJSON> candidateMethodNodes = model.getMethodNodes(caateClassNode, methodNodesInClassNode);
			ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, treeNodeName, candidateMethodNodesCache);
			for(NodeJSON candidateMethodNode : candidateMethodNodes)
			{
				String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
				if(candidateMethodExactName.equals(treeNodeName))
				{
					if(matchParams(candidateMethodNode, treeNode.arguments())==true)
					{
						NodeJSON fcname=model.getMethodContainer(candidateMethodNode, methodContainerCache);
						if(fcname!=null)
							clist.add(fcname);
						printmethods.put(startPosition, candidateMethodNode);

						NodeJSON methodReturnNode = model.getMethodReturn(candidateMethodNode, methodReturnCache);
						if(methodReturnNode != null)
						{
							candidateAccumulator.put(scopeArray, methodReturnNode);
						}
					}
				}
			}
			//System.out.println(treeNodeString + " : " + candidateAccumulator);
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);

			if(clist.isEmpty()==false)
			{
				printtypes.replaceValues(treeNode.getStartPosition(), clist);
			}
		}
	}

	public boolean visit(final ClassInstanceCreation treeNode)
	{
		ASTNode anon=treeNode.getAnonymousClassDeclaration();
		if(anon!=null)
		{
			anon.accept(new ASTVisitor(){
				public void endVisit(MethodDeclaration md)
				{
					String methodDeclarationName = md.getName().toString();
					int startPosition = md.getStartPosition();
					ArrayList <NodeJSON> candidateClassNodes = model.getCandidateClassNodes(treeNode.getType().toString(), candidateClassNodesCache);
					candidateClassNodes = getNewClassElementsList(candidateClassNodes);
					for(NodeJSON candidateClassNode : candidateClassNodes)
					{
						//Collection<NodeJSON>candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
						ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, methodDeclarationName, candidateMethodNodesCache);
						for(NodeJSON candidateMethodNode : candidateMethodNodes)
						{
							String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
							if(candidateMethodExactName.equals(methodDeclarationName))
							{
								if(matchParams(candidateMethodNode, md.parameters())==true)
								{
									printmethods.put(startPosition,candidateMethodNode);
									printMethodsMap.put(md.toString(), startPosition);
									if(candidateMethodNodes.size() < tolerance)
									{
										String possibleImport = getCorrespondingImport(candidateClassNode.getProperty("id").toString());
										if(possibleImport!=null)
										{
											importList.add(possibleImport);
										}
									}
									printtypes.put(startPosition, candidateClassNode);
									printTypesMap.put(treeNode.toString(), startPosition);
								}
							}
						}
					}
				}
			});
		}
		//System.out.println(treeNode.toString()+ treeNode.getParent().getParent().getNodeType());
		String treeNodeString= treeNode.toString();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		int startPosition = treeNode.getType().getStartPosition();
		printMethodsMap.put(treeNodeString, startPosition);
		printTypesMap.put(treeNodeString, startPosition);
		ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(treeNode.getType().toString(), candidateClassNodesCache);
		candidateClassNodes = getNewClassElementsList(candidateClassNodes);
		
		HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		
		for(NodeJSON candidateClassNode : candidateClassNodes)
		{
			//System.out.println("yes: "+candidateClassNode.getProperty("id"));
			//Collection<NodeJSON> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
			ArrayList<NodeJSON> candidateMethodNodes = model.getMethodNodesInClassNode(candidateClassNode, "<init>", candidateMethodNodesCache);
			for(NodeJSON candidateMethodNode : candidateMethodNodes)
			{
				String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
				if(candidateMethodExactName.equals("<init>"))
				{
					//System.out.println("++ : " + candidateMethodNode.getProperty("id"));
					if(matchParams(candidateMethodNode, treeNode.arguments())==true)
					{
						//System.out.println("## : " + candidateMethodNode.getProperty("id"));
						//System.out.println(candidateClassNode);
						printtypes.put(startPosition, candidateClassNode);
						printmethods.put(startPosition, candidateMethodNode);
						candidateAccumulator.put(scopeArray, candidateClassNode);
					}
				}
			}
		}
		if(treeNode.getParent().getNodeType() == 59)
		{
			VariableDeclarationFragment lhs = ((VariableDeclarationFragment) treeNode.getParent());
			
			HashMultimap<ArrayList<Integer>, NodeJSON> tempMap = HashMultimap.create(); 
			tempMap.putAll(getScopeArray(lhs.getParent()), printtypes.get(startPosition));
			variableTypeMap.put(lhs.getName().toString(), tempMap);
			
		}
		methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
		return true;
	}

	public void endVisit(ClassInstanceCreation treeNode)
	{	
		/*System.out.println("here -- ClassInstanceCreation");
		System.out.println("endvisit class inst cre 1");
		int startPosition = treeNode.getType().getStartPosition();
		String treeNodeType = treeNode.getType().toString();
		System.out.println(treeNodeType);
		ArrayList<NodeJSON> candidateClassNodes = model.getCandidateClassNodes(treeNodeType, candidateClassNodesCache);
		candidateClassNodes = getNewCeList(candidateClassNodes);
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		String treeNodeString = treeNode.toString();
		HashMultimap<ArrayList<Integer>, NodeJSON> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		for(NodeJSON candidateClassNode : candidateClassNodes)
		{
			System.out.println("here");
			printTypesMap.put(treeNode.toString(), startPosition);
			printtypes.put(startPosition, candidateClassNode);
			candidateAccumulator.put(scopeArray, candidateClassNode);
		}
		methodReturnTypesMap.put(treeNodeString, candidateAccumulator);*/

	}

	public boolean visit(CastExpression node)
	{
		ArrayList <NodeJSON> candidateClassNodes = model.getCandidateClassNodes(node.getType().toString(), candidateClassNodesCache);
		candidateClassNodes = getNewClassElementsList(candidateClassNodes);

		HashMultimap<ArrayList<Integer>, NodeJSON> temp1= null;
		HashMultimap<ArrayList<Integer>, NodeJSON> temp2= null;

		ArrayList<Integer> scopeArray = getScopeArray(node);
		if(variableTypeMap.containsKey(node.toString()))
		{
			temp1 = variableTypeMap.get(node.toString());
		}
		else
		{
			temp1 = HashMultimap.create();
		}
		if(variableTypeMap.containsKey("("+node.toString()+")"))
		{
			temp2 = variableTypeMap.get("("+node.toString()+")");
		}
		else
		{
			temp2 = HashMultimap.create();
		}
		for(NodeJSON candidateClassNode : candidateClassNodes)
		{
			if(candidateClassNode!=null)
			{
				temp1.put(scopeArray, candidateClassNode);
				if(candidateClassNodes.size() < tolerance)
				{
					String possibleImport = getCorrespondingImport(candidateClassNode.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
				printtypes.put(node.getType().getStartPosition(), candidateClassNode);
				temp2.put(scopeArray, candidateClassNode);
			}
		}
		variableTypeMap.put(node.toString(), temp1);
		variableTypeMap.put("("+node.toString()+")", temp2);
		return true;
	}

	public void endVisit(Assignment node)
	{
		String lhs,rhs;
		lhs=node.getLeftHandSide().toString();
		rhs=node.getRightHandSide().toString();

		if(methodReturnTypesMap.containsKey(rhs))
		{
			if(!variableTypeMap.containsKey(lhs))
			{
				methodReturnTypesMap.put(lhs, methodReturnTypesMap.get(rhs));
				methodReturnTypesMap.put(lhs, methodReturnTypesMap.get(rhs));

			}
			else
			{	
				int flag=0;
				Set<NodeJSON> temp = new HashSet<NodeJSON>();
				HashMultimap<ArrayList<Integer>, NodeJSON> celist_temp = variableTypeMap.get(lhs);
				ArrayList<Integer> scopeArray = getNodeSet(celist_temp, getScopeArray(node));
				if(scopeArray!=null)
				{
					Set<NodeJSON> localTypes = celist_temp.get(scopeArray);
					for(NodeJSON ce : localTypes)
					{
						if(methodReturnTypesMap.get(rhs).values().contains(ce))
						{
							flag=1;
							temp.add(ce);
						}
					}
				}
				if(flag==1)
				{
					variableTypeMap.get(lhs).replaceValues(scopeArray,temp);
				}

			}
		}
	}

	public boolean visit(ImportDeclaration node)
	{

		String importStatement = node.getName().getFullyQualifiedName();
		if(importStatement.endsWith(".*"))
		{
			importStatement= importStatement.substring(0, importStatement.length()-2);
		}
		importList.add(importStatement);
		return true;
	}

	public JSONObject printJson()
	{
		checkForNull();

		//Add to primitive and uncomment to remove unwanted elements
		//String[] primitive = {"int","float","char","long","boolean","String","byte[]","String[]","int[]","float[]","char[]","long[]","byte"};
		String[] primitive={};
		JSONObject main_json=new JSONObject();

		//Collections.sort(printtypes, printtypes.keySet());
		for(Integer key:printtypes.keySet())
		{
			int flag=0;
			String cname=null;
			List<String> namelist = new ArrayList<String>();
			for(NodeJSON type_name:printtypes.get(key))
			{
				int isprimitive=0;
				for(String primitive_type : primitive)
				{
					if(((String)type_name.getProperty("id")).equals(primitive_type)==true)
					{
						isprimitive=1;
						break;
					}
				}
				if(isprimitive==0)
				{
					String nameOfClass = (String)type_name.getProperty("id");
					namelist.add("\""+nameOfClass+"\"");
					if(flag==0)
					{
						cname=(String) type_name.getProperty("exactName");
						flag=1;
					}
				}

			}
			if(namelist.isEmpty()==false)
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
		for(Integer key:printmethods.keySet())
		{
			List<String> namelist = new ArrayList<String>();
			String mname=null;
			for(NodeJSON method_name:printmethods.get(key))
			{
				String nameOfMethod = (String)method_name.getProperty("id");
				namelist.add("\""+nameOfMethod+"\"");
				mname=(String) method_name.getProperty("exactName");
			}
			if(namelist.isEmpty()==false)
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
		if(main_json.isNull("api_elements"))
		{
			String emptyJSON = "{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}" ;
			JSONObject ret = new JSONObject();
			try 
			{
				ret = new JSONObject(emptyJSON);
			} 
			catch (ParseException e) 
			{
				e.printStackTrace();
			}
			return(ret);
		}
		else
		{
			return(main_json);
		}
	}

	public void checkForNull()
	{
		for(Integer key : printtypes.keySet())
			for(NodeJSON type_name:printtypes.get(key))
			{
				if(type_name==null)
					printtypes.remove(key, type_name);
			}
		for(Integer key : printmethods.keySet())
			for(NodeJSON method_name:printmethods.get(key))
			{
				if(method_name==null)
					printmethods.remove(key, method_name);
			}
	}


}