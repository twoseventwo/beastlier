/*
* File PartitionedTree.java
*
* Copyright (C) 2016 Matthew Hall mdhall@ic.ac.uk
*
* This file is part of BEASTLIER, a BEAST 2 package.
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this program; if not, write to the
* Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
* Boston, MA  02110-1301  USA
*/

package beast.evolution.tree;

import beast.core.Description;
import beast.core.Input;
import beast.core.StateNode;
import beast.core.StateNodeInitialiser;
import beast.util.TreeParser;
import com.google.common.collect.Lists;

import java.io.PrintStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static beast.evolution.tree.PartitionedTree.Rules.SECOND_TYPE;
import static beast.evolution.tree.PartitionedTree.Rules.THIRD_TYPE;

/**
 * @author Matthew Hall <mdhall@ic.ac.uk> based on MultiTypeTree.java from the MultiTypeTree package by Tim Vaughan
 */

@Description("A beast.evolution.tree with node partitions as defined by Hall et al")
public class PartitionedTree extends Tree {

    /*
     * Inputs:
     */
    public Input<String> elementLabelInput = new Input<>(
            "elementLabel",
            "Label for partition elements (default 'host')", "host");

//  todo Comment this out until you figure out how it works
//    public Input<TraitSet> elementTraitInput = new Input<>(
//            "elementTrait", "Element trait set.  Used only by BEAUti.");

//  todo Comment _this_ out until a point where the a version with elements not containing tips is happening
//    public Input<String> elementTraitValuesInput = new Input<>(
//            "typeTraitValues",
//            "Comma-delimited list of types to be included even when absent " +
//                    "from the sampled taxa.");

    public Input<Double> rootBranchLengthInput = new Input<>(
            "rootBranchLength", "The length of the root branch (the index infection must occur along this branch",
            50.0);

    String[] ruleTypes = {"second", "third"};

    public Input<String> rulesInput = new Input<>("rules", "Whether partitioning obeys the rules for the second" +
            " class of transmissiont tree reconstruction (internal nodes are transmissions), or the third " +
            "(within-host diversity)", "third", ruleTypes);

    /*
     * Non-input fields:
     */
    protected String elementLabel;
    protected TraitSet elementTraitSet;
    protected List<Object> elementList;

    public enum Rules {SECOND_TYPE, THIRD_TYPE};

    public Rules rules;


    protected double rootBranchLength;

    public PartitionedTree() { };

    public PartitionedTree(Node rootNode) {
        if (!(rootNode instanceof PartitionedTreeNode))
            throw new IllegalArgumentException("Attempted to instantiate "
                    + "multi-type tree with regular root node.");


        setRoot(rootNode);
        initArrays();
    }

    @Override
    public void initAndValidate() {

        rules = rulesInput.get().equals("second") ? SECOND_TYPE : THIRD_TYPE;

        if (m_initial.get() != null && !(this instanceof StateNodeInitialiser)) {

            if (!(m_initial.get() instanceof PartitionedTree)) {
                throw new IllegalArgumentException("Attempted to initialise "
                        + "multi-type tree with regular tree object.");
            }

            PartitionedTree other = (PartitionedTree) m_initial.get();
            root = other.root.copy();
            nodeCount = other.nodeCount;
            internalNodeCount = other.internalNodeCount;
            leafNodeCount = other.leafNodeCount;
        }

        if (nodeCount < 0) {
            // todo do a random starting tree routine that works for partitions

            throw new RuntimeException("No starting tree specified");

//            if (m_taxonset.get() != null) {
//                // make a caterpillar
//                List<String> sTaxa = m_taxonset.get().asStringList();
//                Node left = new PartitionedTreeNode();
//                left.labelNr = 0;
//                left.height = 0;
//                left.setID(sTaxa.get(0));
//                for (int i = 1; i < sTaxa.size(); i++) {
//                    Node right = new PartitionedTreeNode();
//                    right.labelNr = i;
//                    right.height = 0;
//                    right.setID(sTaxa.get(i));
//                    Node parent = new PartitionedTreeNode();
//                    parent.labelNr = sTaxa.size() + i - 1;
//                    parent.height = i;
//                    left.parent = parent;
//                    parent.setLeft(left);
//                    right.parent = parent;
//                    parent.setRight(right);
//                    left = parent;
//                }
//                root = left;
//                leafNodeCount = sTaxa.size();
//                nodeCount = leafNodeCount * 2 - 1;
//                internalNodeCount = leafNodeCount - 1;
//
//            } else {
//                // make dummy tree with a single root node
//                root = new PartitionedTreeNode();
//                root.labelNr = 0;
//                root.labelNr = 0;
//                root.m_tree = this;
//                nodeCount = 1;
//                internalNodeCount = 0;
//                leafNodeCount = 1;
//            }
        }

        if (nodeCount >= 0) {
            initArrays();
        }

        elementLabel = elementLabelInput.get();
        rootBranchLength = rootBranchLengthInput.get();

        processTraits(m_traitList.get());

        if(rules == SECOND_TYPE){
            for(int i=0; i<elementList.size(); i++){
               if(getTipsInElement(i).size() > 1){
                   throw new IllegalArgumentException("As currently implemented rules of the second type require one" +
                           " and only one tip per partition element");
               }

           }
        }

        if(!isValid()){
            throw new RuntimeException("Starting tree is not properly partitioned");
        }

        // Ensure tree is compatible with traits.
        if (hasDateTrait())
            adjustTreeNodeHeights(root);
    }


