/*
* File GuidedPartitionedTree.java
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

import beast.core.Input;
import beast.util.ExtraTreeUtils;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

import java.util.*;

/**
 * @author Matthew Hall <mdhall@ic.ac.uk>
 *
 * A partitioned tree class intended for individual locus phylogenies in a multilocus model. The phylogeny conforms
 * to a transmission structure given by a separate tree with second-type rules, which is the transmission tree.
 */

public class GuidedPartitionedTree extends PartitionedTree {

    public Input<EpidemiologicalPartitionedTree> ttInput = new Input<>("tt", "The transmission tree");

    private EpidemiologicalPartitionedTree tt;

    public final static String elementBranchString = "historyElements";
    public final static String heightBranchString = "historyHeights";

    //what must be added to the height in this tree to get the height in the guide tree
    private double heightAdjustment;

    public void initAndValidate(){
        super.initAndValidate();

        tt = ttInput.get();
        if(tt.getRules() != Rules.COTTAM){
            throw new IllegalArgumentException("The guide tree must have second-type rules");
        }

        double latestGuideDate = Double.NEGATIVE_INFINITY;

        for(Node node : tt.getExternalNodes()){
            if(tt.getDate(node.getHeight())>latestGuideDate){
                latestGuideDate = tt.getDate(node.getHeight());
            }
        }

        double latestDateHere = Double.NEGATIVE_INFINITY;

        for(Node node : getExternalNodes()){
            if(getDate(node.height)>latestDateHere){
                latestDateHere = getDate(node.height);
            }
        }

        //This is a bad thing that shouldn't happen
        if(latestDateHere > latestGuideDate){
            throw new RuntimeException("Tips in the guide tree cannot be dated earlier than the tips from this tree");
        }

        heightAdjustment = latestGuideDate - latestDateHere;
    }

    public double guideTreeHeight(double thisTreeHeight){
        return thisTreeHeight + heightAdjustment;
    }

    public double thisTreeHeight(double guideTreeHeight){
        return guideTreeHeight - heightAdjustment;
    }

    public EpidemiologicalPartitionedTree getGuideTree() {return tt;}

    //Returns the element that the ancestor of this node was present in at the given height (in either this tree or the
    //guide tree.

    private int elementAtHeight(PartitionedTreeNode node, double height, boolean inGuideTree){

        double gtHeight = inGuideTree ? height : guideTreeHeight(height);
        double gtNodeHeight = guideTreeHeight(node.getHeight());

        if(gtHeight < gtNodeHeight){
            throw new IllegalArgumentException("This function is ambiguous if the given height is lower than the " +
                    "node height");
        }

        int nodeElement = node.getPartitionElementNumber();
        PartitionedTreeNode tipInGuideTree = tt.getTip(nodeElement);

        //in the guide tree, internal nodes are transmissions

        PartitionedTreeNode currentNode = tipInGuideTree;
        while(currentNode.getParent() != null && currentNode.getParent().getHeight() < gtHeight){
            currentNode = (PartitionedTreeNode) currentNode.getParent();
        }

        //If the node immediately after this time point is A infecting B, we want to return B
        if(currentNode.getParent()!=null){
            return currentNode.getPartitionElementNumber();
        }

        //if this height is earlier than the root of the guide tree, need to check the length of its root branch
        //todo At some point this should be a bone fide node, but not today

        if(gtHeight > tt.getRoot().getHeight()+tt.getRootBranchLength() ){
            return -1;
        } else {
            return ((PartitionedTreeNode)tt.getRoot()).getPartitionElementNumber();
        }

    }

    //Partition the internal nodes according to the guide; return false if you can't do it

