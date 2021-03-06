/*
* File RandomPartition.java
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
package beast.util;

import beast.core.Description;
import beast.core.Input;
import beast.core.StateNode;
import beast.core.StateNodeInitialiser;
import beast.evolution.tree.Node;
import beast.evolution.tree.PartitionedTree;
import beast.evolution.tree.PartitionedTreeNode;
import beast.evolution.tree.Tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author Matthew Hall <mdhall@ic.ac.uk>
 */

@Description("Takes an unpartitioned tree and randomly partitions it.")
public class RandomPartition extends PartitionedTree implements StateNodeInitialiser {

    public Input<Tree> unpartitionedInput = new Input<>("unpartitioned", "An unpartitioned tree to randomly partition");

    public void initAndValidate(){

        assignFrom(unpartitionedInput.get());

        elementLabel = elementLabelInput.get();

        processTraits(m_initial.get().m_traitList.get());

        super.initAndValidate();

        initStateNodes();
    }


    @Override
    public void initStateNodes() {

        //sigh... if RandomTree calls this twice at the start of a run then I guess I have to too. Which means
        //resetting the internal node partition.

        for(Node node : getInternalNodes()){
            PartitionedTreeNode castNode = (PartitionedTreeNode)node;
            castNode.setPartitionElementNumber(-1);
            node.setMetaData(elementLabel, null);
        }

        PartitionedTreeNode root = (PartitionedTreeNode) getRoot();
        randomlyAssignNode(root, false);

        if (m_initial.get() != null) {
            m_initial.get().assignFromWithoutID(this);
        }

    }


    private Object randomlyAssignNode(PartitionedTreeNode node, boolean allowCreep){

        if(node.isLeaf()){
            return node.getPartitionElementNumber();
        } else {

            //If this is a descendant of a case MRCA and an ancestor of one of that case's tips, it must be
            //assigned that case. If it is that of two cases then this tree is incompatible

            ArrayList<Object> forcedByTopology = new ArrayList<Object>();

            for(Object elementLabel : elementList){

                int elementNo = elementList.indexOf(elementLabel);

                Node elementMRCA = getElementMRCA(elementNo);
                List<PartitionedTreeNode> caseTips = getTipsInElement(elementNo);

                for (PartitionedTreeNode caseTip : caseTips) {
                    if (directDescendant(node, elementMRCA) && directDescendant(caseTip, node)) {
                        if(!forcedByTopology.contains(elementLabel)) {
                            forcedByTopology.add(elementLabel);
                        }
                    }
                }
            }

            if(forcedByTopology.size()>1){
                throw new RuntimeException("Starting phylogeny is incompatible with this tip partition");
            } else if(forcedByTopology.size()==1){
                node.setPartitionElementNumber(elementList.indexOf(forcedByTopology.get(0)));
                node.setMetaData(elementLabel, forcedByTopology.get(0));

                for (int i = 0; i < node.getChildCount(); i++) {
                    PartitionedTreeNode child = (PartitionedTreeNode)node.getChild(i);

                    if(!child.isLeaf()){
                        randomlyAssignNode(child, allowCreep);
                    }
                }

                return forcedByTopology.get(0);
            } else {
                //not mandated by the topology
                //three choices - case of child 1, case of child 2, case of parent, unless this is the root

                Object[] choices = new Object[2];

                for (int i = 0; i < node.getChildCount(); i++) {
                    PartitionedTreeNode child = (PartitionedTreeNode)node.getChild(i);

                    if(!child.isLeaf()){
                        choices[i] = randomlyAssignNode(child, allowCreep);
                    } else {
                        choices[i] = elementList.get(child.getPartitionElementNumber());
                    }
                }
                //if both choices are null and we're at the root, try again

                while(node.isRoot() && choices[0]==null && choices[1]==null){
                    for (int i = 0; i < node.getChildCount(); i++) {
                        PartitionedTreeNode child = (PartitionedTreeNode)node.getChild(i);

                        if(!child.isLeaf()){
                            choices[i] = randomlyAssignNode(child, allowCreep);
                        } else {
                            choices[i] = elementList.get(child.getPartitionElementNumber());
                        }
                    }
                }

                int randomSelection;
                if (node.isRoot()) {
                    //must make a choice at this point
                    randomSelection = Randomizer.nextInt(2);
                    //they can't both be null
                    if(choices[randomSelection]==null){
                        randomSelection = 1-randomSelection;
                    }
                    Object winner = choices[randomSelection];
                    fillDownTree(node, elementList.indexOf(winner));
                    return winner;

                } else {
                    randomSelection = Randomizer.nextInt(allowCreep ? 3 : 2);
                }
                if (randomSelection != 2) {
                    Object winner = choices[randomSelection];

                    if(winner!=null) {
                        fillDownTree(node, elementList.indexOf(winner));
                    } else {
                        node.setPartitionElementNumber(elementList.indexOf(winner));
                        node.setMetaData(elementLabel, winner);
                    }

                    return winner;

                } else {
                    //parent partition element will creep to here, but we don't know what that is yet
                    return null;
                }
            }
        }
    }

    private void fillDownTree(PartitionedTreeNode node, int elementNo){
        if(node.getPartitionElementNumber() == -1){
            node.setPartitionElementNumber(elementNo);
            node.setMetaData(elementLabel, elementList.get(elementNo));
            for(int i=0; i<2; i++){
                PartitionedTreeNode child = (PartitionedTreeNode)node.getChild(i);
                fillDownTree(child, elementNo);
            }
        }
    }

    @Override
    public void getInitialisedStateNodes(List<StateNode> stateNodes) {
        stateNodes.add(this);
    }
}
