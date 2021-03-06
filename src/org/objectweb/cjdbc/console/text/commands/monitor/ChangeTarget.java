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

package org.objectweb.cjdbc.console.text.commands.monitor;

import org.objectweb.cjdbc.common.i18n.ConsoleTranslate;
import org.objectweb.cjdbc.console.text.ConsoleException;
import org.objectweb.cjdbc.console.text.module.MonitorConsole;

/**
 * This class defines a ChangeTarget
 * 
 * @author <a href="mailto:Nicolas.Modrzyk@inria.fr">Nicolas Modrzyk </a>
 * @version 1.0
 */
public class ChangeTarget extends AbstractMonitorCommand
{

  /**
   * Creates a new <code>ChangeTarget.java</code> object
   * 
   * @param module monitoring module
   */
  public ChangeTarget(MonitorConsole module)
  {
    super(module);
  }

  /**
   * @see org.objectweb.cjdbc.console.text.commands.ConsoleCommand#parse(java.lang.String)
   */
  public void parse(String commandText) throws Exception
  {
    try
    {
      String target = commandText.trim();
      String possibleTarget = target.trim();
      if (target == null || target.equals(""))
      {
        module.setCurrentTarget(jmxClient.getRemoteName());
      } else
      {
        if (jmxClient.getDataCollectorProxy()
            .hasVirtualDatabase(possibleTarget))
        {
          // change the target for all commands
          module.setCurrentTarget(possibleTarget);
        } else
          console.printError(ConsoleTranslate.get(
              "module.database.invalid", possibleTarget));
      }
    } catch (Exception e)
    {
      throw new ConsoleException("monitor.command.settarget.failed",e);
    }

  }

  /**
   * @see org.objectweb.cjdbc.console.text.commands.ConsoleCommand#getCommandName()
   */
  public String getCommandName()
  {
    return "setTarget";
  }

  /**
   * @see org.objectweb.cjdbc.console.text.commands.ConsoleCommand#getCommandDescription()
   */
  public String getCommandDescription()
  {
    return ConsoleTranslate.get("monitor.command.settarget");
  }

}