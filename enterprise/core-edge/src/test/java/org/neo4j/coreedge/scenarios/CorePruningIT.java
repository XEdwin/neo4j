/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.coreedge.scenarios;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import org.neo4j.coreedge.discovery.Cluster;
import org.neo4j.coreedge.discovery.TestOnlyDiscoveryServiceFactory;
import org.neo4j.coreedge.server.CoreEdgeClusterSettings;
import org.neo4j.coreedge.server.core.CoreGraphDatabase;
import org.neo4j.coreedge.server.core.EnterpriseCoreEditionModule;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.test.rule.TargetDirectory;

import static junit.framework.TestCase.assertEquals;
import static org.neo4j.coreedge.raft.log.segmented.SegmentedRaftLog.SEGMENTED_LOG_DIRECTORY_NAME;
import static org.neo4j.helpers.collection.MapUtil.stringMap;

public class CorePruningIT
{

    @Rule
    public final TargetDirectory.TestDirectory dir = TargetDirectory.testDirForTest( getClass() );

    private Cluster cluster;

    @After
    public void shutdown()
    {
        if ( cluster != null )
        {
            cluster.shutdown();
            cluster = null;
        }
    }

    @Test
    public void actuallyDeletesTheFiles() throws Exception
    {
        // given
        File dbDir = dir.directory();
        Map<String,String> params = stringMap( CoreEdgeClusterSettings.state_machine_flush_window_size.name(), "1",
                CoreEdgeClusterSettings.raft_log_pruning.name(), "1 entries",
                CoreEdgeClusterSettings.raft_log_rotation_size.name(), "1K",
                CoreEdgeClusterSettings.raft_log_pruning_frequency.name(), "1ms" );

        cluster = Cluster.start( dbDir, 3, 0, params, new TestOnlyDiscoveryServiceFactory() );

        CoreGraphDatabase coreGraphDatabase = null;
        for ( int i = 0; i < 100; i++ )
        {
            coreGraphDatabase = cluster.coreTx( ( db, tx ) -> {
                createStore( db, 1 );
                tx.success();
            } );
        }

        // when pruning kicks in
        Thread.sleep( 100 );

        // then some files are actually deleted
        int filesAfterPruning = numberOfFiles( new File( coreGraphDatabase.getStoreDir() ) );
        int expectedNumberOfLogFilesAfterPruning = 2; //Specific for this test.
        assertEquals( expectedNumberOfLogFilesAfterPruning, filesAfterPruning );
    }

    private int numberOfFiles( File storeDir )
    {
        File clusterDir = new File( storeDir, EnterpriseCoreEditionModule.CLUSTER_STATE_DIRECTORY_NAME );
        File raftLogDir = new File( clusterDir, SEGMENTED_LOG_DIRECTORY_NAME );
        return raftLogDir.list().length;
    }

    private void createStore( GraphDatabaseService db, int size )
    {
        for ( int i = 0; i < size; i++ )
        {
            Node node1 = db.createNode();
            Node node2 = db.createNode();

            node1.setProperty( "hej", "svej" );
            node2.setProperty( "tjabba", "tjena" );

            Relationship rel = node1.createRelationshipTo( node2, RelationshipType.withName( "halla" ) );
            rel.setProperty( "this", "that" );
        }
    }
}