    @Override
    protected void processTraits(List<TraitSet> traitList) {
        super.processTraits(traitList);

        // Record trait set associated with leaf types.
        for (TraitSet traitSet : traitList) {
            if (traitSet.getTraitName().equals(elementLabel)) {
                elementTraitSet = traitSet;
                break;
            }
        }

        //  todo when implementing BEAUTi
        //
        // Use explicitly-identified type trait set if available.
        // Seems dumb, but needed for BEAUti as ListInputEditors
        // muck things up...
//        if (elementTraitInput.get() != null)
//            elementTraitSet = elementTraitInput.get();

        // Construct type list.
        if(elementTraitSet == null) {
            // You can't get away with this for partitioned trees. todo There may be a better way than this, though.
            throw new RuntimeException("No TraitSet for partition elements specified");

//            if (getTaxonset() != null) {
//                TraitSet dummyTraitSet = new TraitSet();
//
//                StringBuilder sb = new StringBuilder();
//                for (int i=0; i<getTaxonset().getTaxonCount(); i++) {
//                    if (i>0)
//                        sb.append(",\n");
//                    sb.append(getTaxonset().getTaxonId(i)).append("=NOT_SET");
//                }
//                try {
//                    dummyTraitSet.initByName(
//                            "traitname", "type",
//                            "taxa", getTaxonset(),
//                            "value", sb.toString());
//                    dummyTraitSet.setID("typeTraitSet.t:"
//                            + BeautiDoc.parsePartition(getID()));
//                    setElementTrait(dummyTraitSet);
//                } catch (Exception ex) {
//                    System.out.println("Error setting default type trait.");
//                }
//            }
        }

        if (elementTraitSet != null) {

            Set<String> typeSet = new HashSet<>();

            int nTaxa = elementTraitSet.taxaInput.get().asStringList().size();
            for (int i = 0; i < nTaxa; i++) {
                typeSet.add(elementTraitSet.getStringValue(i));
            }

            // Include any additional trait values in type list
            // Don't do this for now
//            if (typeTraitValuesInput.get() != null) {
//                for (String typeName : typeTraitValuesInput.get().split(","))
//                    typeSet.add(typeName);
//            }

            elementList = Lists.newArrayList(typeSet);

            System.out.println("Partition element trait with the following elements detected:");
            for (int i = 0; i < elementList.size(); i++)
                System.out.println(elementList.get(i) + " (" + i + ")");

        }
    }

    /**
     * @return TraitSet with same name as elementLabel.
     */
    public TraitSet getElementTrait() {
        if (!traitsProcessed)
            processTraits(m_traitList.get());

        return elementTraitSet;
    }

    /**
     * @return true if TraitSet with same name as elementLabel exists.
     */
    public boolean hasElementTrait() {
        return getElementTrait() != null;
    }

    /**
     * Specifically set the element trait set for this beast.evolution.tree.
     *
     * @param traitSet
     */
    public void setElementTrait(TraitSet traitSet) {
        if (hasElementTrait()) {
            m_traitList.get().remove(elementTraitSet);
        }

        // More BEAUTi business
//        if (traitSet != null) {
//            elementTraitInput.setValue(traitSet, this);
//        }

        elementTraitSet = traitSet;
    }

