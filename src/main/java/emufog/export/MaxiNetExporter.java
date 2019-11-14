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
package emufog.export;

import emufog.config.Config;
import emufog.container.Container;
import emufog.graph.Edge;
import emufog.graph.EmulationSettings;
import emufog.graph.Graph;
import emufog.graph.Node;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class exports a graph object to a valid python file usable with the
 * MaxiNet (https://maxinet.github.io/) network emulation framework.
 */
public class MaxiNetExporter implements GraphExporter {

    /**
     * list of all lines of the respective file in top down order
     */
    private final List<String> lines;

    /**
     * mapping of edges to their respective connector
     */
    private final Map<Edge, String> connectors;

    /**
     * Creates a new MaxiNet exporter instance.
     */
    public MaxiNetExporter() {
        lines = new ArrayList<>();
        connectors = new HashMap<>();
    }

    @Override
    public void exportGraph(Graph graph, Path path) throws IllegalArgumentException, IOException {
        if (graph == null) {
            throw new IllegalArgumentException("The given graph object does not exist.");
        }
        if (path == null) {
            throw new IllegalArgumentException("The given path is null. Please provide a valid path");
        }

        // check if file exists and can be overwritten
        Config config = graph.getConfig();
        File file = path.toFile();
        if (!config.overWriteOutputFile && file.exists()) {
            throw new IllegalArgumentException("The given file already exist. Please provide a valid path");
        }

        // check the file ending of the given path
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**.py");
        if (!matcher.matches(path)) {
            throw new IllegalArgumentException("The file name for MaxiNet has to be a python file (.py)");
        }

        // initialize empty sets to start the writing
        lines.clear();
        connectors.clear();

        // begin to write the python file
        lines.add("#!/usr/bin/env python2");
        lines.add("");
        lines.add("import time");
        lines.add("");
        lines.add("from MaxiNet.Frontend import maxinet");
        lines.add("from MaxiNet.Frontend.container import Docker");
        lines.add("from mininet.topo import Topo");
        lines.add("from mininet.node import OVSSwitch");
        lines.add("");
        lines.add("topo = Topo()");
        lines.add("");

        addHosts(graph);

        addSwitches(graph);

        addConnectors(graph);

        addLinks(graph);

        lines.add("");
        lines.add("# create experiment");
        lines.add("cluster = maxinet.Cluster()");
        lines.add("exp = maxinet.Experiment(cluster, topo, switch=OVSSwitch)");
        lines.add("exp.setup()");

        // set the overwrite option if feature is set in the config file
        StandardOpenOption overwrite = config.overWriteOutputFile ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.APPEND;
        // write output in UTF-8 to the specified file
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, overwrite);
        lines.clear();
        connectors.clear();
    }

    /**
     * Writes all container host nodes of the graph to the output file.
     *
     * @param graph graph to export
     */
    private void addHosts(Graph graph) {
        lines.add("# add hosts");

        graph.getNodes().stream().filter(Node::hasEmulationSettings).forEach(n -> {
            EmulationSettings emu = n.getEmulationNode();
            Container container = emu.getContainer();
            lines.add(String.format("%s = topo.addHost(\"%s\", cls=Docker, ip=\"%s\", dimage=\"%s\", mem_limit=%d)",
                n.getName(),
                n.getName(),
                emu.getIP(),
                container.getName(),
                container.getMemoryLimit()));
        });
    }

    /**
     * Writes all switches that do not require container to the output file.
     *
     * @param graph graph to export
     */
    private void addSwitches(Graph graph) {
        lines.add("");
        lines.add("# add switches");

        List<Node> nodes = new ArrayList<>();
        nodes.addAll(graph.getEdgeNodes());
        nodes.addAll(graph.getBackboneNodes());
        nodes.stream().filter(n -> !n.hasEmulationSettings()).forEach(n -> lines.add(String.format("%s = topo.addSwitch(\"%s\")", n.getName(), n.getName())));
    }

    /**
     * Creates connectors between two hosts to run in MaxiNet.
     *
     * @param graph graph to export
     */
    private void addConnectors(Graph graph) {
        lines.add("");
        lines.add("# add connectors");

        int counter = 0;
        for (Edge e : graph.getEdges()) {
            if (e.getSource().hasEmulationSettings() && e.getDestination().hasEmulationSettings()) {
                String name = "c" + counter;
                lines.add(String.format("%s = topo.addSwitch(\"%s\")", name, name));
                connectors.put(e, name);
                counter++;
            }
        }
    }

    /**
     * Established the connections between two nodes based on the edges of the graph.
     *
     * @param graph graph to export
     */
    private void addLinks(Graph graph) {
        lines.add("");
        lines.add("# add links");

        for (Edge e : graph.getEdges()) {
            if (connectors.containsKey(e)) {
                String connector = connectors.get(e);
                addLink(e.getSource().getName(), connector, e.getDelay() / 2, e.getBandwidth());
                addLink(connector, e.getDestination().getName(), e.getDelay() / 2, e.getBandwidth());
            } else {
                addLink(e.getSource().getName(), e.getDestination().getName(), e.getDelay(), e.getBandwidth());
            }
        }
    }

    /**
     * Adds a new link between two nodes to the document.
     *
     * @param source      source of the link
     * @param destination destination of the link
     * @param latency     latency applied to this link
     * @param bandwidth   bandwidth limitations of this link
     */
    private void addLink(String source, String destination, float latency, float bandwidth) {
        lines.add(String.format("topo.addLink(%s, %s, delay='%fms', bw=%f)", source, destination, latency, bandwidth));
    }
}
