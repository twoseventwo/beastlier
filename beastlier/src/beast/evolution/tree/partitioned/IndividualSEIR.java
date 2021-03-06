/*
* File IndividualSEIR.java
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
package beast.evolution.tree.partitioned;

import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.math.distributions.ParametricDistribution;
import beastlier.durations.DurationDistribution;
import beastlier.durations.FixedValueDurationDistribution;
import beastlier.geography.SpatialKernel;
import beastlier.outbreak.CategorySet;
import beastlier.outbreak.ClinicalCase;
import beastlier.outbreak.GeographicallyLocatedClinicalCase;
import org.apache.commons.math.FunctionEvaluationException;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Matthew Hall <mdhall@ic.ac.uk>
 */

public class IndividualSEIR extends BetweenHostModel {

    public Input<SpatialKernel> kernelInput = new Input<>("kernel", "The spatial kernel function; if null or absent, " +
            "geography will be ignored", null, Input.Validate.OPTIONAL);
    public Input<List<DurationDistribution>> latentInput = new Input<>("latent", "One or more categories uniting " +
            "latent periods across clinical cases", new ArrayList<>());
    public Input<List<DurationDistribution>> infectiousInput = new Input<>("infectious", "One or more categories " +
            "uniting infectious periods across clinical cases", new ArrayList<>());
    public Input<RealParameter> baseTransmissionRateInput = new Input<>("baseTransmissionRate", "The unmodified " +
            "transmission rate");
    public Input<ParametricDistribution> initialInfectionTimePriorInput = new Input<>("initialInfectionTimePrior",
            "The prior distribution for the time of the index infection", null, Input.Validate.OPTIONAL);
    public Input<CategorySet> infectiousCategorySetInput = new Input<>("infectiousCategorySet", "Assignment of " +
            "clinical cases to distributions of infectious periods");
    public Input<CategorySet> latentCategorySetInput = new Input<>("latentCategorySet", "Assignment of clinical " +
            "cases to distributions of periods");


    private SpatialKernel kernel;
    private RealParameter baseTransmissionRate;
    private ParametricDistribution initialInfectionTimePrior;
    private HashMap<ClinicalCase, Double> indexCasePrior;
    private boolean hasGeography;
    private List<DurationDistribution> latentCategories;
    private List<DurationDistribution> infectiousCategories;
    private Map<ClinicalCase, DurationDistribution> latentCategoriesMap;
    private Map<ClinicalCase, DurationDistribution> infectiousCategoriesMap;

    public void initAndValidate(){
        super.initAndValidate();

        kernel = kernelInput.get();

        hasGeography = kernel != null;

        hasLatentPeriods = true;

        baseTransmissionRate = baseTransmissionRateInput.get();
        initialInfectionTimePrior = initialInfectionTimePriorInput.get();

        latentCategories = latentInput.get();

        CategorySet latentCategorySet = latentCategorySetInput.get();

        for(DurationDistribution category : latentCategories){
            if(category.hasProbability()){
                throw new RuntimeException("Latent periods in this model are identical");
            }
        }

        latentCategoriesMap = new HashMap<>();

        for(ClinicalCase aCase : outbreak.getCases()){
            for(DurationDistribution category : latentCategories){
                if(category.getID().equals(latentCategorySet.getDistributionName(aCase.getID()))){
                    latentCategoriesMap.put(aCase, category);
                }
            }
        }

        infectiousCategories = infectiousInput.get();

        CategorySet infectiousCategorySet = infectiousCategorySetInput.get();

        infectiousCategoriesMap = new HashMap<>();

        for(ClinicalCase aCase : outbreak.getCases()){
            for(DurationDistribution category : infectiousCategories){
                if(category.getID().equals(infectiousCategorySet.getDistributionName(aCase.getID()))){
                    infectiousCategoriesMap.put(aCase, category);
                }
            }
        }

        double totalWeights = 0;
        super.initAndValidate();

        indexCasePrior = new HashMap<>();

        for(ClinicalCase aCase : outbreak.getCases()){
            totalWeights += aCase.getIndexPriorWeight();
        }

        for(ClinicalCase aCase : outbreak.getCases()){
            indexCasePrior.put(aCase, aCase.getIndexPriorWeight()/totalWeights);
        }

        if(kernel!=null && !outbreak.hasGeography()){
            throw new IllegalArgumentException("Kernel specified but ClinicalCases have no attached geographical" +
                    " information");
        }
    }

