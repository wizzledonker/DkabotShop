//Code taken and modified from Essentials, as are ItemDb.java and items.csv, though items.csv is not marked as such.
package com.dkabot.DkabotShop;

import java.io.*;
import java.math.BigInteger;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;


public class ManagedFile
{
	private final static int BUFFERSIZE = 1024 * 8;
	private final transient File file;

	public ManagedFile(final String filename, final DkabotShop plugin)
	{
		file = new File(plugin.getDataFolder(), filename);

		if (file.exists())
		{
			try
			{
				if (!file.delete())
				{
					throw new IOException("Could not delete file " + file.toString());
				}
			}
			catch (IOException ex)
			{
				Bukkit.getLogger().log(Level.SEVERE, ex.getMessage(), ex);
			}
		}

		if (!file.exists())
		{
			try
			{
				copyResourceAscii("/" + filename, file);
			}
			catch (IOException ex)
			{
				Bukkit.getLogger().log(Level.SEVERE, "File " + file.getName() + " is not loaded!", ex);
			}
		}
	}

	public static void copyResourceAscii(final String resourceName, final File file) throws IOException
	{
		final InputStreamReader reader = new InputStreamReader(ManagedFile.class.getResourceAsStream(resourceName));
		try
		{
			final MessageDigest digest = getDigest();
			final DigestOutputStream digestStream = new DigestOutputStream(new FileOutputStream(file), digest);
			try
			{
				final OutputStreamWriter writer = new OutputStreamWriter(digestStream);
				try
				{
					final char[] buffer = new char[BUFFERSIZE];
					do
					{
						final int length = reader.read(buffer);
						if (length >= 0)
						{
							writer.write(buffer, 0, length);
						}
						else
						{
							break;
						}
					}
					while (true);
					writer.write("\n");
					writer.flush();
					final BigInteger hashInt = new BigInteger(1, digest.digest());
					digestStream.on(false);
					digestStream.write('#');
					digestStream.write(hashInt.toString(16).getBytes());
				}
				finally
				{
					writer.close();
				}
			}
			finally
			{
				digestStream.close();
			}
		}
		finally
		{
			reader.close();
		}
	}

	public static MessageDigest getDigest() throws IOException
	{
		try
		{
			return MessageDigest.getInstance("MD5");
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IOException(ex);
		}
	}

	public List<String> getLines()
	{
		try
		{
			final BufferedReader reader = new BufferedReader(new FileReader(file));
			try
			{
				final List<String> lines = new ArrayList<String>();
				do
				{
					final String line = reader.readLine();
					if (line == null)
					{
						break;
					}
					else
					{
						lines.add(line);
					}
				}
				while (true);
				return lines;
			}
			finally
			{
				reader.close();
			}
		}
		catch (IOException ex)
		{
			Bukkit.getLogger().log(Level.SEVERE, ex.getMessage(), ex);
			return Collections.emptyList();
		}
	}
}