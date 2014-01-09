import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ClusterElimination
{
	
	String collisionsDetectedFile;
	String originalClassesFile;
	HashSet<HashSet<String>> clustersSet;   		//A hashset of hashsets of clusters
	HashMap<String, HashSet<String>> clustersMap;	//A mapping from shortname to clusters
	HashMap<String, String> originalClassMap;		//A mapping from shortname to the right package (if a cluster)
	HashMap<String, String> finalMap;				
	
	
	public ClusterElimination(String arg1, String arg2) throws IOException 
	{
		collisionsDetectedFile = arg1;
		originalClassesFile = arg2;
		getClustersMap();
		getOriginalClassesMap();
	}
	
	
	//Check if it is really a cluster before calling this.
	private String findRightClass(String shortName)
	{
		String rightPackage = findRightPackage(shortName);
		if(rightPackage != null)
		{
			HashSet<String> cluster = clustersMap.get(shortName);
			for(String clusterCandidate : cluster)
			{
				System.out.println(clusterCandidate);
				String clusterPackage = clusterCandidate.substring(0, clusterCandidate.lastIndexOf("."));
				if(rightPackage.indexOf(clusterPackage) != -1)
					return clusterCandidate;
			}
		}
		return null;
	}

	private String findRightPackage(String shortName)
	{
		if(originalClassMap.containsKey(shortName))
			return originalClassMap.get(shortName);
		else
			return null;
	}

	public static void main(String[] args) throws IOException 
	{
		ClusterElimination celim = new ClusterElimination("class-collisions_update.txt", "forReid.txt");
		
		System.out.println(celim.findRightClass("TaggedComponent"));
		
		HashSet<String> x = new HashSet<String>();
		System.out.println(celim.checkIfCluster(x));
		//celim.populate();
		//celim.test();
		HashSet<String> cluster = celim.clustersMap.get("Iterable");
		for(String xy : cluster)
			System.out.println(xy);
	}
	
	
	
	private HashMap<String, String> populate() 
	{
		finalMap = new HashMap<String, String>();
		
		return null;
	}
	
	private boolean checkIfCluster(HashSet<String> set)
	{
		String shortName = "";
		for(String s : set)
		{
			String arr[] = s.split("\\.");
			shortName = arr[arr.length-1];
			break;
		}
		
		if(clustersMap.containsKey(shortName))
		{
			HashSet<String> clusterSet = clustersMap.get(shortName);
			int yc = 0, nc = 0;
			for(String s : set)
			{
				if(clusterSet.contains(s) == false)
				{
					nc++;
				}
				else
					yc++;
			}
			if(yc >= set.size()-3)
				return true;
			else
				return false;
		}
		else
			return false;
		
		/*if(clustersSet.contains(set))
			return true;
		else
			return false;*/
	}
	

	private void test()
	{
		int yc = 0, nc = 0;
		for(String shortName : clustersMap.keySet())
		{
			if(originalClassMap.containsKey(shortName))
			{
				yc++;
				System.out.println(shortName);
				System.out.println("-- "+ this.findRightClass(shortName));
			}
			else
				nc++;

		}
		//System.out.println(clustersMap.values().size());
		System.out.println("yes: " + yc + " nc : " + nc);
	}
	
	private void getOriginalClassesMap() throws IOException
	{
		originalClassMap = new HashMap<String, String>();
		BufferedReader br = new BufferedReader(new FileReader(originalClassesFile));
		String s = "";
		while((s = br.readLine())!=null)
		{
			String[] arr = s.split(";");
			if(arr.length == 3)
			{
				String className = arr[1].replace(" ", "");
				String packageName = new String();
				if(!arr[0].isEmpty())
				{
					packageName = arr[0].replace(" ", "").replace('/', '.').substring(1);
				}
				originalClassMap.put(className, packageName);
			}
		}
		br.close();
	}
	
	private void getClustersMap() throws IOException 
	{
		clustersMap = new HashMap<String, HashSet<String>>();
		clustersSet = new HashSet<HashSet<String>>();
		BufferedReader br = new BufferedReader(new FileReader(collisionsDetectedFile));
		String s = "";
		String classShortName = new String();
		HashSet<String> set = new HashSet<String>();
		while((s = br.readLine())!=null)
		{
			String line = s;
			if(s.startsWith("  -"))
			{
				
				line = s.substring(5);
				//line = line.substring(0,(line.lastIndexOf(':')-1)).replace('$', '.');
				line = line.substring(0,(line.lastIndexOf(':')-1));
				set.add(line);
			}
			else
			{
				if(!classShortName.isEmpty())
				{
					clustersSet.add(set);
					clustersMap.put(classShortName, set);
					set = new HashSet<String>();
				}
				//line = line.substring(0, line.lastIndexOf(':')-1).replace('$', '.');
				line = line.substring(0, line.lastIndexOf(':')-1);
				classShortName = line;
				
			}
		}
		clustersMap.put(classShortName, set);
		clustersSet.add(set);
		br.close();
	}
	
	
}