    public double evaluateLogP(){

//        try {
//            PrintStream firstSteam = new PrintStream("tt.nex");
//
//            PartitionedTreeLogger.debugLog(tree, 0, false, firstSteam);
//        } catch (FileNotFoundException e){
//            e.printStackTrace();
//        }

        double transLogProb = 0;

        double rate = baseTransmissionRate.getValue();

        ArrayList<ClinicalCase> previouslyInfectious = new ArrayList<>();

        double currentEventTime;
        boolean first = true;

        for (TreeEvent event : sortedTreeEvents) {
            currentEventTime = event.getTime();

            ClinicalCase thisCase = event.getCase();

            if (event.getType() == EventType.INFECTION) {
                if (first) {
                    // index infection

                    if (indexCasePrior != null) {
                        transLogProb += Math.log(indexCasePrior.get(thisCase));
                    }
                    if (initialInfectionTimePrior != null) {
                        transLogProb += initialInfectionTimePrior.logDensity(currentEventTime);
                    }
                    if (!hasLatentPeriods) {
                        previouslyInfectious.add(thisCase);
                    }

                    first = false;

                } else {

                    ClinicalCase infector = event.getInfector();

                    if(thisCase.wasEverInfected()) {
                        if (previouslyInfectious.contains(thisCase)){
                            return Double.NEGATIVE_INFINITY;
                        }
                        if (event.getTime() > thisCase.getEndTime()){
                            return Double.NEGATIVE_INFINITY;
                        }
                        if (infector.getEndTime() < event.getTime()){
                            return Double.NEGATIVE_INFINITY;
                        }
                        if (getInfectiousTime(infector) > event.getTime()) {
                            return Double.NEGATIVE_INFINITY;
                        }
                        if(!previouslyInfectious.contains(infector)){
                            throw new RuntimeException("Infector not previously infected");
                        }
                    }

                    // no other previously infectious case has infected this case...

                    for (ClinicalCase nonInfector : previouslyInfectious) {

                        double timeDuringWhichNoInfection;
                        if (nonInfector.getEndTime() < event.getTime()) {
                            timeDuringWhichNoInfection = nonInfector.getEndTime() - getInfectiousTime(nonInfector);
                        } else {
                            timeDuringWhichNoInfection = event.getTime() - getInfectiousTime(nonInfector);
                        }

                        if(timeDuringWhichNoInfection < 0){
                            throw new RuntimeException("negative time");
                        }

                        double transRate = rate;
                        if (hasGeography) {
                            try {
                                transRate *= kernel.getValue((GeographicallyLocatedClinicalCase) thisCase,
                                        (GeographicallyLocatedClinicalCase) nonInfector);
                            } catch (FunctionEvaluationException e){
                                e.printStackTrace();
                            }
                        }

                        transLogProb += -transRate * timeDuringWhichNoInfection;
                    }

                    // ...until the end

                    if(thisCase.wasEverInfected()) {
                        double transRate = rate;
                        if (hasGeography) {
                            try {
                                transRate *= kernel.getValue((GeographicallyLocatedClinicalCase) thisCase,
                                        (GeographicallyLocatedClinicalCase) infector);
                            } catch (FunctionEvaluationException e){
                                e.printStackTrace();
                            }
                        }
                        transLogProb += Math.log(transRate);
                    }
                    if (!hasLatentPeriods) {
                        previouslyInfectious.add(thisCase);
                    }
                }

            } else if (event.getType() == EventType.INFECTIOUSNESS) {
                if (event.getTime() < Double.POSITIVE_INFINITY) {

                    if(event.getTime() > event.getCase().getEndTime()){
                        return Double.NEGATIVE_INFINITY;
                    }

                    if (first) {
                        throw new RuntimeException("First event is not an infection");
                    }

                    previouslyInfectious.add(thisCase);
                }
            }
        }

        double periodsLogProb = 0;

        for(DurationDistribution category : infectiousCategories){
            if(category.hasProbability()) {
                List<ClinicalCase> relevantCases = new ArrayList<>();

                for(ClinicalCase aCase : outbreak.getCases()){
                    if(getInfectiousCategory(aCase)==category){
                        relevantCases.add(aCase);
                    }
                }

                Double[] infectiousPeriods = new Double[relevantCases.size()];

                for (int i = 0; i < infectiousPeriods.length; i++) {
                    infectiousPeriods[i] = relevantCases.get(i).getEndTime() - getInfectiousTime(relevantCases.get(i));
                }

                RealParameter collectionOfValues = new RealParameter(infectiousPeriods);

                periodsLogProb += category.getLogProbability(collectionOfValues);
            }
        }

        // just reject states where these round to +INF

        if(transLogProb == Double.POSITIVE_INFINITY){
            System.out.println("TransLogProb +INF");
            return Double.NEGATIVE_INFINITY;
        }
        if(periodsLogProb == Double.POSITIVE_INFINITY){
            System.out.println("PeriodsLogProb +INF");
            return Double.NEGATIVE_INFINITY;
        }

        logP = periodsLogProb + transLogProb;

        return logP;
    }