    public boolean updatePartitions(){

        // tips imply that the host was infected at that time

        for(Node tip : getExternalNodes()){
            PartitionedTreeNode castTip = (PartitionedTreeNode)tip;
            if(tip.getHeight() > tt.getInfectionHeightByNr(castTip.getPartitionElementNumber()) ){
                return false;
            }
        }

        //this is going to be slow if done like this, but it will catch problems.

        for(Node node : getInternalNodes()) {

            if(node.getNr()==102){
                System.out.println();
            }

            PartitionedTreeNode castNode = (PartitionedTreeNode) node;
            Set<Integer> requiredElements = new HashSet<>();

            for (Node tip : node.getAllLeafNodes()) {
                PartitionedTreeNode castTip = (PartitionedTreeNode) tip;
                int elementAtNodeHeight = elementAtHeight(castTip, node.getHeight(), false);
                requiredElements.add(elementAtNodeHeight);
            }
            if (requiredElements.size() != 1) {
                return false;
            }
            int elementNo = requiredElements.iterator().next();

            castNode.setPartitionElementNumber(elementNo);
        }

        for(Node node : getNodesAsArray()){

            PartitionedTreeNode castNode = (PartitionedTreeNode)node;
            int elementNo = castNode.getPartitionElementNumber();

            if(elementNo!=-1) {
                List<Integer> ancestralChain = tt.getAncestralChain(elementNo);

                List<Double> transitionHeights = new ArrayList<>();
                List<Integer> infectorsAlongBranch = new ArrayList<>();

                //starting at the end of the branch


                if (!node.isRoot()) {
                    int currentPositionInChain = 0;
                    double currentHeight = node.getHeight();
                    double parentHeight = node.getParent().getHeight();
                    while (currentHeight < parentHeight) {
                        // going up the branch from the bottom, the arrays list the heights of the next transition (or
                        // the parent height) and the element number in the interval ending in that height

                        currentHeight = tt.getInfectionHeightByNr(ancestralChain.get(currentPositionInChain));
                        if (currentHeight < parentHeight) {
                            transitionHeights.add(currentHeight);
                            infectorsAlongBranch.add(ancestralChain.get(currentPositionInChain));
                            currentPositionInChain++;
                        } else {
                            transitionHeights.add(parentHeight);
                            infectorsAlongBranch.add(ancestralChain.get(currentPositionInChain));
                        }
                    }
                } else {
                    //the root branch stretches to infinity
                    for (int no : ancestralChain) {
                        infectorsAlongBranch.add(no);
                        transitionHeights.add(tt.getInfectionHeightByNr(no));
                    }
                }
                int[] branchElements = Ints.toArray(infectorsAlongBranch);
                double[] branchTransitionHeights = Doubles.toArray(transitionHeights);

                node.setMetaData(elementBranchString, branchElements);
                node.setMetaData(heightBranchString, branchTransitionHeights);
            } else {
                //we're above the root of the guide tree
                List<Double> transitionHeights = new ArrayList<>();
                List<Integer> infectorsAlongBranch = new ArrayList<>();
                transitionHeights.add(node.isRoot() ? Double.POSITIVE_INFINITY : node.getParent().getHeight());
                infectorsAlongBranch.add(-1);

                int[] branchElements = Ints.toArray(infectorsAlongBranch);
                double[] branchTransitionHeights = Doubles.toArray(transitionHeights);

                node.setMetaData(elementBranchString, branchElements);
                node.setMetaData(heightBranchString, branchTransitionHeights);
            }

        }
        return true;
    }


    public boolean somethingIsDirty(){
        return super.somethingIsDirty() || tt.somethingIsDirty();
    }

    //extracts the subtree taking place in the transmission tree from here upwards to the root. Probably
    //extremely slow.

    public PartitionedTree extractAncestralCorridor(int elementNo, HashMap<Node, Node> references){
        List<Integer> ancestralChain = tt.getAncestralChain(elementNo);
        PartitionedTreeNode root = (PartitionedTreeNode)getRoot();

        PartitionedTreeNode copyRoot = copyDown(root, references, ancestralChain);

        ExtraTreeUtils.renumberNodes(copyRoot);

        return new PartitionedTree(copyRoot);
    }