    /**
     * Retrieve the list of elements.
     * @return List of elements.
     */
    public List<Object> getElementList() {
        if (!traitsProcessed)
            processTraits(m_traitList.get());

        return elementList;
    }


    /**
     * @param element
     * @return string name of given type
     */
    public Object getElementString(int element) {
        if (!traitsProcessed)
            processTraits(m_traitList.get());

        return elementList.get(element);
    }

    /**
     * @param elementString
     * @return integer type corresponding to given type string
     */
    public int getTypeFromString(String elementString) {
        if (!traitsProcessed)
            processTraits(m_traitList.get());

        return elementList.indexOf(elementString);
    }

    /**
     * @return type label to be used in logging.
     */
    public String getElementLabel() {
        return elementLabel;
    }


    @Override
    protected final void initArrays() {
        // initialise tree-as-array representation + its stored variant
        m_nodes = new PartitionedTreeNode[nodeCount];
        listNodes((PartitionedTreeNode)root, (PartitionedTreeNode[])m_nodes);
        m_storedNodes = new PartitionedTreeNode[nodeCount];
        Node copy = root.copy();
        listNodes((PartitionedTreeNode)copy, (PartitionedTreeNode[])m_storedNodes);
    }

    /**
     * Convert tree to array representation.
     *
     * @param node Root of sub-tree to convert.
     * @param nodes Array to populate with tree nodes.
     */
    private void listNodes(PartitionedTreeNode node, PartitionedTreeNode[] nodes) {
        nodes[node.getNr()] = node;
        node.m_tree = this;
        if (!node.isLeaf()) {
            listNodes(node.getLeft(), nodes);
            if (node.getRight()!=null)
                listNodes(node.getRight(), nodes);
        }
    }

    /**
     * Deep copy, returns a completely new partitioned tree.
     *
     * @return a deep copy of this partitioned tree
     */
    @Override
    public PartitionedTree copy() {
        PartitionedTree tree = new PartitionedTree();
        tree.ID = ID;
        tree.index = index;
        tree.root = root.copy();
        tree.nodeCount = nodeCount;
        tree.internalNodeCount = internalNodeCount;
        tree.leafNodeCount = leafNodeCount;
        tree.elementLabel = elementLabel;
        return tree;
    }

    /**
     * Copy all values from an existing partitioned tree.
     *
     * @param other
     */
    @Override
    public void assignFrom(StateNode other) {
        PartitionedTree pTree = (PartitionedTree) other;

        PartitionedTreeNode[] pNodes = new PartitionedTreeNode[pTree.getNodeCount()];
        for (int i=0; i<pTree.getNodeCount(); i++)
            pNodes[i] = new PartitionedTreeNode();

        ID = pTree.ID;
        root = pNodes[pTree.root.getNr()];
        root.assignFrom(pNodes, pTree.root);
        root.parent = null;

        nodeCount = pTree.nodeCount;
        internalNodeCount = pTree.internalNodeCount;
        leafNodeCount = pTree.leafNodeCount;
        initArrays();
    }

    /**
     * Copy all values aside from IDs from an existing partitioned tree.
     *
     * @param other
     */
    @Override
    public void assignFromFragile(StateNode other) {
        PartitionedTree pTree = (PartitionedTree) other;
        if (m_nodes == null) {
            initArrays();
        }
        root = m_nodes[pTree.root.getNr()];
        Node[] otherNodes = pTree.m_nodes;
        int iRoot = root.getNr();
        assignFromFragileHelper(0, iRoot, otherNodes);
        root.height = otherNodes[iRoot].height;
        root.parent = null;

        PartitionedTreeNode ptRoot = (PartitionedTreeNode)root;
        ptRoot.partitionElementNumber = ((PartitionedTreeNode)(otherNodes[iRoot])).partitionElementNumber;

        if (otherNodes[iRoot].getLeft() != null) {
            root.setLeft(m_nodes[otherNodes[iRoot].getLeft().getNr()]);
        } else {
            root.setLeft(null);
        }
        if (otherNodes[iRoot].getRight() != null) {
            root.setRight(m_nodes[otherNodes[iRoot].getRight().getNr()]);
        } else {
            root.setRight(null);
        }
        assignFromFragileHelper(iRoot + 1, nodeCount, otherNodes);
    }

