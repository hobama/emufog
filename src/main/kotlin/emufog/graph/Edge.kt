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
 * This class represents an edge in the network graph. The connection between the two nodes is bidirectional.
 * Latency and bandwidth apply to both directions.
 */
class Edge internal constructor(

    /**
     * unique identifier of the edge object
     */
    val id: Int,

    /**
     * one end of the connection
     */
    from: Node,

    /**
     * the other end of the connection
     */
    to: Node,

    /**
     * latency delay on this edge in ms
     */
    val delay: Float,

    /**
     * bandwidth of the connection on Mbit/s
     */
    val bandwidth: Float
) {

    private val sourceSystem: AS = from.system

    private val destinationSystem: AS = to.system

    private val sourceId: Int = from.id

    private val destinationId: Int = to.id

    /**
     * the source of the edge, one end of the link
     */
    val source: Node
        get() = getNode(sourceId, sourceSystem)

    /**
     * the destination of the edge, other end of the link
     */
    val destination: Node
        get() = getNode(destinationId, destinationSystem)

    /**
     * Returns the other end of the connection for the given node. In case the node is not part of the connection the
     * method returns `null`.
     *
     * @param node node to find the partner for
     * @return the other end of the connection or `null` if node is not part of this edge
     */
    fun getDestinationForSource(node: Node): Node? {
        return when (node.id) {
            sourceId -> destination
            destinationId -> source
            else -> null
        }
    }

    /**
     * Indicates whether this edge connects two different ASs or not.
     *
     * @return `true` if edge is connecting different ASs, `false` otherwise
     */
    fun isCrossASEdge(): Boolean = sourceSystem != destinationSystem

    private fun getNode(id: Int, system: AS): Node {
        val node = system.getNode(id)
        requireNotNull(node) { "The node: $id does not exist in $system" }

        return node
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Edge) {
            return false
        }

        return id == other.id
    }

    override fun hashCode(): Int = id

    override fun toString(): String = "Edge: $id"
}