    private PartitionedTreeNode copyDown(PartitionedTreeNode oldNode, HashMap<Node, Node> references,
                                         List<Integer> chain){
        double heightInGuideTree = guideTreeHeight(oldNode.getHeight());

        //there is only one partition element that this can be in

        Iterator<Integer> chainIt = chain.iterator();
        int currentElt = chainIt.next();

        while(tt.getInfectionHeightByNr(currentElt) < heightInGuideTree){
            currentElt = chainIt.next();
        }

        int nodeElt = oldNode.getPartitionElementNumber();

        if(currentElt == nodeElt){
            PartitionedTreeNode copyNode = new PartitionedTreeNode();

            copyNode.height = oldNode.getHeight();
            copyNode.metaDataString = oldNode.metaDataString;
            copyNode.setParent(null);
            copyNode.setID(oldNode.getID());

            copyNode.setPartitionElementNumber(oldNode.getPartitionElementNumber());
            references.put(copyNode, oldNode);

            if(!oldNode.isLeaf()){
                for(int i=0; i<oldNode.getChildCount(); i++){
                    copyNode.addChild(copyDown((PartitionedTreeNode)oldNode.getChild(i), references, chain));
                }
            }

            return copyNode;
        } else {
            int infectedElement;

            if (chain.contains(nodeElt)) {
                //this happens if this node is in a host that's not in the chain
                infectedElement = nodeElt;
            } else {
                //the host is in the chain, but tranmission occurred to another host before this time
                infectedElement = currentElt;
            }

            //put in a new tip to represent the point at which an infection went outside the chain

            double newHeight = thisTreeHeight(tt.getInfectionHeightByNr(infectedElement));

            PartitionedTreeNode transNode = new PartitionedTreeNode();
            transNode.height = newHeight;
            transNode.setParent(null);
            transNode.setPartitionElementNumber(infectedElement);
            String tip = chain.contains(nodeElt) ? "To_"+infectedElement : "Continued_"+infectedElement;

            transNode.setID(tip);
            return transNode;
        }
    }


