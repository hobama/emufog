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
package emufog.reader.caida

import emufog.config.Config
import emufog.graph.EdgeNode
import emufog.graph.Graph
import emufog.graph.Node
import emufog.reader.GraphReader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * This reader can read in the CAIDA topology an build a graph based on that data.
 */
object CaidaFormatReader : GraphReader {

    /**
     * Reads in a graph from a Caida dataset. Such a dataset consists of a .nodes.geo, a .nodes.as
     * and a .links file. The list of input files need to contain those three files.
     *
     * @param files the list of files to read in
     * @return read in graph object
     */
    @Throws(IOException::class)
    override fun readGraph(files: List<Path>): Graph {
        return CaidaFormatReaderImpl(files).readGraph()
    }
}

private class CaidaFormatReaderImpl(files: List<Path>) {

    /* number of times AS field exceeds the Integer range */
    private var asNoInteger: Int = 0

    /* number of times no node has been found with an ID to assign an AS */
    private var noNodeFoundForAS: Int = 0

    /* number of times no node has been found with an ID to establish a connection */
    private var noNodeFoundForEdge: Int = 0

    /* number of times ID field exceeds the Integer range */
    private var idsNoInteger: Int = 0

    /* number of times a node line was skipped */
    private var nodeLineSkipped: Int = 0

    /* number of times a AS line was skipped */
    private var asLineSkipped: Int = 0

    /* number of times a link line was skipped */
    private var linkLineSkipped: Int = 0

    /* number of times coordinate field exceeds the Float range */
    private var coordinatesNoFloats: Int = 0

    /* mapping from ID's to coordinates of the nodes */
    private val nodeCoordinates: MutableMap<Int, Coordinates> = HashMap()

    private val nodesFile: Path

    private val asFile: Path

    private val linkFile: Path

    private val graph = Graph(Config.config!!)

    init {
        nodesFile = getFileWithEnding(files, ".nodes.geo")
            ?: throw IllegalArgumentException("The given files do not contain a .nodes.geo file.")
        asFile = getFileWithEnding(files, ".nodes.as")
            ?: throw IllegalArgumentException("The given files do not contain a .nodes.as file.")
        linkFile = getFileWithEnding(files, ".links")
            ?: throw IllegalArgumentException("The given files do not contain a .links file.")
    }

    internal fun readGraph(): Graph {
        // read in the nodes
        nodesFile.toFile().forEachLine(CHARSET) { processNodeLine(it) }

        // read in the AS
        asFile.toFile().forEachLine(CHARSET) { processASLine(it) }

        // read in the edges
        linkFile.toFile().forEachLine(CHARSET) { processLinkLine(it) }

        // log errors
        logResults()

        return graph
    }

    /**
     * Logs the errors and not matching IDs while reading.
     */
    private fun logResults() {
        // additional logging for debugging
        LOG.debug("ID out of Integer range: {}", idsNoInteger)
        LOG.debug("AS out of Integer range: {}", asNoInteger)
        LOG.debug("Coordinates out of Float range: {}", coordinatesNoFloats)
        LOG.debug("Number of times no nodes were found to assign an AS: {}", noNodeFoundForAS)
        LOG.debug("Number of times no nodes were found to build an edge: {}", noNodeFoundForEdge)
        LOG.debug("Nodes read without an AS: {}", nodeCoordinates.size)
        LOG.debug("Number of node lines skipped: {}", nodeLineSkipped)
        LOG.debug("Number of AS lines skipped: {}", asLineSkipped)
        LOG.debug("Number of link lines skipped: {}", linkLineSkipped)
    }

