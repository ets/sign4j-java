package net.rarobertson.sign4j.java;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class App
{
	public static final byte[] ZIP_END_HEADER =
	{
		0x50, 0x4B, 0x05, 0x06
	};
	public static final int END_HEADER_SIZE = 22;
	public static final int MAX_COMMENT_SIZE = 0xFFFF;

	private static boolean verbose, quotes;
	
	public static void main(String[] args)
	{
		// Enough parameters?
		if (args.length < 2)
		{
			displayUsage();
			return;
		}
		
		int i;
		
		// Command options
		for (i = 0; i < args.length; i++)
		{
			if (args[i].equals("-v"))
				verbose = true;
			else if (args[i].equals("-q"))
				quotes = true;
			else
				break;
		}
		
		// Enough parameters?
		if (args.length < 2 + i)
		{
			displayUsage();
			return;
		}
		
		File inputFile = new File(args[i]);
		if (verbose)
			System.out.println(">Using input file \"" + args[i] + "\"");
		
		if (!inputFile.exists())
		{
			System.out.println("Could not find file \"" + args[i] + "\"");
			System.out.println();
			return;
		}
		
		// Combine the rest of the arguments to a single string
		String command;
		if (quotes)
		{
			command = "\"" + args[1] + "\"";
			for (i++; i < args.length; i++)
				command += " \"" + args[i] + "\"";
		}
		else
		{
			command = args[1];
			for (i++; i < args.length; i++)
				command += " " + args[i];
		}
		
		int originalLength = (int)inputFile.length();
		
		if (verbose)
		{
			System.out.println(">Signing command line: " + command);
			System.out.println(">Original file length: " + originalLength);
		}
		
		FileInputStream inputStream;
		
		try
		{
			inputStream = new FileInputStream(inputFile);
		}
		catch (FileNotFoundException ex)
		{
			System.out.println("If you see this, Java broke");
			System.out.println(ex.toString());
			return;
		}
		
		// Buffer for reading from file, smallest possible buffer we can get away with
		byte[] buffer = new byte[END_HEADER_SIZE + MAX_COMMENT_SIZE < originalLength ? END_HEADER_SIZE + MAX_COMMENT_SIZE : originalLength];
		// Initialize so we know if we found one
		int commentLength = -1;
		// Initialize because Java doesn't realize I'm assigning it or exiting so gives a compiler error
		int offset = 0;
		
		// Locate the ZIP 'end of central directory' header and find the comment-length value
		try
		{
			inputStream.getChannel().position(inputFile.length() - buffer.length);
			inputStream.read(buffer, 0, buffer.length);
			for (int p = buffer.length - END_HEADER_SIZE - 1; p >= 0; p--)
				if (segmentMatch(buffer, p, ZIP_END_HEADER))
				{
					commentLength = getShort(buffer, p + END_HEADER_SIZE - 2);
					if (commentLength == buffer.length - (p + END_HEADER_SIZE))
					{
						offset = (int)(inputFile.length() - buffer.length + p + END_HEADER_SIZE - 2);
						break;
					}
					else
						commentLength = -1;
				}
			if (commentLength == -1)
			{
				System.out.println("ZIP ending header not found");
				System.out.println("This file probably doesn't contain a JAR");
				return;
			}
		}
		catch (IOException ex)
		{
			System.out.println("Error reading input file.");
			System.out.println(ex.toString());
			return;
		}
		finally
		{
			try
			{
				inputStream.close();
			}
			catch (Exception ex)
			{
				// Good job Java. You failed to close a file that was only being read
			}
		}
		
		if (verbose)
		{
			System.out.println(">Found comment length: " + commentLength);
			System.out.println(">Running command");
		}
		
		int commandResult = system(command);
		
		if (verbose)
			System.out.println(">Command result: " + commandResult);
		
		FileOutputStream outputStream;
		
		try
		{
			outputStream = new FileOutputStream(inputFile);
		}
		catch (IOException ex)
		{
			System.out.println("Could not open file for writing");
			System.out.println(ex.toString());
			return;
		}
		
		// Change the ZIP end header to pretend the signed data added to the end is part of the ZIP comment
		try
		{
			outputStream.getChannel().position(offset);
			outputStream.write(getShort((short)(commentLength + inputFile.length() - originalLength)));
		}
		catch (IOException ex)
		{
			System.out.println("Could not write new comment length to file");
			System.out.println(ex.toString());
			return;
		}
		finally
		{
			try
			{
				outputStream.close();
			}
			catch (Exception ex)
			{
				System.out.println("Exception closing file after writing. File may be damaged.");
				System.out.println(ex.toString());
				return;
			}
		}
		
		if (verbose)
		{
			System.out.println(">Wrote two bytes to file");
			System.out.println(">Finished");
		}
	}
	
	private static void displayUsage()
	{
		System.out.println("To use this program, you must pass at least two parameters in the form:");
		System.out.println("sign4j-java [options] <input filename> <sign command>");
		System.out.println("Options");
		System.out.println("-v      Verbose output of variables and debug statements");
		System.out.println("-q      Add quotes to parameters of signing command line");
		System.out.println();
	}
	
	private static boolean segmentMatch(byte[] source, int start, byte[] find)
	{
		for (int i = 0; i < find.length && i < source.length - start; i++)
			if (source[start + i] != find[i])
				return false;
		return true;
	}
	
	private static short getShort(byte[] source, int start)
	{
		return (short)(source[start + 1] | (source[start] << 8));
	}
	
	private static byte[] getShort(short value)
	{
		byte[] ret = new byte[2];
		ret[0] = (byte)((value >> 8) | 0xFF);
		ret[1] = (byte)(value | 0xFF);
		return ret;
	}
	
	// Like C's system function
	private static int system(String command)
	{
		try
		{
			Process process = Runtime.getRuntime().exec(command);
			process.waitFor();
			return process.exitValue();
		}
		catch (Exception ex)
		{
			System.out.println("Exception executing: " + command);
			System.out.println(ex.toString());
			return -1;
		}
	}
}
