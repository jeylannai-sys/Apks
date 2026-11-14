package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagramDao {

    @Query("SELECT * FROM diagrams ORDER BY updatedAt DESC")
    fun getAllDiagrams(): Flow<List<DiagramEntity>>

    @Query("SELECT * FROM diagrams WHERE id = :id LIMIT 1")
    suspend fun getDiagramById(id: String): DiagramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagram(diagram: DiagramEntity)

    @Update
    suspend fun updateDiagram(diagram: DiagramEntity)

    @Delete
    suspend fun deleteDiagram(diagram: DiagramEntity)

    @Query("SELECT * FROM diagram_nodes WHERE diagramId = :diagramId ORDER BY orderIndex ASC")
    fun getNodesForDiagram(diagramId: String): Flow<List<DiagramNodeEntity>>

    @Query("SELECT * FROM diagram_nodes WHERE diagramId = :diagramId ORDER BY orderIndex ASC")
    suspend fun getNodesForDiagramOnce(diagramId: String): List<DiagramNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: DiagramNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<DiagramNodeEntity>)

    @Update
    suspend fun updateNode(node: DiagramNodeEntity)

    @Delete
    suspend fun deleteNode(node: DiagramNodeEntity)

    @Query("DELETE FROM diagram_nodes WHERE id = :nodeId")
    suspend fun deleteNodeById(nodeId: String)

    @Query("SELECT * FROM diagram_connections WHERE diagramId = :diagramId")
    fun getConnectionsForDiagram(diagramId: String): Flow<List<DiagramConnectionEntity>>

    @Query("SELECT * FROM diagram_connections WHERE diagramId = :diagramId")
    suspend fun getConnectionsForDiagramOnce(diagramId: String): List<DiagramConnectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: DiagramConnectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnections(connections: List<DiagramConnectionEntity>)

    @Delete
    suspend fun deleteConnection(connection: DiagramConnectionEntity)

    @Query("DELETE FROM diagram_connections WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun deleteConnectionsForNode(nodeId: String)

    @Query("DELETE FROM diagram_nodes WHERE diagramId = :diagramId")
    suspend fun deleteAllNodesInDiagram(diagramId: String)

    @Query("DELETE FROM diagram_connections WHERE diagramId = :diagramId")
    suspend fun deleteAllConnectionsInDiagram(diagramId: String)
}
