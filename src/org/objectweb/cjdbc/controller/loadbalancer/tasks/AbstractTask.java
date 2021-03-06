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
 * Initial developer(s): Emmanuel Cecchet.
 * Contributor(s): Jaco Swart.
 */

package org.objectweb.cjdbc.controller.loadbalancer.tasks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.objectweb.cjdbc.common.exceptions.SQLExceptionFactory;
import org.objectweb.cjdbc.controller.loadbalancer.BackendWorkerThread;

/**
 * Defines an abstract task to be processed by a
 * <code>BackendWorkerThread</code>.
 * 
 * @author <a href="mailto:Emmanuel.Cecchet@inria.fr">Emmanuel Cecchet </a>
 * @author <a href="mailto:jaco.swart@iblocks.co.uk">Jaco Swart </a>
 * @version 1.0
 */
public abstract class AbstractTask
{
  //
  // How the code is organized ?
  // 1. Member variables
  // 2. Constructor(s)
  // 3. Task management
  // 4. Getter/Setter
  //

  /** Total number of threads. */
  private int       totalNb;

  /** Number of threads that must succeed before returning. */
  private int       nbToComplete;

  /** Number of thread that have started the execution of the task */
  private int       executionStarted;
  /** Number of backendThread that have succeeded */
  private int       success        = 0;
  /** Number of backendThread that have failed */
  private int       failed         = 0;
  /** List of exceptions of failed nodes */
  private ArrayList exceptions     = null;

  // Internal tid flag used by BackendWorkerThread
  private boolean   hasTid         = false;

  // ResultSet for getting back autogenerated keys in an update with keys
  private ResultSet generatedKeysResultSet;

  // True if the timeout has expired on this task
  private boolean   timeoutExpired = false;

  /*
   * Constructor
   */

  /**
   * Sets the number of threads among the total number of threads that must
   * successfully complete the execution of this AbstractTask before returning.
   * 
   * @param nbToComplete number of threads that must succeed before returning
   * @param totalNb total number of threads
   */
  public AbstractTask(int nbToComplete, int totalNb)
  {
    this.nbToComplete = nbToComplete;
    this.totalNb = totalNb;
    success = 0;
    failed = 0;
    executionStarted = 0;
  }

  /*
   * Task management
   */

  /**
   * The task code executed by the backendThread.
   * 
   * @param backendThread The backend thread executing this task
   * @throws SQLException if an error occurs
   */
  public void execute(BackendWorkerThread backendThread) throws SQLException
  {
    synchronized (this)
    {
      // If the task has expired and nobody has executed it yet, we ignore it
      // else we have to play it.
      // Note that the exception corresponding to the timeout is set by the
      // caller of setExpiredTimeout.
      if (timeoutExpired && (executionStarted == 0))
        return;
      this.executionStarted++;
    }
    executeTask(backendThread);
    // Completed executions are handled by the task internal code that calls
    // notifyFailure or notifySuccess.
  }

  /**
   * The implementation specific task code to be executed by backendThread.
   * 
   * @param backendThread The backend thread executing this task
   * @throws SQLException if an error occurs
   */
  public abstract void executeTask(BackendWorkerThread backendThread)
      throws SQLException;

  /**
   * Notifies the successful completion of this task.
   */
  public synchronized void notifySuccess()

  {
    success++;

    // Notify if needed
    if ((success == nbToComplete) || (success + failed == totalNb))
    {
      if (failed > 0)
        notifyAll(); // Notify all failed threads too
      else
        notify();
    }
  }

  /**
   * This is used to notify the completion of this task without success or
   * failure. This is usually used when the task has been discarded for example
   * by a backend that is currently disabling but still needs to execute the
   * remaining queries in open transactions.
   * <p>
   * Therefore, this only decrements by one the number of threads that needs to
   * complete.
   */
  public synchronized void notifyCompletion()
  {
    totalNb--;
    // Notify if needed
    if (success + failed == totalNb)
    {
      notifyAll(); // Notify all failed threads
    }
  }