    /**
     * helper to assignFromFragile *
     */
    private void assignFromFragileHelper(int iStart, int iEnd, Node[] otherNodes) {
        for (int i = iStart; i < iEnd; i++) {
            PartitionedTreeNode sink = (PartitionedTreeNode) m_nodes[i];
            PartitionedTreeNode src = (PartitionedTreeNode) otherNodes[i];
            sink.height = src.height;
            sink.parent = m_nodes[src.parent.getNr()];

            sink.partitionElementNumber = src.partitionElementNumber;

            if (src.getLeft() != null) {
                sink.setLeft(m_nodes[src.getLeft().getNr()]);
                if (src.getRight() != null) {
                    sink.setRight(m_nodes[src.getRight().getNr()]);
                } else {
                    sink.setRight(null);
                }
            }
        }
    }

    /**
     * Check whether partition and timings of tree are sensible.
     *
     * @return true if times are "valid" and the element assignment is a valid partition structure
     */
    public boolean isValid() {

        if(rules==THIRD_TYPE) {

            for (int i = 0; i < nodeCount; i++) {
                PartitionedTreeNode node = (PartitionedTreeNode) getNode(i);
                int elementNumber = node.getPartitionElementNumber();

                boolean linked = false;
                for (Node child : node.getChildren()) {
                    if (((PartitionedTreeNode) child).getPartitionElementNumber() == elementNumber) {
                        linked = true;
                    }
                }

                Node parent = node.getParent();
                if (((PartitionedTreeNode) parent).getPartitionElementNumber() == elementNumber) {
                    linked = true;
                }

                if (!linked) {
                    return false;
                }

            }
            return true;
        } else {
            for (int i = 0; i < internalNodeCount; i++) {
                PartitionedTreeNode node = (PartitionedTreeNode) getNode(i);
                int elementNumber = node.getPartitionElementNumber();

                int childrenInSameElement = 0;

                for(Node child : node.getChildren()){

                    if(((PartitionedTreeNode)child).getPartitionElementNumber() ==
                            node.getPartitionElementNumber()){
                        childrenInSameElement++;
                    }
                }

                if(childrenInSameElement!=1){
                    return false;
                }
            }
            return true;
        }
    }


    /**
     * Obtain the number of elements in the partition
     *
     * @return number of elements
     */
    public int getNElements() {
        if (!traitsProcessed)
            processTraits(m_traitList.get());

        return elementList.size();
    }


    /////////////////////////////////////////////////
    //           StateNode implementation          //
    /////////////////////////////////////////////////
    @Override
    protected void store() {
        storedRoot = m_storedNodes[root.getNr()];
        int iRoot = root.getNr();

        storeNodes(0, iRoot);

        storedRoot.setHeight(m_nodes[iRoot].getHeight());
        storedRoot.setParent(null);

        if (root.getLeft()!=null)
            storedRoot.setLeft(m_storedNodes[root.getLeft().getNr()]);
        else
            storedRoot.setLeft(null);
        if (root.getRight()!=null)
            storedRoot.setRight(m_storedNodes[root.getRight().getNr()]);
        else
            storedRoot.setRight(null);

        PartitionedTreeNode mtStoredRoot = (PartitionedTreeNode)storedRoot;

        mtStoredRoot.partitionElementNumber = ((PartitionedTreeNode)m_nodes[iRoot]).partitionElementNumber;

        storeNodes(iRoot+1, nodeCount);
    }

    /**
     * helper to store *
     */
    private void storeNodes(int iStart, int iEnd) {
        for (int i = iStart; i<iEnd; i++) {
            PartitionedTreeNode sink = (PartitionedTreeNode)m_storedNodes[i];
            PartitionedTreeNode src = (PartitionedTreeNode)m_nodes[i];
            sink.setHeight(src.getHeight());
            sink.setParent(m_storedNodes[src.getParent().getNr()]);
            if (src.getLeft()!=null) {
                sink.setLeft(m_storedNodes[src.getLeft().getNr()]);
                if (src.getRight()!=null)
                    sink.setRight(m_storedNodes[src.getRight().getNr()]);
                else
                    sink.setRight(null);
            }

            sink.partitionElementNumber = src.partitionElementNumber;
        }
    }

