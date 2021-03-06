/*
 * MIT License
 *
 * Copyright (c) 2018 emufog contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package emufog.graph

/**
 * This class represents an autonomous system of the network graph. Hence it's a sub graph of the total graph providing
 * access to its nodes.
 */
class AS internal constructor(

    /**
     * unique identifier of the autonomous system
     */
    val id: Int
) {

    /**
     * mapping of edge nodes in the autonomous system
     */
    private val edges: MutableMap<Int, EdgeNode> = hashMapOf()

    /**
     * all edge nodes in this autonomous system
     */
    val edgeNodes: Collection<EdgeNode>
        get() = edges.values

    /**
     * mapping of backbone nodes in the autonomous system
     */
    private val backbones: MutableMap<Int, BackboneNode> = hashMapOf()

    /**
     * all backbone nodes in this autonomous system
     */
    val backboneNodes: Collection<BackboneNode>
        get() = backbones.values

    /**
     * mapping of edge device nodes in the autonomous system
     */
    private val edgeDevices: MutableMap<Int, EdgeDeviceNode> = hashMapOf()

    /**
     * all edge device nodes in this autonomous system
     */
    val edgeDeviceNodes: Collection<EdgeDeviceNode>
        get() = edgeDevices.values

    /**
     * Returns the edge node associated with the given ID from the AS.
     *
     * @param id the edge node's ID
     * @return node object or `null` if not found
     */
    fun getEdgeNode(id: Int): EdgeNode? = edges[id]

    /**
     * Returns the backbone node associated with the given ID from the AS.
     *
     * @param id the backbone node's ID
     * @return node object or `null` if not found
     */
    fun getBackboneNode(id: Int): BackboneNode? = backbones[id]

    /**
     * Returns the edge device node associated with the given ID from the AS.
     *
     * @param id the edge device node's ID
     * @return node object or `null` if not found
     */
    fun getEdgeDeviceNode(id: Int): EdgeDeviceNode? = edgeDevices[id]

    /**
     * Returns the node associated with the given ID from the AS.
     *
     * @param id the node's ID
     * @return node object or `null` if not found
     */
    fun getNode(id: Int): Node? = edges[id] ?: backbones[id] ?: edgeDevices[id]

    fun replaceByEdgeNode(node: Node): EdgeNode {
        removeNodeForReplacement(node)

        return createEdgeNode(node.id, node.edges, node.emulationNode)
    }

    fun replaceByBackboneNode(node: Node): BackboneNode {
        removeNodeForReplacement(node)

        return createBackboneNode(node.id, node.edges, node.emulationNode)
    }

    fun replaceByEdgeDeviceNode(node: Node, emulationNode: EdgeEmulationNode): EdgeDeviceNode {
        removeNodeForReplacement(node)

        return createEdgeDeviceNode(node.id, node.edges, emulationNode)
    }

    /**
     * Creates and returns a new edge node in the autonomous system.
     *
     * @param id id of the new node
     * @return newly created edge node
     */
    internal fun createEdgeNode(id: Int): EdgeNode = createEdgeNode(id, emptyList(), null)

    /**
     * Creates and returns a new backbone node in the autonomous system.
     *
     * @param id id of the new node
     * @return newly created backbone node
     */
    internal fun createBackboneNode(id: Int): BackboneNode = createBackboneNode(id, emptyList(), null)

    /**
     * Creates and returns a new edge device node in the autonomous system.
     *
     * @param id id of the new node
     * @return newly created edge device node
     */
    internal fun createEdgeDeviceNode(id: Int, emulationNode: EdgeEmulationNode): EdgeDeviceNode {
        return createEdgeDeviceNode(id, emptyList(), emulationNode)
    }

    /**
     * Returns if this instance of an autonomous system contains the given node.
     *
     * @param node node to check for
     * @return `true` if the as contains this node, `false` otherwise
     */
    internal fun containsNode(node: Node): Boolean {
        return backbones.containsKey(node.id) || edges.containsKey(node.id) || edgeDevices.containsKey(node.id)
    }

    private fun removeNodeForReplacement(node: Node) {
        require(node.system == this) { "The node: ${node.id} to replace is not assigned to the as: $id" }

        if (!removeNode(node)) {
            throw IllegalStateException("The node: ${node.id} to replace cannot be deleted from the autonomous system")
        }
    }

    private fun createEdgeDeviceNode(id: Int, edges: List<Edge>, emulationNode: EdgeEmulationNode): EdgeDeviceNode {
        val edgeDeviceNode = EdgeDeviceNode(id, this, edges, emulationNode)
        edgeDevices[edgeDeviceNode.id] = edgeDeviceNode

        return edgeDeviceNode
    }

    private fun createBackboneNode(id: Int, edges: List<Edge>, emulationNode: EmulationNode?): BackboneNode {
        val backboneNode = BackboneNode(id, this, edges, emulationNode)
        backbones[backboneNode.id] = backboneNode

        return backboneNode
    }

    private fun createEdgeNode(id: Int, edges: List<Edge>, emulationNode: EmulationNode?): EdgeNode {
        val edgeNode = EdgeNode(id, this, edges, emulationNode)
        this.edges[edgeNode.id] = edgeNode

        return edgeNode
    }

    /**
     * Removes a node from the AS.
     *
     * @param node node to remove
     * @return `true` if node could be deleted, `false` if not
     */
    private fun removeNode(node: Node): Boolean {
        return edges.remove(node.id) != null || backbones.remove(node.id) != null || edgeDevices.remove(node.id) != null
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AS) {
            return false
        }

        return id == other.id
    }

    override fun hashCode(): Int = id

    override fun toString(): String = "AS: $id"
}