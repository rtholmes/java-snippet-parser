# java-baker
 #

Java Snippet Parser - Parses incomplete Java source code snippets to identify API usage instances in them. Uses a Neo4j Graph as an oracle while making predictions. Primarily to provide better API documentation to libraries and provide a two-way mapping between API documentation and source code examples on online resources like StackOverflow and Github.


**Parsing public API from .jar files for the Oracle to consume -
**
```
#!java

1. Inconsistencyinspectorresources takes in a JAR file and returns an XML with all of its API. https://bitbucket.org/rtholmes/inconsistencyinspectorresources. Use it as described under the static resources part in the README.
2. Once you have the XML, clone this repository - https://bitbucket.org/siddhukrs/model-generator. This is the repo that contains scripts to build the graph. In Graph.java in the default package, in the main method, rename fname2 to point to the XML and DB_PATH to where you want the graph written. If you have multiple XML files from multiple JARs, use the commented out block of code below it to iterate through the files. Any dependencies required by the project are in the dependencies folder. This writes the graph to DB_PATH.
```


**Running the neo4j graph server and parsing snippets -
**
```
#!java

1. The dependencies folder has all necessary libraries to create the graph. However, to run a server, you would have to install neo4j on your machine. Their website has details on setting this up.
2. Once you have neo4j installed, you can update neo4j's server config to set this graph folder as your database and have it run on your local host. You can then use the RestAPI package to access the graph. The Tester.java file can be used to test if your setup worked right.
3. Once that is set up, clone https:\/\/bitbucket.org\/siddhukrs\/java-snippet-parser to access the parser. The parser uses the model-generator to access the graph. It takes a snippet of code to analyze it against the graph and find API usages.
```