    /////////////////////////////////////////////////
    // Methods implementing the Loggable interface //
    /////////////////////////////////////////////////
    @Override
    public void init(PrintStream printStream) {

        printStream.println("#NEXUS\n");
        printStream.println("Begin taxa;");
        printStream.println("\tDimensions ntax="+getLeafNodeCount()+";");
        printStream.println("\t\tTaxlabels");
        for (int i = 0; i<getLeafNodeCount(); i++)
            printStream.println("\t\t\t"+getNodesAsArray()[i].getID());
        printStream.println("\t\t\t;");
        printStream.println("End;");

        printStream.println("Begin trees;");
        printStream.println("\tTranslate");
        for (int i = 0; i<getLeafNodeCount(); i++) {
            printStream.print("\t\t\t"+(getNodesAsArray()[i].getNr()+1)
                    +" "+getNodesAsArray()[i].getID());
            if (i<getLeafNodeCount()-1)
                printStream.print(",");
            printStream.print("\n");
        }
        printStream.print("\t\t\t;");
    }

    @Override
    public void log(int i, PrintStream printStream) {
        printStream.print("beast.evolution.tree STATE_"+i+" = ");
        printStream.print(toString());
        printStream.print(";");


    }

    @Override
    public void close(PrintStream printStream) {
        printStream.println("End;");
    }


    /////////////////////////////////////////////////
    // Methods for flat trees rewritten            //
    /////////////////////////////////////////////////

    /**
     * Generates a new beast.evolution.tree in which the colours are indicated as meta-data.
     *
     * Caveat: assumes more than one node exists on beast.evolution.tree (i.e. leaf != root)
     *
     * @return Normal beast.evolution.tree.
     */
    public Tree getNormalTree() {

        Tree flatTree = copy();
        flatTree.initArrays();

        for (Node node : getNodesAsArray()) {

            PartitionedTreeNode pNode = (PartitionedTreeNode)node;

            int nodeNum = node.getNr();

            if (node.isRoot()) {
                Node startNode = flatTree.getNode(nodeNum);
                startNode.setMetaData(elementLabel, ((PartitionedTreeNode)node).getPartitionElementNumber());
                startNode.metaDataString = String.format("%s=%d", elementLabel, pNode.getPartitionElementNumber());
                continue;
            }

            Node startNode = flatTree.getNode(nodeNum);
            startNode.setMetaData(elementLabel, ((PartitionedTreeNode)node).getPartitionElementNumber());
            startNode.metaDataString = String.format("%s=%d", elementLabel, pNode.getPartitionElementNumber());

        }

        return flatTree;
    }


