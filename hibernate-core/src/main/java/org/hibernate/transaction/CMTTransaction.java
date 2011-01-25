/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 *
 */
package org.hibernate.transaction;

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import org.hibernate.HibernateException;
import org.hibernate.Logger;
import org.hibernate.Transaction;
import org.hibernate.TransactionException;
import org.hibernate.engine.jdbc.spi.JDBCContext;
import org.hibernate.util.JTAHelper;

/**
 * Implements a basic transaction strategy for CMT transactions. All work is
 * done in the context of the container managed transaction.
 * <p/>
 * The term 'CMT' is potentially misleading here; the pertinent point simply
 * being that the transactions are being managed by something other than the
 * Hibernate transaction mechanism.
 *
 * @author Gavin King
 */
public class CMTTransaction implements Transaction {

    private static final Logger LOG = org.jboss.logging.Logger.getMessageLogger(Logger.class,
                                                                                CMTTransaction.class.getPackage().getName());

	protected final JDBCContext jdbcContext;
	protected final TransactionFactory.Context transactionContext;

	private boolean begun;

	public CMTTransaction(JDBCContext jdbcContext, TransactionFactory.Context transactionContext) {
		this.jdbcContext = jdbcContext;
		this.transactionContext = transactionContext;
	}

	/**
	 * {@inheritDoc}
	 */
	public void begin() throws HibernateException {
		if (begun) {
			return;
		}

        LOG.debug("Begin");

		boolean synchronization = jdbcContext.registerSynchronizationIfPossible();

		if ( !synchronization ) {
			throw new TransactionException("Could not register synchronization for container transaction");
		}

		begun = true;

		jdbcContext.afterTransactionBegin(this);
	}

	/**
	 * {@inheritDoc}
	 */
	public void commit() throws HibernateException {
		if (!begun) {
			throw new TransactionException("Transaction not successfully started");
		}

        LOG.debug("Commit");

		boolean flush = !transactionContext.isFlushModeNever() &&
		        !transactionContext.isFlushBeforeCompletionEnabled();

		if (flush) {
			transactionContext.managedFlush(); //if an exception occurs during flush, user must call rollback()
		}

		begun = false;

	}

	/**
	 * {@inheritDoc}
	 */
	public void rollback() throws HibernateException {
		if (!begun) {
			throw new TransactionException("Transaction not successfully started");
		}

        LOG.debug("Rollback");

		try {
			getTransaction().setRollbackOnly();
		}
		catch (SystemException se) {
            LOG.unableToSetTransactionToRollbackOnly(se);
			throw new TransactionException("Could not set transaction to rollback only", se);
		}

		begun = false;

	}

	/**
	 * Getter for property 'transaction'.
	 *
	 * @return Value for property 'transaction'.
	 */
	public javax.transaction.Transaction getTransaction() throws SystemException {
		return transactionContext.getFactory().getTransactionManager().getTransaction();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isActive() throws TransactionException {

		if (!begun) return false;

		final int status;
		try {
			status = getTransaction().getStatus();
		}
		catch (SystemException se) {
            LOG.error(LOG.unableToDetermineTransactionStatus(), se);
            throw new TransactionException(LOG.unableToDetermineTransactionStatus(), se);
		}
		if (status==Status.STATUS_UNKNOWN) {
            throw new TransactionException(LOG.unableToDetermineTransactionStatus());
		}
		else {
			return status==Status.STATUS_ACTIVE;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean wasRolledBack() throws TransactionException {

		if (!begun) return false;

		final int status;
		try {
			status = getTransaction().getStatus();
		}
		catch (SystemException se) {
            LOG.error(LOG.unableToDetermineTransactionStatus(), se);
            throw new TransactionException(LOG.unableToDetermineTransactionStatus(), se);
		}
		if (status==Status.STATUS_UNKNOWN) {
            throw new TransactionException(LOG.unableToDetermineTransactionStatus());
		}
		else {
			return JTAHelper.isRollback(status);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean wasCommitted() throws TransactionException {

		if ( !begun ) return false;

		final int status;
		try {
			status = getTransaction().getStatus();
		}
		catch (SystemException se) {
            LOG.error(LOG.unableToDetermineTransactionStatus(), se);
            throw new TransactionException(LOG.unableToDetermineTransactionStatus(), se);
		}
		if (status==Status.STATUS_UNKNOWN) {
            throw new TransactionException(LOG.unableToDetermineTransactionStatus());
		}
		else {
			return status==Status.STATUS_COMMITTED;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerSynchronization(Synchronization sync) throws HibernateException {
		try {
			getTransaction().registerSynchronization(sync);
		}
		catch (Exception e) {
			throw new TransactionException("Could not register synchronization", e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void setTimeout(int seconds) {
		throw new UnsupportedOperationException("cannot set transaction timeout in CMT");
	}

}
