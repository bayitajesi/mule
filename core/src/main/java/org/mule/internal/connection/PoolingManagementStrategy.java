/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.internal.connection;

import static org.mule.config.i18n.MessageFactory.createStaticMessage;
import org.mule.api.DefaultMuleException;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.config.PoolingProfile;
import org.mule.api.connection.ConnectionException;
import org.mule.api.connection.ConnectionProvider;
import org.mule.api.connection.ManagedConnection;

import java.util.NoSuchElementException;

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;

/**
 * A {@link ManagementStrategyAdapter} which returns connections obtained from a {@link #pool}
 *
 * @param <Config>     the generic type of the objects to be used as configs
 * @param <Connection> the generic type of the connections to be managed
 * @since 4.0
 */
final class PoolingManagementStrategy<Config, Connection> extends ManagementStrategyAdapter<Config, Connection>
{

    private final PoolingProfile poolingProfile;
    private final ObjectPool<Connection> pool;

    /**
     * Creates a new instance
     *
     * @param config             the config for which connections are to be created
     * @param connectionProvider the {@link ConnectionProvider} used to manage the connections
     * @param poolingProfile     the {@link PoolingProfile} which configures the {@link #pool}
     * @param muleContext        the application's {@link MuleContext}
     */
    PoolingManagementStrategy(Config config,
                              ConnectionProvider<Config, Connection> connectionProvider,
                              PoolingProfile poolingProfile,
                              MuleContext muleContext)
    {
        super(config, connectionProvider, muleContext);
        this.poolingProfile = poolingProfile;

        pool = createPool();
    }

    /**
     * Returns a {@link ManagedConnection} which wraps a connection obtained from the
     * {@link #pool}
     *
     * @return a {@link ManagedConnection}
     * @throws ConnectionException if the connection could not be obtained
     */
    @Override
    public ManagedConnection<Connection> getManagedConnection() throws ConnectionException
    {
        try
        {
            return new PooledManagedConnection<>(pool.borrowObject(), pool);
        }
        catch (NoSuchElementException e)
        {
            throw new ConnectionException("Connection pool is exhausted");
        }
        catch (Exception e)
        {
            throw new ConnectionException("An exception was found trying to obtain a connection", e);
        }
    }

    /**
     * Closes the pool, causing the contained connections to be closed as well.
     *
     * @throws MuleException
     */
    //TODO: MULE-9082 - pool.close() doesn't destroy unreturned connections
    @Override
    public void close() throws MuleException
    {
        try
        {
            pool.close();
        }
        catch (Exception e)
        {
            throw new DefaultMuleException(createStaticMessage("Could not close connection pool"), e);
        }
    }

    private ObjectPool<Connection> createPool()
    {
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.maxIdle = poolingProfile.getMaxIdle();
        config.maxActive = poolingProfile.getMaxActive();
        config.maxWait = poolingProfile.getMaxWait();
        config.whenExhaustedAction = (byte) poolingProfile.getExhaustedAction();
        config.minEvictableIdleTimeMillis = poolingProfile.getMinEvictionMillis();
        config.timeBetweenEvictionRunsMillis = poolingProfile.getEvictionCheckIntervalMillis();

        GenericObjectPool genericPool = new GenericObjectPool(new ObjectFactoryAdapter(), config);

        return genericPool;
    }

    private class ObjectFactoryAdapter implements PoolableObjectFactory<Connection>
    {

        @Override
        public Connection makeObject() throws Exception
        {
            return connectionProvider.connect(config);
        }

        @Override
        public void destroyObject(Connection connection) throws Exception
        {
            connectionProvider.disconnect(connection);
        }

        @Override
        public boolean validateObject(Connection obj)
        {
            return false;
        }

        @Override
        public void activateObject(Connection obj) throws Exception
        {

        }

        @Override
        public void passivateObject(Connection obj) throws Exception
        {

        }
    }
}
