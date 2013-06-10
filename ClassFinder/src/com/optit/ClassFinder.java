package com.optit;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.optit.logger.CommandLineLogger;
import com.optit.logger.Logger;

public class ClassFinder
{
	private Properties parameters;
	private LinkedList<File> files = new LinkedList<File>();
	private Logger logger;
	
	public ClassFinder()
	{
		logger = new CommandLineLogger();
	}
	
	public ClassFinder(Logger logger)
	{
		this.logger = logger;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		ClassFinder finder = new ClassFinder();
		if (!finder.parseArguments(args))
		{
			// Parsing of arguments was not successful, print help and exit
			new ClassFinder().printHelp();
			System.exit(0);
		}
		finder.findClass();
	}
	
	/**
	 * Parses the passed on arguments
	 * @param args The arguments to pass on
	 * @return Whether the parsing of the arguments was successful
	 */
	public boolean parseArguments(String[] args)
	{
		// No parameters were passed, print help and exit printHelp does the exit
		if (null == args || args.length == 0 || args.length == 1)
		{
			return false;
		}
		else
		{
			// Parameters were passed on, properties file ignored -> read passed on parameters
			parameters = new Properties();
			// Set defaults
			parameters.setProperty(Parameters.matchCase, "false");
			parameters.setProperty(Parameters.verbose, "false");
			
			for (int i=0;i<args.length;i++)
			{
				switch (args[i])
				{
					case Parameters.directory:
					{
						parameters.setProperty(Parameters.directory, args[++i]);
						break;
					}
					case Parameters.classname:
					{
						parameters.setProperty(Parameters.classname, args[++i]);
						break;
					}
					case Parameters.matchCase:
					{
						parameters.setProperty(Parameters.matchCase, "true");
						break;
					}
					case Parameters.verbose:
					{
						parameters.setProperty(Parameters.verbose, "true");
						break;
					}
					case "-help":
					case "--help":
					case "-?":
					case "--?":
					{
						printHelp();
						System.exit(0);
					}
					default:
					{
						logger.log("Unknown parameter: " + args[i]);
						logger.log();
						return false;
					}
				}
			}
			return true;
		}
	}
	
	/**
	 * Print usage help into stdout and Exit
	 */
	public void printHelp()
	{
		logger.log("Usage: java -jar ClassFinder.jar|com.optit.ClassFinder -d [directory] -c [classname] -m -verbose -help|-h|--help|-?");
        logger.log("");
        logger.log("[-d]			The directory to search in");
        logger.log("[-c]			The classname to search for");
        logger.log("[-m]			Match case");
        logger.log("[-verbose]			Enables verbose output");
        logger.log("[-help|--help|-h|-?]	Display this help");
        logger.log();
        logger.log("The directory specified will be searched recursviely.");
        logger.log("The class name can either just be the class name (e.g. String) or the fully qualified one (e.g. java.lang.String)");
        logger.log();
        logger.log("Good hunting!");
	}
	
	/**
	 * This function sits on the top level and catches all exceptions and prints out proper error messages 
	 * @param e The exception that comes from somewhere within the code
	 */
	public void handleExceptions(Exception e)
	{
		logger.log("Application error: " + e.getMessage());
		if (e.getCause() != null)
		{
			logger.log("Caused by: " + e.getCause().toString());
		}
		e.printStackTrace(System.err);
	}
	
