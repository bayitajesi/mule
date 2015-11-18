/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.internal.connection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mule.api.MuleContext;
import org.mule.api.connection.ConnectionProvider;
import org.mule.api.connection.ManagedConnection;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;
import org.mule.tck.testmodels.fruit.Apple;
import org.mule.tck.testmodels.fruit.Banana;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class NullManagementStrategyTestCase extends AbstractMuleTestCase
{

    @Mock
    private ConnectionProvider<Apple, Banana> connectionProvider;

    @Mock
    private Apple config;

    @Mock
    private Banana connection;

    @Mock
    private MuleContext muleContext;

    private NullManagementStrategy<Apple, Banana> strategy;

    @Before
    public void before() throws Exception
    {
        when(connectionProvider.connect(config)).thenReturn(connection);
        strategy = new NullManagementStrategy<>(config, connectionProvider, muleContext);
    }

    @Test
    public void getConnection() throws Exception
    {
        ManagedConnection<Banana> managedConnection = strategy.getManagedConnection();
        assertThat(managedConnection.getConnection(), is(sameInstance(connection)));
    }

    @Test
    public void close() throws Exception
    {
        strategy.close();
        verify(connectionProvider, never()).disconnect(any(Banana.class));
    }
}
