/**
 * C-JDBC: Clustered JDBC.
 * Copyright (C) 2002-2005 French National Institute For Research In Computer
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
 * Contributor(s): Jean-Bernard van Zuylen.
 */

package org.objectweb.cjdbc.controller.scheduler.singledb;

import java.sql.SQLException;

import org.objectweb.cjdbc.common.exceptions.RollbackException;
import org.objectweb.cjdbc.common.sql.AbstractWriteRequest;
import org.objectweb.cjdbc.common.sql.ParsingGranularities;
import org.objectweb.cjdbc.common.sql.SelectRequest;
import org.objectweb.cjdbc.common.sql.StoredProcedure;
import org.objectweb.cjdbc.common.util.Constants;
import org.objectweb.cjdbc.common.xml.DatabasesXmlTags;
import org.objectweb.cjdbc.controller.requestmanager.RAIDbLevels;
import org.objectweb.cjdbc.controller.scheduler.AbstractScheduler;
import org.objectweb.cjdbc.controller.scheduler.schema.TransactionExclusiveLock;

/**
 * This scheduler provides transaction level scheduling for a SingleDB. Each
 * write takes a lock on the whole database. All following writes are blocked
 * until the transaction of the first write completes.
 * 
 * @author <a href="mailto:Emmanuel.Cecchet@inria.fr">Emmanuel Cecchet </a>
 * @version 1.0
 */
public class SingleDBPessimisticTransactionLevelScheduler
    extends AbstractScheduler
{

  //
  // How the code is organized ?
  //
  // 1. Member variables
  // 2. Constructor
  // 3. Request handling
  // 4. Transaction management
  // 5. Debug/Monitoring
  //

  private long             requestId;
  TransactionExclusiveLock lock = new TransactionExclusiveLock();

  //
  // Constructor
  //

  /**
   * Creates a new Pessimistic Transaction Level Scheduler
   */
  public SingleDBPessimisticTransactionLevelScheduler()
  {
    super(RAIDbLevels.SingleDB, ParsingGranularities.NO_PARSING);
  }

  //
  // Request Handling
  //

  /**
   * Additionally to scheduling the request, this method replaces the SQL Date
   * macros such as now() with the current date.
   * 
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#scheduleReadRequest(SelectRequest)
   */
  public final synchronized void scheduleReadRequest(SelectRequest request)
      throws SQLException
  {
    request.setId(requestId++);
  }

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#readCompletedNotify(SelectRequest)
   */
  public final void readCompletedNotify(SelectRequest request)
  {
  }

  /**
   * Additionally to scheduling the request, this method replaces the SQL Date
   * macros such as now() with the current date.
   * 
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#scheduleWriteRequest(AbstractWriteRequest)
   */
  public void scheduleNonSuspendedWriteRequest(AbstractWriteRequest request)
      throws SQLException
  {
    if (lock.acquire(request))
    {
      synchronized (this)
      {
        request.setId(requestId++);
      }
      if (logger.isDebugEnabled())
        logger.debug("Request " + request.getId() + " scheduled for write ("
            + getPendingWrites() + " pending writes)");
    }
    else
    {
      if (logger.isWarnEnabled())
        logger.warn("Request " + request.getId() + " timed out ("
            + request.getTimeout() + " s)");
      throw new SQLException("Timeout (" + request.getTimeout()
          + ") for request: " + request.getId());
    }
  }

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#notifyWriteCompleted(AbstractWriteRequest)
   */
  public final synchronized void notifyWriteCompleted(
      AbstractWriteRequest request)
  {
    // Requests outside transaction delimiters must release the lock
    // as soon as they have executed
    if (request.isAutoCommit())
      releaseLock(request.getTransactionId());
  }

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#scheduleNonSuspendedStoredProcedure(org.objectweb.cjdbc.common.sql.StoredProcedure)
   */
  public final synchronized void scheduleNonSuspendedStoredProcedure(
      StoredProcedure proc) throws SQLException, RollbackException
  {
    if (lock.acquire(proc))
    {
      synchronized (this)
      {
        proc.setId(requestId++);
      }
      if (logger.isDebugEnabled())
        logger.debug("Stored procedure " + proc.getId()
            + " scheduled for write (" + getPendingWrites()
            + " pending writes)");
    }
    else
    {
      if (logger.isWarnEnabled())
        logger.warn("Stored procedure " + proc.getId() + " timed out ("
            + proc.getTimeout() + " s)");
      throw new SQLException("Timeout (" + proc.getTimeout()
          + ") for request: "
          + proc.getSQLShortForm(Constants.SQL_SHORT_FORM_LENGTH));
    }
  }

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#notifyStoredProcedureCompleted(org.objectweb.cjdbc.common.sql.StoredProcedure)
   */
  public final void notifyStoredProcedureCompleted(StoredProcedure proc)
  {
    // Requests outside transaction delimiters must release the lock
    // as soon as they have executed
    if (proc.isAutoCommit() && (!proc.isCreate()))
      releaseLock(proc.getTransactionId());
  }

  //
  // Transaction Management
  //

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#commitTransaction(long)
   */
  protected final void commitTransaction(long transactionId)
  {
    releaseLock(transactionId);
  }

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#rollbackTransaction(long)
   */
  protected final void rollbackTransaction(long transactionId)
  {
    releaseLock(transactionId);
  }

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#rollbackTransaction(long,
   *      String)
   */
  protected final void rollbackTransaction(long transactionId,
      String savepointName)
  {
  }

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#setSavepointTransaction(long,
   *      String)
   */
  protected final void setSavepointTransaction(long transactionId, String name)
  {
  }

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#releaseSavepointTransaction(long,
   *      String)
   */
  protected final void releaseSavepointTransaction(long transactionId,
      String name)
  {
  }

  /**
   * Release the locks we may own on the schema.
   * 
   * @param transactionId id of the transaction that releases the lock
   */
  private void releaseLock(long transactionId)
  {
    // Are we the lock owner ?
    if (lock.isLocked())
    {
      if (lock.getLocker() == transactionId)
        lock.release();

      // Note that the following warnings could be safely ignored if the
      // transaction
      // commiting/rolllbacking (releasing the lock) has not done any
      // conflicting write
      else if (logger.isDebugEnabled())
        logger.debug("Transaction " + transactionId
            + " wants to release the lock held by transaction "
            + lock.getLocker());
    }
    else if (logger.isDebugEnabled())
      logger.warn("Transaction " + transactionId
          + " tries to release a lock that has not been acquired.");
  }

  //
  // Debug/Monitoring
  //

  /**
   * @see org.objectweb.cjdbc.controller.scheduler.AbstractScheduler#getXmlImpl()
   */
  public String getXmlImpl()
  {
    return "<" + DatabasesXmlTags.ELT_SingleDBScheduler + " "
        + DatabasesXmlTags.ATT_level + "=\""
        + DatabasesXmlTags.VAL_pessimisticTransaction + "\"/>";
  }
}