	// Find the class
	public void findClass()
	{
		boolean verbose = parameters.getProperty(Parameters.verbose).equals("true");

		// Class name contains some package qualifiers
		boolean containsPackageQualifier = (parameters.getProperty(Parameters.classname).indexOf(".") != -1); 
		// Change "." in package names to slashes (e.g. "org.apache.commons" -> "org/apache/commons")
		String classname = parameters.getProperty(Parameters.classname).replaceAll("\\.", "/");

		
		// Get file tree of directory
		buildFileList(new File(parameters.getProperty(Parameters.directory)));
		
		Iterator<File> fileIterator = files.iterator();
		// Loop over all the filtered files
		while (fileIterator.hasNext())
		{
			File file = fileIterator.next();
			
			if (verbose && logger instanceof CommandLineLogger)
			{
				// Use full qualified file name for logging, not the \ replaced one
				logger.log("Looking at: " + file.getAbsolutePath());
			}

			String fullFileName = file.getAbsolutePath().replaceAll("\\\\", "/");

			// Direct class files
			if (fullFileName.endsWith(".class"))
			{
				// IF:
				// Package qualifier was defined or a part of (e.g. apache.commons.Random -> apache/commons/Random)
				// AND
				// The file name ends with that qualifier --> org.apache.commons.Random.class ends with "apache/commons/Random.class)
				// --> CLASS FOUND!
				// OR
				// Package qualifier wasn't specified but just the class (e.g. Random)
				// AND
				// The FILE NAME (note the call to file.getName() rather than getAbsolutePath()) matches --> org.apache.commons.Random.class's file name is Random.class
				// --> CLASS FOUND!
				if ((containsPackageQualifier && fullFileName.endsWith(classname + ".class"))
					||
					(!containsPackageQualifier && file.getName().equals(classname + ".class")))
				{
					logger.log(classname, null, file.getAbsolutePath());
				}
			}
			// Direct java source file
			else if (fullFileName.endsWith(".java"))
			{
				// IF:
				// Package qualifier was defined or a part of (e.g. apache.commons.Random -> apache/commons/Random)
				// AND
				// The file name ends with that qualifier --> org.apache.commons.Random.java ends with "apache/commons/Random.java)
				// --> CLASS FOUND!
				// OR
				// Package qualifier wasn't specified but just the class (e.g. Random)
				// AND
				// The FILE NAME (note the call to file.getName() rather than getAbsolutePath()) matches --> org.apache.commons.Random.java's file name is Random.java
				// --> CLASS FOUND!
				if ((containsPackageQualifier && fullFileName.endsWith(classname + ".java"))
					||
					(!containsPackageQualifier && file.getName().equals(classname + ".java")))
				{
					logger.log(classname, null, file.getAbsolutePath());
				}
			}
			// The rest of the files: jar, war, ear, zip, rar
			else
			{
				try (JarFile jarFile = new JarFile(file))
				{
					Enumeration<JarEntry> entries = jarFile.entries();
					while(entries.hasMoreElements())
					{
						JarEntry entry = (JarEntry) entries.nextElement();
						
						if (containsPackageQualifier)
						{
							if (entry.getName().endsWith(classname + ".class") || entry.getName().endsWith(classname + ".java"))
							{
								logger.log(classname, file.getAbsolutePath(), entry.getName());
							}
						}
						// No package qualified, just Class Name
						else
						{
							// IF:
							// -- 95% scenario first: Class is in a sub package of the jar file
							// The Class name ends with "/Classname.class" OR "/Classname.java" (e.g. org.apache.commons.Random.class ends with "/Random.class")
							// OR
							// In the case that the class doesn't belong to any package or it was just a ZIPPED file of a .class
							// The file name equals classname.class or classname.java (e.g. Random.java got zipped up into Random.zip. The only thing in there is Random.java and as no package qualifier was given, the class is found)
							if (entry.getName().endsWith("/" + classname + ".class") || entry.getName().endsWith("/" + classname + ".java")
								|| 
								entry.getName().equals(classname + ".class") || entry.getName().equals(classname + ".java"))
							{
								logger.log(classname, file.getAbsolutePath(), entry.getName());
							}
						}
					}
				}
				catch (IOException e)
				{
					if (verbose)
					{
						logger.log("Error reading file " + fullFileName + ": " + e.getMessage());
						logger.logErr(e.getMessage());
					}
				}
			}
		}
	}
	
	public void buildFileList(File directory)
	{
		if (!directory.exists())
		{
			logger.log("Directory \"" + directory.getAbsolutePath() + "\" does not exist!");
		}
		
		// File is directly passed on, no directory serach necessary
		if (!directory.isDirectory() && new SearchableFileFilter().accept(directory))
		{
			files.add(directory);
		}
		else
		{
			for (File file : directory.listFiles(new SearchableFileFilter()))
			{
				if (file.isDirectory())
				{
					buildFileList(file);
				}
				else
				{
					files.add(file);
				}
			}
		}
	}
}
