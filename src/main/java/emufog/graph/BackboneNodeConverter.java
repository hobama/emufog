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
package emufog.graph;

/**
 * This class convert an existing node to a backbone node in the graph.
 */
class BackboneNodeConverter extends NodeConverter {

    private static final BackboneNodeConverter INSTANCE = new BackboneNodeConverter();

    @Override
    Node createNewNode(Node oldNode) {
        return new BackboneNode(oldNode.id, oldNode.as);
    }

    @Override
    void addNodeToGraph(Node newNode) {
        newNode.as.addBackboneNode((BackboneNode) newNode);
    }

    @Override
    boolean needsConversion(Node oldNode) {
        return !(oldNode instanceof BackboneNode);
    }

    @Override
    BackboneNode convert(Node oldNode) {
        return (BackboneNode) super.convert(oldNode);
    }

    /**
     * Converts an arbitrary node to a backbone node. If the node is already a backbone node
     * it will just be returned. Otherwise the old node will be removed from the AS and
     * replaced by the new node.
     *
     * @param oldNode old node to replace by a backbone node
     * @return newly created backbone node instance
     */
    static BackboneNode convertToBackbone(Node oldNode) {
        return INSTANCE.convert(oldNode);
    }
}