    /**
     * Reads in an edge of the graph.
     *
     * @param line current line to process
     */
    private fun processLinkLine(line: String) {
        val values = line.split(" ".toRegex())
        if (values.size < EDGE_COLUMNS) {
            LOG.debug("There are not {} columns in the link line: {}", EDGE_COLUMNS, line)
            linkLineSkipped++
            return
        }

        val linkStr = values[1].substring(1, values[1].length - 1)
        val id = linkStr.toIntOrNull()
        if (id == null) {
            LOG.debug("Failed to parse the link id {} to an integer.", linkStr)
            idsNoInteger++
            return
        }

        for (i in 3 until values.size - 1) {
            var sourceStr = values[i]
            var end = sourceStr.indexOf(':')
            if (end == -1) {
                end = sourceStr.length
            }
            sourceStr = sourceStr.substring(1, end)
            val sourceID = sourceStr.toIntOrNull()
            if (sourceID == null) {
                LOG.debug("Failed to parse the link's source id {} to an integer.", sourceStr)
                idsNoInteger++
                return
            }

            var destinationStr = values[i + 1]
            end = destinationStr.indexOf(':')
            if (end == -1) {
                end = destinationStr.length
            }
            destinationStr = destinationStr.substring(1, end)
            val destinationID = destinationStr.toIntOrNull()
            if (destinationID == null) {
                LOG.debug(
                    "Failed to parse the link's destination id {} to an integer.",
                    destinationStr
                )
                idsNoInteger++
                return
            }

            val from: EdgeNode? = graph.getEdgeNode(sourceID)
            val to: EdgeNode? = graph.getEdgeNode(destinationID)

            if (from == null || to == null) {
                LOG.debug("To create a link source and destination must be found.")
                noNodeFoundForEdge++
                return
            }

            graph.createEdge(id, from, to, getLatency(from, to), 1000f)
        }
    }

    /**
     * Adapts the AS field of the node identified in the current line.
     *
     * @param line current line to process
     */
    private fun processASLine(line: String) {
        val values = line.split(" ".toRegex())
        if (values.size < AS_COLUMNS) {
            asLineSkipped++
            LOG.debug(
                "There are not {} columns in the autonomous system line: {}",
                AS_COLUMNS,
                line
            )
            return
        }

        val nodeStr = values[1].substring(1)
        val id = nodeStr.toIntOrNull()
        if (id == null) {
            LOG.debug("Failed to parse the id {} to an integer.", nodeStr)
            idsNoInteger++
            return
        }

        val system = values[2].toIntOrNull()
        if (system == null) {
            LOG.debug("Failed to parse the autonomous system id {} to an integer.", values[2])
            asNoInteger++
            return
        }

        graph.createEdgeNode(id, graph.getOrCreateAutonomousSystem(system))
    }

    /**
     * Reads in and process a line of the input file to add a node to the graph given.
     *
     * @param line current line to process
     */
    private fun processNodeLine(line: String) {
        val values = line.split("\t".toRegex())
        if (values.size < NODE_COLUMNS) {
            LOG.debug("There are not {} columns in the node line: {}", NODE_COLUMNS, line)
            nodeLineSkipped++
            return
        }

        val nodeStr = values[0].substring(10, values[0].length - 1)
        val id = nodeStr.toIntOrNull()
        if (id == null) {
            LOG.debug("Failed to parse the id {} to an integer.", nodeStr)
            idsNoInteger++
            return
        }

        try {
            nodeCoordinates[id] = Coordinates(values[5].toFloat(), values[6].toFloat())
        } catch (e: NumberFormatException) {
            LOG.debug("Failed to parse coordinates {} and {} to floats.", values[5], values[6])
            coordinatesNoFloats++
        }
    }

    private fun getLatency(from: Node, to: Node): Float {
        return 1F
    }

    /**
     * Returns the path of the file matching the giving file extension at the end.
     *
     * @param files list of possible files
     * @param fileExtension file extension to match
     * @return the file of the list matching the extension or `null` if not found
     */
    private fun getFileWithEnding(files: List<Path>, fileExtension: String): Path? {
        return files.firstOrNull { it.toString().endsWith(fileExtension) }
    }

    /**
     * Coordinates of a node in the graph. This class gets mapped to the respective ID of the node.
     * @property xPos x coordinate
     * @property yPos y coordinate
     */
    internal data class Coordinates(val xPos: Float, val yPos: Float)

    companion object {

        private val LOG = LoggerFactory.getLogger(CaidaFormatReader::class.java)

        /**
         * initialize charset according to https://en.wikipedia.org/wiki/ISO/IEC_8859-1
         */
        private val CHARSET = StandardCharsets.ISO_8859_1

        private const val NODE_COLUMNS = 7

        private const val AS_COLUMNS = 3

        private const val EDGE_COLUMNS = 4
    }
}
