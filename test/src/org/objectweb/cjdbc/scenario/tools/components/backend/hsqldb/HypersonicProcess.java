/**
 * C-JDBC: Clustered JDBC.
 * Copyright (C) 2002-2004 French National Institute For Research In Computer
 * Science And Control (INRIA).
 * Contact: c-jdbc@objectweb.org
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2.1 of the License, or any later
 * version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 * Initial developer(s): Nicolas Modrzyk
 * Contributor(s): ______________________.
 */

package org.objectweb.cjdbc.scenario.tools.components.backend.hsqldb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

import org.objectweb.cjdbc.scenario.tools.ScenarioConstants;
import org.objectweb.cjdbc.scenario.tools.components.ComponentInterface;

/**
 * This class defines a HyperSonicProcess
 * 
 * @author <a href="mailto:Nicolas.Modrzyk@inria.fr">Nicolas Modrzyk </a>
 * @version 1.0
 */
public class HypersonicProcess implements ComponentInterface
{
  String  templateDir = "/database";
  String  jarFile     = "/hsqldb.jar";
  String  scriptFile  = "database-raidb1.template";
  String  port;
  String  database;
  File    dir;
  Process process;

  /**
   * Creates a new <code>HyperSonicProcess.java</code> object Start a new
   * independant process
   * 
   * @param port to start hypersonic on
   * @param database to load by default
   * @throws IOException if fails to start
   */
  public HypersonicProcess(String port, String database) throws IOException
  {
    this.port = port;
    this.database = database;
    start();
  }

  /**
   * @see org.objectweb.cjdbc.scenario.tools.components.ComponentInterface#start()
   */
  public void start() throws IOException
  {
    File file = new File(ScenarioConstants.PROCESS_DIRECTORY);
    dir = new File(file + File.separator + port);
    dir.mkdirs();
    dir.deleteOnExit();
    File jar = new File(getClass().getResource(jarFile).getFile());
    Runtime run = Runtime.getRuntime();
    String command = "java -Xmx128m -classpath " + jar.getAbsolutePath()
        + " org.hsqldb.Server -port " + port + " -database " + database;
    process = run.exec(command, null, dir);
  }

  /**
   * Returns the database value.
   * 
   * @return Returns the database.
   */
  public String getDatabase()
  {
    return database;
  }

  /**
   * Returns the port value.
   * 
   * @return Returns the port.
   */
  public String getPort()
  {
    return port;
  }

  /**
   * @see org.objectweb.cjdbc.scenario.tools.components.ComponentInterface#loadDatabase()
   */
  public void loadDatabase() throws Exception
  {
    loadDatabase(scriptFile);
  }

  /**
   * @see org.objectweb.cjdbc.scenario.tools.components.ComponentInterface#loadDatabase(java.lang.String)
   */
  public void loadDatabase(String templateName) throws Exception
  {
    Class.forName("org.hsqldb.jdbcDriver");
    Properties props = new Properties();
    props.put("user", "sa");
    props.put("password", "");
    Connection con = DriverManager.getConnection(
        "jdbc:hsqldb:hsql://localhost:" + port, props);
    Statement statement = con.createStatement();
    File file = new File(getClass().getResource(
        templateDir + "/" + templateName).getFile());
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line = "";
    while ((line = reader.readLine()) != null)
    {
      try
      {
        statement.executeQuery(line);
      }
      catch (Exception e)
      {
        /* Execute all the queries */
        //System.out.println(e.getMessage());
        //break;
      }
    }
  }

  /**
   * Returns the process associated to this database.
   * 
   * @return Returns the process.
   */
  public Object getProcess()
  {
    return process;
  }

  /**
   * Remove files generated by this process
   */
  public void release()
  {
    process.destroy();

    try
    {
      Connection c;
      while ((c = DriverManager.getConnection("jdbc:hsqldb:hsql://localhost/"
          + database + ":" + port, "sa", "")) != null)
      {
        try
        {
          System.out.println("Killing process");
          Statement s = c.createStatement();
          s.executeQuery("SHUTDOWN SQL");
          c.close();
        }
        catch (Exception e)
        {
          // ignore and then try again
        }
      }

    }
    catch (Exception e)
    {
      // cannot get connections anymore
    }

    if (dir == null || !dir.exists())
      return;
    File[] f = dir.listFiles();
    if (f != null)
    {
      for (int j = 0; j < f.length; j++)
        f[j].delete();
    }
    dir.delete();

    try
    {
      process.waitFor();
    }
    catch (InterruptedException e1)
    {
      e1.printStackTrace();
    }

    System.out.println("Hypersonic on port:" + port + " released.(Exit value:"
        + process.exitValue() + ")");
  }

  /**
   * @see org.objectweb.cjdbc.scenario.tools.components.ComponentInterface#loadDatabase(java.lang.String,
   *      java.lang.String)
   */
  public void loadDatabase(String xml, String targetDB) throws Exception
  {
    this.loadDatabase(xml);
  }
}