    public void explodeTree(){

//        try {
//            PrintStream firstSteam = new PrintStream("tt.nex");
//
//            PartitionedTreeLogger.debugLog(tt, 0, false, firstSteam);
//
//            PrintStream secondStream = new PrintStream("phy.nex");
//
//            PartitionedTreeLogger.debugLog(this, 0, true, secondStream);
//
//            PrintStream thirdStream = new PrintStream("pure.nex");
//
//
//            init(thirdStream);
//            log(0, thirdStream);
//            close(thirdStream);
//        }catch(FileNotFoundException e){
//            e.printStackTrace();
//        }

//        elementsAsTrees = new HashMap<>();
//        elementsAsTrees.put(-1, new ArrayList<>());
//        for(int i=0; i<getNElements(); i++){
//            elementsAsTrees.put(i, new ArrayList<>());
//        }
//        scanForTreelets((PartitionedTreeNode)getRoot(), elementsAsTrees, null);
    }

//    private Node scanForTreelets(PartitionedTreeNode node, AbstractMap<Integer, List<Treelet>> elementsAsTrees,
//                                 Tree currentTree) {
//        double[] changeHeights = (double[]) node.getMetaData(heightBranchString);
//        int[] changeElements = (int[]) node.getMetaData(elementBranchString);
//
//        if(changeHeights.length == 1){
//            //no infections along the branch
//            if(currentTree == null){
//                //at the root only
//                Node copyRoot = new Node();
//                Tree protoTreelet = new Tree(copyRoot);
//                copyRoot.setHeight(node.getHeight());
//                for (int childNo = 0; childNo < node.getChildCount(); childNo++) {
//                    Node child = scanForTreelets((PartitionedTreeNode) node.getChild(childNo), elementsAsTrees,
//                            protoTreelet);
//                    copyRoot.addChild(child);
//                }
//                copyRoot.setHeight(node.getHeight());
//                protoTreelet = new Tree(copyRoot);
//                protoTreelet.getLeafNodeCount();
//                protoTreelet.getInternalNodeCount();
//
//                Treelet treelet = new Treelet(protoTreelet, changeHeights[0]-node.getHeight());
//                List<Treelet> elementTreelets = elementsAsTrees.get(node.getPartitionElementNumber());
//                elementTreelets.add(treelet);
//                return null;
//
//            } else {
//                Node copy;
//                if (node.isLeaf()) {
//                    copy = new Node(node.getID());
//                    currentTree.addNode(copy);
//                } else {
//                    copy = new Node();
//                    currentTree.addNode(copy);
//                    for (int childNo = 0; childNo < node.getChildCount(); childNo++) {
//                        Node child = scanForTreelets((PartitionedTreeNode) node.getChild(childNo), elementsAsTrees,
//                                currentTree);
//                        copy.addChild(child);
//                    }
//                }
//                copy.setHeight(node.getHeight());
//                return copy;
//            }
//        } else {
//            //the section nearest the child
//            Node copyRoot;
//            Tree protoTreelet;
//            if(node.isLeaf()){
//                copyRoot = new Node(node.getID());
//            } else {
//                copyRoot = new Node();
//                protoTreelet = new Tree(copyRoot);
//                for(int childNo = 0; childNo < node.getChildCount(); childNo++){
//                    Node child = scanForTreelets((PartitionedTreeNode)node.getChild(childNo), elementsAsTrees,
//                            protoTreelet);
//                    copyRoot.addChild(child);
//                }
//            }
//            copyRoot.setHeight(node.getHeight());
//            protoTreelet = new Tree(copyRoot);
//            protoTreelet.getLeafNodeCount();
//            protoTreelet.getInternalNodeCount();
//
//            Treelet treelet = new Treelet(protoTreelet, changeHeights[0]-node.getHeight());
//            List<Treelet> elementTreelets = elementsAsTrees.get(node.getPartitionElementNumber());
//            elementTreelets.add(treelet);
//
//            if(changeElements.length > 2){
//                //one or more little segments
//                for(int segmentNo = 1; segmentNo < changeElements.length-1; segmentNo++){
//                    Node segmentEnd = new Node("Transmission_" + getElementString(changeElements[segmentNo-1]));
//                    Treelet treelet1 = new Treelet(new Tree(segmentEnd),
//                            changeHeights[segmentNo] - changeHeights[segmentNo-1]);
//                    elementTreelets = elementsAsTrees.get(changeElements[segmentNo]);
//                    elementTreelets.add(treelet1);
//                }
//            }
//
//            //the section nearest the parent
//            // an infinite dark section above the first infection is of no interest
//            if(currentTree != null) {
//                Node transmissionTip = new Node("Transmission_"
//                        + getElementString(changeElements[changeElements.length - 2]));
//                currentTree.addNode(transmissionTip);
//                transmissionTip.setHeight(changeHeights[changeElements.length - 2]);
//                return transmissionTip;
//            } else {
//                return null;
//            }
//        }
//
//    }


    // The bundle is the set of branches that are in the given element at the given height (even if neither node is
    // in it); more efficient to borrow the intersectingEdges trick from operators, which also helps if some subtrees
    // are temporarily disconnected mid-proposal