    public double getInfectiousTime(ClinicalCase aCase){
        DurationDistribution category = getLatentCategory(aCase);
        if(category.hasProbability()){
            throw new RuntimeException("Latent periods in this model are fixed");
        }

        FixedValueDurationDistribution castCategory = (FixedValueDurationDistribution)category;

        return getInfectionTime(aCase) + castCategory.getValue();
    }

    public DurationDistribution getLatentCategory(ClinicalCase aCase){
        return latentCategoriesMap.get(aCase);
    }

    public DurationDistribution getInfectiousCategory(ClinicalCase aCase){
        return infectiousCategoriesMap.get(aCase);
    }

    public void init(final PrintStream out){
        for(int i=0; i<outbreak.getEverInfectedCases().size(); i++){
            out.print(outbreak.getEverInfectedCases().get(i) + "_infectionTime\t");
        }
        for(int i=0; i<outbreak.getEverInfectedCases().size(); i++){
            out.print(outbreak.getEverInfectedCases().get(i) + "_infectiousTime\t");
        }
        for(int i=0; i<outbreak.getEverInfectedCases().size(); i++){
            out.print(outbreak.getEverInfectedCases().get(i) + "_infectiousPeriod\t");
        }
        for(int i=0; i<outbreak.getEverInfectedCases().size(); i++){
            out.print(outbreak.getEverInfectedCases().get(i) + "_lengthOfInfection\t");
        }
        out.print("bhm_logP" + "\t");
    }

    public void log(final int sample, final PrintStream out){
        for(int i=0; i<outbreak.getEverInfectedCases().size(); i++){
            out.print(tree.getInfectionTime(outbreak.getEverInfectedCase(i)) + "\t");
        }
        for(int i=0; i<outbreak.getEverInfectedCases().size(); i++){
            out.print(getInfectiousTime(outbreak.getEverInfectedCase(i)) + "\t");
        }
        for(int i=0; i<outbreak.getEverInfectedCases().size(); i++){
            out.print((outbreak.getEverInfectedCase(i).getEndTime()
                    - getInfectiousTime(outbreak.getEverInfectedCase(i))) + "\t");
        }
        for(int i=0; i<outbreak.getEverInfectedCases().size(); i++){
            out.print((outbreak.getEverInfectedCase(i).getEndTime()
                    - tree.getInfectionTime(outbreak.getEverInfectedCase(i))) + "\t");
        }
        out.print(logP + "\t");
    }

    /**
     * @return a list of unique ids for the state nodes that form the argument
     */
    public List<String> getArguments(){
        List<String> out = new ArrayList<>();
        out.add(outbreakInput.get().getID());
        out.add(treeInput.get().getID());

        return out;
    }

    /**
     * @return a list of unique ids for the state nodes that make up the conditions
     */
    public List<String> getConditions(){
        List<String> out = new ArrayList<>();
        out.addAll(kernelInput.get().getParameterIds());
        out.add(baseTransmissionRateInput.get().getID());
        for(DurationDistribution latCat : latentCategories){
            if(latCat.isDirtyCalculation()){
                out.add(latCat.getID());
            }
        }

        return out;
    }

    @Override
    protected boolean requiresRecalculation() {

        boolean aLatentPeriodHasChanged = false;

        for(DurationDistribution latCat : latentCategories){
            if(latCat.isDirtyCalculation()){
                aLatentPeriodHasChanged = true;
            }
        }

        boolean treeHasChanged = super.requiresRecalculation();

        boolean answer = kernelInput.get().isDirtyCalculation() || baseTransmissionRateInput.get().somethingIsDirty()
                || treeHasChanged || aLatentPeriodHasChanged;

        if(treeHasChanged || aLatentPeriodHasChanged){
            typeOfDirt = IS_FILTHY;
        } else {
            typeOfDirt = answer ? IS_DIRTY : IS_CLEAN;
        }
        return answer;
    }
}