  /**
   * Notifies that the specified backendThread failed to execute this task. If
   * all nodes failed, this method return <code>false</code> meaning that the
   * problem was due to the task and not to the thread. If the method returns
   * <code>true</code>, it can mean that this thread failed and is no more
   * coherent, therefore the backend associated to this thread should be
   * disabled.
   * 
   * @param backendThread the backend thread that has failed
   * @param timeout time in milliseconds to wait for other threads to signal
   *          success or failure
   * @param e the exception causing the failure
   * @return <code>true</code> if at least one node succeeded to execute this
   *         task, <code>false</code> if all threads failed
   * @throws SQLException if an error occured in the notification process
   */
  public synchronized boolean notifyFailure(BackendWorkerThread backendThread,
      long timeout, Exception e) throws SQLException
  {
    failed++;

    // Log the exception
    if (exceptions == null)
      exceptions = new ArrayList();
    if (e instanceof SQLException)
    {
      SQLException sqlEx = (SQLException) e;
      exceptions.add(SQLExceptionFactory.getSQLException(sqlEx,
          "BackendThread " + backendThread.getBackend().getName() + " failed ("
              + sqlEx.getLocalizedMessage() + ")"));
    }
    else
      exceptions.add(new SQLException("BackendThread "
          + backendThread.getBackend().getName() + " failed ("
          + e.getLocalizedMessage() + ")").initCause(e));

    // Notify if needed
    if (success + failed == totalNb)
    {
      notifyAll(); // Notify all failed threads
    }
    else
    {
      try
      { // Wait to check if all other threads failed or not
        wait(timeout);
      }
      catch (InterruptedException ie)
      {
        throw (SQLException) new SQLException(
            "Wait interrupted() in failed task of backend "
                + backendThread.getBackend().getName() + " ("
                + e.getLocalizedMessage() + ")").initCause(e);
      }
    }
    return success > 0;
  }

  //
  // Getter/Setter
  //

  /**
   * Returns the exceptions lists.
   * 
   * @return an <code>ArrayList</code>
   */
  public ArrayList getExceptions()
  {
    return exceptions;
  }

  /**
   * Returns the number of threads that have started the execution of the task.
   * 
   * @return Returns the number of started executions.
   */
  public synchronized int getExecutionStarted()
  {
    return executionStarted;
  }

  /**
   * Returns the failed.
   * 
   * @return an <code>int</code> value
   */
  public int getFailed()
  {
    return failed;
  }

  /**
   * Returns the number of threads that must succeed before returning.
   * 
   * @return an <code>int</code> value
   */
  public int getNbToComplete()
  {
    return nbToComplete;
  }

  /**
   * Returns the success.
   * 
   * @return an <code>int</code> value
   */
  public int getSuccess()
  {
    return success;
  }

  /**
   * Returns the total number of threads.
   * 
   * @return an <code>int</code> value
   * @see #setTotalNb
   */
  public int getTotalNb()
  {
    return totalNb;
  }

  /**
   * Sets the total number of threads.
   * 
   * @param totalNb the total number of threads to set
   * @see #getTotalNb
   */
  public void setTotalNb(int totalNb)
  {
    this.totalNb = totalNb;
  }

  /**
   * Returns true if the task has been sucessfully completed by nbToComplete
   * nodes (set in the constructor) of if everyone has completed (succesfully or
   * not), false otherwise.
   * 
   * @return true if the task execution is complete
   * @see AbstractTask#AbstractTask(int, int)
   */
  public synchronized boolean hasCompleted()
  {
    return ((success >= nbToComplete) || (success + failed == totalNb));
  }

  /**
   * Returns true if the task has completed (succesfully or not) or false if we
   * are still expecting answers from some backends.
   * 
   * @return true if the task execution is complete
   */
  public synchronized boolean hasFullyCompleted()
  {
    return success + failed == totalNb;
  }

  /**
   * Returns true if this task has a tid attached to it.
   * <p>
   * Used internally by BackendWorkerThread.
   * 
   * @return Returns the hasTid.
   */
  public boolean hasTid()
  {
    return hasTid;
  }

  /**
   * Sets the hasTid value.
   * <p>
   * Used internally by BackendWorkerThread.
   * 
   * @param hasTid The hasTid to set.
   */
  public void setHasTid(boolean hasTid)
  {
    this.hasTid = hasTid;
  }

  /**
   * Returns the generatedKeysResultSet value.
   * 
   * @return Returns the generatedKeysResultSet.
   */
  public ResultSet getGeneratedKeysResultSet()
  {
    return generatedKeysResultSet;
  }

  /**
   * Sets the generatedKeysResultSet value.
   * 
   * @param generatedKeysResultSet The generatedKeysResultSet to set.
   */
  public void setGeneratedKeysResultSet(ResultSet generatedKeysResultSet)
  {
    this.generatedKeysResultSet = generatedKeysResultSet;
  }

  /**
   * Set the flag to tell that the timeout has expired on this task. If no
   * backend has started the task execution then the task will be canceled and
   * the method will return true. Otherwise, all backends will execute the
   * request and the method will return false.
   * 
   * @return true if BackendThreads will ignore the task, false if all backends
   *         will execute the task.
   */
  public synchronized boolean setExpiredTimeout()
  {
    this.timeoutExpired = true;
    return executionStarted == 0;
  }

}
