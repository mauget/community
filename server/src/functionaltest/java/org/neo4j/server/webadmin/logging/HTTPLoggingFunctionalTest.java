package org.neo4j.server.webadmin.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.containsString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.server.NeoServer;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.helpers.FunctionalTestHelper;
import org.neo4j.server.helpers.ServerBuilder;
import org.neo4j.server.helpers.ServerHelper;
import org.neo4j.server.rest.JaxRsResponse;
import org.neo4j.server.rest.RestRequest;
import org.neo4j.server.startup.healthcheck.HTTPLoggingPreparednessRule;
import org.neo4j.server.startup.healthcheck.StartupHealthCheckFailedException;
import org.neo4j.test.TargetDirectory;
import org.neo4j.test.server.ExclusiveServerTestBase;

public class HTTPLoggingFunctionalTest extends ExclusiveServerTestBase
{
    private NeoServer server;
    private static final String logDirectory = "target/test-data/impermanent-db/log";

    @Before
    public void cleanUp() throws IOException
    {
        ServerHelper.cleanTheDatabase( server );
        removeHttpLogs();
    }

    private void removeHttpLogs() throws IOException
    {
        File logDir = new File( logDirectory );
        if ( logDir.exists() )
        {
            FileUtils.deleteDirectory( logDir );
        }
    }

    @After
    public void stopServer()
    {
        if ( server != null )
        {
            server.stop();
        }
    }

    @Test
    public void givenExplicitlyDisabledServerLoggingConfigurationShouldNotLogAccesses() throws Exception
    {
        // given
        server = ServerBuilder.server().withDefaultDatabaseTuning()
            .withProperty( Configurator.HTTP_LOGGING, "false" )
            .withProperty( Configurator.HTTP_LOG_CONFIG_LOCATION,
                getClass().getResource( "/neo4j-server-test-logback.xml" ).getFile() )
            .build();
        server.start();
        FunctionalTestHelper functionalTestHelper = new FunctionalTestHelper( server );

        // when
        String query = "?implicitlyDisabled" + UUID.randomUUID().toString();
        JaxRsResponse response = new RestRequest().get( functionalTestHelper.webAdminUri() + query );
        assertEquals( 200, response.getStatus() );
        response.close();

        // then
        assertFalse( occursIn( query, new File( logDirectory + File.separator + "http.log" ) ) );
    }

    @Test
    public void givenExplicitlyEnabledServerLoggingConfigurationShouldLogAccess() throws Exception
    {
        // given
        server = ServerBuilder.server().withDefaultDatabaseTuning()
            .withProperty( Configurator.HTTP_LOGGING, "true" )
            .withProperty( Configurator.HTTP_LOG_LOCATION, logDirectory )
            .withProperty( Configurator.HTTP_LOG_CONFIG_LOCATION,
                getClass().getResource( "/neo4j-server-test-logback.xml" ).getFile() )
            .build();
        server.start();

        FunctionalTestHelper functionalTestHelper = new FunctionalTestHelper( server );

        // when
        String query = "?explicitlyEnabled=" + UUID.randomUUID().toString();
        JaxRsResponse response = new RestRequest().get( functionalTestHelper.webAdminUri() + query );
        assertEquals( 200, response.getStatus() );
        response.close();

        // then
        assertTrue( occursIn( query, new File( logDirectory + File.separator + "http.log" ) ) );
    }

    @Test
    public void givenConfigurationWithUnwritableLogDirectoryShouldFailToStartServer() throws Exception
    {
        // given
        final String unwritableLogDir = createUnwritableDirectory().getAbsolutePath();
        server = ServerBuilder.server().withDefaultDatabaseTuning()
            .withStartupHealthCheckRules( new HTTPLoggingPreparednessRule() )
            .withProperty( Configurator.HTTP_LOGGING, "true" )
            .withProperty( Configurator.HTTP_LOG_LOCATION, unwritableLogDir )
            .withProperty( Configurator.HTTP_LOG_CONFIG_LOCATION,
                getClass().getResource( "/neo4j-server-test-logback.xml" ).getFile() )
            .build();

        // when
        try
        {
            server.start();
            fail( "should have thrown exception" );
        }
        catch ( StartupHealthCheckFailedException e )
        {
            // then
            assertThat( e.getMessage(),
                containsString( String.format( "HTTP log directory [%s] is not writable", unwritableLogDir ) ) );
        }

    }

    private File createUnwritableDirectory()
    {
        TargetDirectory targetDirectory = TargetDirectory.forTest( this.getClass() );

        final File file = targetDirectory.directory( "unwritable" );
        file.setWritable( false, false );

        System.out.println( "file = " + file );


        return file;
    }

    private boolean occursIn( String lookFor, File file ) throws FileNotFoundException
    {
        if ( !file.exists() )
        {
            return false;
        }

        boolean result = false;
        Scanner scanner = new Scanner( file );
        while ( scanner.hasNext() )
        {
            if ( scanner.next().contains( lookFor ) )
            {
                result = true;
            }
        }

        scanner.close();

        return result;
    }
}