    public int getBundle(PartitionedTreeNode node, double height, int elementNo, List<PartitionedTreeNode> twigs){

        List<Integer> ancestralChain = tt.getAncestralChain(elementNo);

        final PartitionedTreeNode parent = (PartitionedTreeNode)node.getParent();

        //we can stop if:
        //1) We're so far down that the edge cannot intersect
        //2) We're higher up than the required height and in a partition element which is not part of the ancestral
        //chain. There's no way a lineage can re-enter the required element subsequently.

        if((parent != null && parent.getHeight() < height) ||
                (node.getHeight() > height && !(ancestralChain.contains(node.getPartitionElementNumber())))){
            return 0;
        }

        if(parent!=null && !(ancestralChain.contains(parent.getPartitionElementNumber()))){
            throw new RuntimeException("The bundle function is not working");
        }

        if (node.getHeight() < height) {
            if(parent == null || ancestralChain.contains(parent.getPartitionElementNumber())){
                int[] branchElements = (int[])node.getMetaData(elementBranchString);
                double[] transitionHeights = (double[])node.getMetaData(heightBranchString);

                double currentHeight = node.getHeight();
                int currentIndex = -1;
                int currentElement = node.getPartitionElementNumber();

                while(currentHeight < height){
                    currentIndex ++;
                    currentHeight = transitionHeights[currentIndex];
                    currentElement = branchElements[currentIndex];
                }

                if(currentElement == elementNo){
                    if(twigs != null) {
                        twigs.add(node);
                    }
                    return 1;
                }

            }
        }

        int count = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            count += getBundle((PartitionedTreeNode)node.getChild(i), height, elementNo, twigs);
        }
        return count;
    }

    // The hooks are where the phylogeny nodes protruding from the transmission tree branch as it is moved hook
    // onto the rest of the tree. This can feasibly be the root's "parent", and because of difficulties with
    // the root otherwise, this function returns the ends of the hook branches, not the beginnings.
    // They can be found by finding the bundle at the point of infection and then tracing up to the nodes below
    // the points of attachment to the rest of the tree

    public List<PartitionedTreeNode> getHooks(int elementNo, double height){
        // First, get all the nodes whose parent branches are at in the required element at the right time (even if
        // neither node is

        List<PartitionedTreeNode> laterNodes = new ArrayList<>();

        getBundle((PartitionedTreeNode)getRoot(), height, elementNo, laterNodes);

        //but this isn't quite it; if any two nodes in that set coalesce with each other before they coalesce
        //which nodes from elsewhere in the tree, then the hook extends from their ancestor, not them.

        //dear God is this method inefficient right now

        boolean satisfied = false;

        while(!satisfied){
            Set<PartitionedTreeNode> parents = new HashSet<>();
            boolean rootIsIn = false;
            for(PartitionedTreeNode child : laterNodes){
                if(child.getParent() != null){
                    parents.add((PartitionedTreeNode)child.getParent());
                } else {
                    rootIsIn = true;
                }
            }
            //if all the parents are distinct then you can stop.
            if((rootIsIn && parents.size() == laterNodes.size()-1)
                    || (!(rootIsIn) && parents.size() == laterNodes.size())){
                satisfied = true;
            } else {
                outer_loop:
                for(PartitionedTreeNode node1 : laterNodes){
                    for(PartitionedTreeNode node2 : laterNodes){
                        if(node1!=node2){
                            if(node1.getParent() == node2.getParent()){
                                laterNodes.remove(node1);
                                laterNodes.remove(node2);
                                laterNodes.add((PartitionedTreeNode)node1.getParent());
                                break outer_loop;
                            }
                        }
                    }
                }
            }
        }
        return laterNodes;
    }

    public Tree getFlattenedTree() {

        // Create new tree to modify.  Note that copy() doesn't
        // initialise the node array lists, so initArrays() must
        // be called manually.
        Tree flatTree = copy();

        flatTree.initArrays();

        int nextNodeNr = getNodeCount();
        PartitionedTreeNode colourChangeNode;

        for (Node node : getNodesAsArray()) {

            int nodeNum = node.getNr();

            Node newNode = flatTree.getNode(nodeNum);

            newNode.setMetaData(elementLabel,
                    ((PartitionedTreeNode) node).getPartitionElementNumber());

            newNode.metaDataString = String.format("%s=\"%s\"", elementLabel,
                    getElementString(((PartitionedTreeNode) node).getPartitionElementNumber()));

        }

        PartitionedTreeNode newRoot = null;

        for(Node node : flatTree.getNodesAsArray()){

            PartitionedTreeNode originalNode = (PartitionedTreeNode)getNode(node.getNr());

            int[] elementHistory = (int[])originalNode.getMetaData(elementBranchString);
            double[] heightHistory = (double[])originalNode.getMetaData(heightBranchString);

            PartitionedTreeNode currentNode = (PartitionedTreeNode)node;

            if(node.isRoot()){
                for(int eventNo = 0; eventNo < elementHistory.length; eventNo++){
                    if(eventNo != elementHistory.length-1) {
                        colourChangeNode = new PartitionedTreeNode();
                        colourChangeNode.setNr(nextNodeNr);
                        colourChangeNode.setID(String.valueOf(nextNodeNr));
                        nextNodeNr++;

                        currentNode.setParent(colourChangeNode);
                        colourChangeNode.addChild(currentNode);

                        colourChangeNode.setHeight(heightHistory[eventNo]);
                        colourChangeNode.setMetaData(elementLabel, elementHistory[eventNo+1]);
                        colourChangeNode.setPartitionElementNumber(elementHistory[eventNo+1]);
                        currentNode = colourChangeNode;
                    } else {
                        newRoot = currentNode;
                    }
                }
            } else if(elementHistory.length>1){
                //otherwise there is nothing to do
                for(int eventNo = 0; eventNo < elementHistory.length; eventNo++){
                    if(eventNo != elementHistory.length-1) {
                        colourChangeNode = new PartitionedTreeNode();
                        colourChangeNode.setNr(nextNodeNr);
                        colourChangeNode.setID(String.valueOf(nextNodeNr));
                        nextNodeNr++;

                        currentNode.setParent(colourChangeNode);
                        colourChangeNode.addChild(currentNode);

                        colourChangeNode.setHeight(heightHistory[eventNo]);
                        colourChangeNode.setMetaData(elementLabel, elementHistory[eventNo+1]);
                        colourChangeNode.setPartitionElementNumber(elementHistory[eventNo+1]);
                        currentNode = colourChangeNode;
                    } else {

                        PartitionedTreeNode parent = (PartitionedTreeNode)flatTree
                                .getNode(originalNode.getParent().getNr());

                        currentNode.setParent(parent);
                        parent.removeChild(node);
                        parent.addChild(currentNode);

                    }
                }
            }
        }


        //and once again this seems the only way to do it.

        flatTree = new Tree(newRoot);
        flatTree.getInternalNodeCount();
        flatTree.getLeafNodeCount();
        flatTree.initAndValidate();

        return flatTree;
    }

    @Override
    public int countNodesInPartition(int elementNo, boolean internalOnly, boolean forWithinHostPhylogeny){
        //todo work out when this actually needs to be done
        updatePartitions();

        if(forWithinHostPhylogeny && !internalOnly){
            return countDown(elementNo, (PartitionedTreeNode)getRoot());
        } else {
            return super.countNodesInPartition(elementNo, internalOnly, forWithinHostPhylogeny);
        }
    }

    private int countDown(int elementNo, PartitionedTreeNode node){
        int count = 0;

        int[] elementHistory = (int[])node.getMetaData(elementBranchString);

        //the top bit if present
        if(elementHistory.length>1) {
            if (elementHistory[elementHistory.length - 1] == elementNo) {
                count++;
            }
        }

        //any middle bits

        if(elementHistory.length>2){
            for(int portion = elementHistory.length-2; portion>=1; portion--){
                if(elementHistory[portion]==elementNo){
                    count++;
                }
            }
        }

        //the end bit

        count += (node.getPartitionElementNumber() == elementNo ? 1 : 0);
        for(Node child : node.getChildren()){
            PartitionedTreeNode castChild = (PartitionedTreeNode)child;
            count += countDown(elementNo, castChild);
        }
        return count;
    }

}