    /**
     * Initialise topology and partitions from Tree object in which colours
     * are marked by meta-data tags.
     *
     * @param tree
     * @param takeNrsFromTree
     * @throws java.lang.Exception
     */
    public void initFromNormalTree(Tree tree, boolean takeNrsFromTree) throws Exception {

        // Build new coloured beast.evolution.tree:

        List<Node> activeTreeNodes = new ArrayList<Node>();
        List<Node> nextActiveTreeNodes = new ArrayList<Node>();
        List<PartitionedTreeNode> activePartTreeNodes = new ArrayList<PartitionedTreeNode>();
        List<PartitionedTreeNode> nextActivePartTreeNodes = new ArrayList<PartitionedTreeNode>();

        // Populate active node lists with root:
        activeTreeNodes.add(tree.getRoot());
        PartitionedTreeNode newRoot = new PartitionedTreeNode();
        activePartTreeNodes.add(newRoot);

        // Initialise counter used to number leaves when takeNrsFromFlatTree
        // is false:
        int nextNr = 0;

        while (!activeTreeNodes.isEmpty()) {

            nextActiveTreeNodes.clear();
            nextActivePartTreeNodes.clear();

            for (int idx = 0; idx<activeTreeNodes.size(); idx++) {
                Node treeNode = activeTreeNodes.get(idx);
                PartitionedTreeNode partTreeNode = activePartTreeNodes.get(idx);

                List<Integer> colours = new ArrayList<>();
                List<Double> times = new ArrayList<>();

                while (treeNode.getChildCount()==1) {
                    int col = (int) Math.round(
                            (Double) treeNode.getMetaData(elementLabel));
                    colours.add(col);
                    times.add(treeNode.getHeight());

                    treeNode = treeNode.getLeft();
                }

                // Order changes from youngest to oldest:
                Collections.reverse(colours);
                Collections.reverse(times);

                switch (treeNode.getChildCount()) {
                    case 0:
                        // Leaf at base of branch
                        if (takeNrsFromTree) {
                            partTreeNode.setNr(treeNode.getNr());
                            partTreeNode.setID(String.valueOf(treeNode.getID()));
                        } else {
                            partTreeNode.setNr(nextNr);
                            partTreeNode.setID(String.valueOf(nextNr));
                            nextNr += 1;
                        }
                        break;

                    case 2:
                        // Non-leaf at base of branch
                        nextActiveTreeNodes.add(treeNode.getLeft());
                        nextActiveTreeNodes.add(treeNode.getRight());

                        PartitionedTreeNode daughter = new PartitionedTreeNode();
                        PartitionedTreeNode son = new PartitionedTreeNode();
                        partTreeNode.addChild(daughter);
                        partTreeNode.addChild(son);
                        nextActivePartTreeNodes.add(daughter);
                        nextActivePartTreeNodes.add(son);

                        break;
                }

                // Set node type at base of multi-type beast.evolution.tree branch:
                int nodeType = (int) Math.round(
                        (Double) treeNode.getMetaData(elementLabel));
                partTreeNode.setPartitionElementNumber(nodeType);

                // Set node height:
                partTreeNode.setHeight(treeNode.getHeight());
            }

            // Replace old active node lists with new:
            activeTreeNodes.clear();
            activeTreeNodes.addAll(nextActiveTreeNodes);

            activePartTreeNodes.clear();
            activePartTreeNodes.addAll(nextActivePartTreeNodes);

        }


        // Number internal nodes:
        numberInternalNodes(newRoot, newRoot.getAllLeafNodes().size());

        // Assign beast.evolution.tree topology:
        assignFromWithoutID(new PartitionedTree(newRoot));
        initArrays();

    }

    /**
     * Helper method to assign sensible node numbers
     * to each internal node.  This is a post-order traversal, meaning the
     * root is given the largest number.
     *
     * @param node
     * @param nextNr
     * @return
     */
    protected int numberInternalNodes(Node node, int nextNr) {
        if (node.isLeaf())
            return nextNr;

        for (Node child : node.getChildren())
            nextNr = numberInternalNodes(child, nextNr);

        node.setNr(nextNr);
        node.setID(String.valueOf(nextNr));

        return nextNr+1;
    }

    // here begin the partition utility functions

    public PartitionedTreeNode getElementMRCA(int elementNo){
        HashSet<String> elementTips = new HashSet<>();

        for(Node tip : getExternalNodes()){
            if(((PartitionedTreeNode)tip).getPartitionElementNumber()==elementNo){
                elementTips.add(getTaxonId(tip));
            }

        }
        return (PartitionedTreeNode) TreeUtils.getCommonAncestorNode(this, elementTips);
    }

    public boolean isRootBlockedBy(int elementNo, int maybeBlockedBy){
        PartitionedTreeNode elementMRCA = getElementMRCA(elementNo);
        PartitionedTreeNode potentialBlockingMRCA = getElementMRCA(maybeBlockedBy);

        PartitionedTreeNode currentNode = elementMRCA;

        while(currentNode!=null){
            currentNode = (PartitionedTreeNode)currentNode.getParent();
            if(currentNode == potentialBlockingMRCA){
                return true;
            }

        }
        return false;
    }

    public boolean isRootBlocked(int elementNo){
        for(int i=0; i<elementList.size(); i++){
            if(isRootBlockedBy(elementNo, i)){
                return true;
            }
        }
        return false;

    }

