import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ClusterElimination
{
	public static void main(String[] args) throws IOException 
	{
		
		String collisionsDetected = "class-collisions_update.txt";
		String originalClasses = "forReid.txt";
		
		HashMap<String, ArrayList<String>> clustersMap = getClusters(collisionsDetected);
		HashMap<String, String> originalClassMap = originalClass(originalClasses);
		
		//Maps a short class name to the right package. Query this whenever we obtain a set with cardinality > 1
		HashMap<String, String> finalMap = populate(clustersMap, originalClassMap);
		test(clustersMap, originalClassMap);
	}
	
	private static HashMap<String, String> populate(
			HashMap<String, ArrayList<String>> clustersMap,
			HashMap<String, String> originalClassMap) 
	{
		HashMap<String, String> finalMap = new HashMap<String, String>();
		
		return null;
	}

	private static void test(HashMap<String, ArrayList<String>> clustersMap, HashMap<String, String> originalClassMap)
	{
		int yc = 0, nc = 0;
		/*for(String shortClassName : originalClassMap.keySet())
		{
			if(clustersMap.containsKey(shortClassName))
			{
				yc++;
				System.out.println("yes");
			}
			else
			{
				nc++;
				System.out.println("no");
			}
		}*/
		
		for(ArrayList<String> list : clustersMap.values())
		{
			for(String s : list)
			{
				String[] arr = s.split("\\.");
				String shortName = arr[arr.length - 1];
				System.out.println(s);
				if(originalClassMap.containsKey(shortName))
				{
					yc++;
					System.out.println("-- "+originalClassMap.get(shortName));
				}
				else
					nc++;
			}
			
		}
		System.out.println(clustersMap.values().size());
		System.out.println("yes: " + yc + " nc : " + nc);
		
	}
	
	private static HashMap<String, String> originalClass(String originalClasses) throws IOException
	{
		HashMap<String, String> map = new HashMap<String, String>();
		BufferedReader br = new BufferedReader(new FileReader(originalClasses));
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
				map.put(className, packageName);
			}
		}
		br.close();
		return map;
	}
	
	private static HashMap<String, ArrayList<String>> getClusters(String collisionsDetected) throws IOException 
	{
		HashMap<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
		BufferedReader br = new BufferedReader(new FileReader(collisionsDetected));
		String s = "";
		String classShortName = new String();
		ArrayList<String> list = new ArrayList<String>();
		while((s = br.readLine())!=null)
		{
			String line = s;
			if(s.startsWith("  -"))
			{
				
				line = s.substring(5);
				//line = line.substring(0,(line.lastIndexOf(':')-1)).replace('$', '.');
				line = line.substring(0,(line.lastIndexOf(':')-1));
				list.add(line);
			}
			else
			{
				if(!classShortName.isEmpty())
				{
					map.put(classShortName, list);
					list = new ArrayList<String>();
				}
				//line = line.substring(0, line.lastIndexOf(':')-1).replace('$', '.');
				line = line.substring(0, line.lastIndexOf(':')-1);
				classShortName = line;
				
			}
		}
		map.put(classShortName, list);
		
		br.close();
		return map;
	}
	
	
}