/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.internal.connection;

import org.mule.api.MuleException;
import org.mule.api.connection.ConnectionException;

import org.apache.commons.pool.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ManagedConnectionAdapter} which wraps a {@code Connection}
 * obtained from a {@link #pool}.
 *
 * @param <Connection> the generic type of the connection to be returned
 * @since 4.0
 */
final class PooledManagedConnection<Connection> implements ManagedConnectionAdapter<Connection>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(PooledManagedConnection.class);

    private final Connection connection;
    private final ObjectPool<Connection> pool;

    /**
     * Creates a new instance
     *
     * @param connection the connection to be wrapped
     * @param pool       the pool from which the {@code connection} was obtained and to which it has to be returned
     */
    PooledManagedConnection(Connection connection, ObjectPool<Connection> pool)
    {
        this.connection = connection;
        this.pool = pool;
    }

    /**
     * @return the {@link #connection}
     */
    @Override
    public Connection getConnection() throws ConnectionException
    {
        return connection;
    }

    /**
     * Returns the {@link #connection} to the {@link #pool}
     */
    @Override
    public void release()
    {
        try
        {
            pool.returnObject(connection);
        }
        catch (Exception e)
        {
            LOGGER.warn("Could not return connection to the pool. Connection has been destroyed", e);
        }
    }

    /**
     * Does nothing for this implementation. Connections are only closed
     * when the pool is.
     */
    @Override
    public void close() throws MuleException
    {

    }
}