    public boolean isAncestral(PartitionedTreeNode node){

        int elementNo = node.getPartitionElementNumber();

        Set<String> descendentLeaves = TreeUtils.getDescendantLeaves(this, node);

        for(String id : descendentLeaves){
            for(Node tip : getExternalNodes()){
                if(getTaxonId(tip).equals(id)){
                    if(((PartitionedTreeNode)tip).getPartitionElementNumber()==elementNo){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public HashSet<PartitionedTreeNode> getTipsInElement(int elementNo){
        HashSet<PartitionedTreeNode> out = new HashSet<>();

        for(Node node : getExternalNodes()){
            if(((PartitionedTreeNode)node).getPartitionElementNumber()==elementNo){
                out.add((PartitionedTreeNode)node);
            }
        }

        return out;
    }

    public HashSet<PartitionedTreeNode> getNodesInElement(int elementNo){
        HashSet<PartitionedTreeNode> out = new HashSet<>();

        for(Node node : m_nodes){
            if(((PartitionedTreeNode)node).getPartitionElementNumber()==elementNo){
                out.add((PartitionedTreeNode)node);
            }
        }

        return out;
    }

    public HashSet<PartitionedTreeNode> getNodesInSameElement(PartitionedTreeNode node){
        int elementNo = node.getPartitionElementNumber();
        return getNodesInElement(elementNo);
    }

    public PartitionedTreeNode getEarliestNodeInPartition(int elementNo){

        PartitionedTreeNode tipMRCA = getElementMRCA(elementNo);

        if(tipMRCA.getPartitionElementNumber() != elementNo){
            throw new RuntimeException("Node partition disconnected");
        }

        PartitionedTreeNode child = tipMRCA;
        PartitionedTreeNode parent = (PartitionedTreeNode)child.getParent();
        boolean transmissionFound = parent == null;
        while (!transmissionFound) {

            if (child.getPartitionElementNumber() != parent.getPartitionElementNumber()) {
                transmissionFound = true;
            } else {
                child = parent;
                parent = (PartitionedTreeNode)child.getParent();
                if (parent == null) {
                    transmissionFound = true;
                }
            }
        }
        return child;

    }

    public HashSet<PartitionedTreeNode> samePartitionElementUpTree(PartitionedTreeNode node){
        HashSet<PartitionedTreeNode> out = new HashSet<>();
        int elementNo = node.getPartitionElementNumber();
        PartitionedTreeNode currentNode = node;
        PartitionedTreeNode parentNode = (PartitionedTreeNode)node.getParent();
        while(parentNode!=null && parentNode.getPartitionElementNumber() == elementNo){
            out.add(parentNode);
            if(countChildrenInSamePartition(parentNode)==2){
                PartitionedTreeNode otherChild = (PartitionedTreeNode)sibling(currentNode);
                out.add(otherChild);
                out.addAll(samePartitionElementDownTree(otherChild));
            }
            currentNode = parentNode;
            parentNode = (PartitionedTreeNode)currentNode.getParent();
        }
        return out;
    }

    public HashSet<PartitionedTreeNode> samePartitionElementDownTree(PartitionedTreeNode node){
        HashSet<PartitionedTreeNode> out = new HashSet<>();
        int elementCase = node.getPartitionElementNumber();
        for(Node child : node.getChildren()){
            PartitionedTreeNode castChild = (PartitionedTreeNode)child;

            if(castChild.getPartitionElementNumber() == elementCase){
                out.add(castChild);
                out.addAll(samePartitionElementDownTree(castChild));
            }
        }
        return out;
    }

    private int countChildrenInSamePartition(PartitionedTreeNode node){
        if(node.isLeaf()){
            return -1;
        } else {
            int count = 0;
            int parentCase = node.getPartitionElementNumber();
            for(Node child : node.getChildren()){
                if(((PartitionedTreeNode)child).getPartitionElementNumber()  == parentCase){
                    count++;
                }
            }
            return count;
        }
    }

    public static Node sibling(Node node){
        if(node.isRoot()){
            return null;
        } else {
            Node parent = node.getParent();
            for(Node child : parent.getChildren()){
                if(child != node){
                    return child;
                }
            }
        }
        return null;
    }


    /////////////////////////////////////////////////
    // Serialization and deserialization for state //
    /////////////////////////////////////////////////

    /**
     * reconstruct beast.evolution.tree from XML fragment in the form of a DOM node *
     * @param node
     *
     * todo check this works
     */
    @Override
    public void fromXML(org.w3c.dom.Node node) {
        try {
            String sNewick = node.getTextContent().replace("&", "");

            TreeParser parser = new TreeParser();
            parser.initByName(
                    "IsLabelledNewick", false,
                    "offset", 0,
                    "adjustTipHeights", false,
                    "singlechild", true,
                    "newick", sNewick);

            initFromNormalTree(parser, true);

            initArrays();
        } catch (Exception ex) {
            Logger.getLogger(PartitionedTree.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}