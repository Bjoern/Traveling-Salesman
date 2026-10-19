/*
 * Created on 06-Jan-2006
 * 
 * This source code is released under the GNU Lesser General Public License Version 3, 29 June 2007
 * see http://www.gnu.org/licenses/lgpl.html or the plain text version of the LGPL included with this project
 * 
 * It comes with no warranty whatsoever
 */
package net.blinker.ga;

import java.util.Random;

import net.blinker.tsm.TravelingSalesman;

/**
 * The environment holds the random seed (for reproducible simulations), the population, the mutationrate, keeps track of fitness
 * and runs the genetic algorithm. 
 * 
 * Since it was written as a proof of concept, it currently has too many dependencies on the TravelingSalesman class. It would
 * be desireable to user more abstractions, so that the GA can be used for a wider class of problems. Also parameters like
 * population size and mutation rate shouldn't be hardcoded. As a proof of concept it works, though.
 * 
 * In each step, the algorithm picks two parents and replaces a random gene with the offspring of the two parents. The likelihood
 * for a parent to be picked depends on it's fitness 
 *  
 * @author Bjoern Guenzel - http://blog.blinker.net
 */
public class Environment {

	public final static float MUTATION_RATE = 0.003f;
	
	public final static int RANDOM_SEED = 1234;
	
	public final static int POPULATION_SIZE = 500;//must be even number

	/** extra bits per allele, to reduce the bias of "allele % n" */
	public final static int EXTRA_BITS = 2;

	/** number of genes drawn for each tournament when picking a parent */
	public final static int TOURNAMENT_SIZE = 3;

	/** probability that an offspring is made by crossover rather than copying one parent */
	public double crossoverRate = 1.0;
	
	public static Random random = new Random(RANDOM_SEED);

	public int bitsPerAllele;
	
	public int allelesCount;
	public int bitsPerGene;
	public int bytesPerGene;
	
	public Gene[] genes;
	
	public Gene[] oldGenes;
	
	public double[] fitnesses;
	
	public double fitnessSum;
	
	private TravelingSalesman travelingSalesman;
	
	private int[] route;//temporary variable for calculating the fitness
	
	private double minCost;
	int fittestGeneIndex;
	
	public Environment(TravelingSalesman travelingSalesman){
		this.travelingSalesman = travelingSalesman;
		
		initGenes();
		
		route = new int[travelingSalesman.n];
		
		fitnesses = new double[POPULATION_SIZE];
	}
	
	public void run(){
		
		int generation = 0;
		calculateFitnesses();
		
		//main loop
		while(true){
			if(generation % POPULATION_SIZE == 0){
				System.out.println("\nGeneration: "+generation+", cost: "+minCost);
				System.out.print("\nminimum gene: ");
				printGene(genes[fittestGeneIndex]);
				initRouteFromGene(genes[fittestGeneIndex]);
				System.out.print("\nminimum route: ");
				printRoute();
				System.out.print("\n");
			}
			
			createNewOffspring();
			
			generation++;
		}
	}
	
	
	private void initGenes(){
		//0 to n-1 for the cities, n to denote the seperator. Superfluous numbers are being ignored
		// Enough bits to reach every city (the old floor() left the top cities unreachable),
		// plus EXTRA_BITS so that "% n" below is close to uniform.
		bitsPerAllele = 32 - Integer.numberOfLeadingZeros(travelingSalesman.n - 1) + EXTRA_BITS;
		
		//2n alleles, as we use pair permutations
		allelesCount = 2*travelingSalesman.n;
		bitsPerGene = (allelesCount*bitsPerAllele);
		bytesPerGene = (bitsPerGene+Byte.SIZE-1)/Byte.SIZE;
		
		genes = new Gene[POPULATION_SIZE];
		oldGenes = new Gene[POPULATION_SIZE];
		
		//random initial genes
		for(int i = 0;i<genes.length;i++){
			byte[] geneData = new byte[bytesPerGene];
			
			Environment.random.nextBytes(geneData);
			
			genes[i] = new Gene(geneData, this);
			oldGenes[i] = new Gene(new byte[bytesPerGene], this);
		}
	}	
	
	private void createNewOffspring(){
		//indices of parent genes
		int parent1 = pickGene();
		int parent2 = pickGene();
		
		//replace the least fit gene that is not a parent (replacing a random gene gave
		//almost no selection pressure). The fittest gene is never the least fit one.
		int replacedGene = -1;
		for(int i = 0;i<genes.length;i++){
			if(i == parent1 || i == parent2){
				continue;
			}
			if(replacedGene < 0 || fitnesses[i] < fitnesses[replacedGene]){
				replacedGene = i;
			}
		}
		
		//replace old gene with offspring of i1 and i2
		genes[parent1].createOffspring(genes[parent2], genes[replacedGene]);
		
		//update fitnessSum: remove fitness of old gene, add fitness of new gene
		
		fitnessSum -= fitnesses[replacedGene];
		
		initRouteFromGene(genes[replacedGene]);
		
		double cost = travelingSalesman.calculateCosts(route);
		
		if(minCost < 0 || cost < minCost){
			minCost = cost;
			fittestGeneIndex = replacedGene;
		}
		
		fitnesses[replacedGene] = 1/cost;
		
		fitnessSum += fitnesses[replacedGene];
	}

	/**
	 * Tournament selection: draw TOURNAMENT_SIZE random genes and return the fittest of them.
	 *
	 * This replaces fitness-proportional (roulette) selection. With fitness = 1/cost, tours that
	 * differ in length by a few percent had almost the same chance to be picked, so selection
	 * pressure was close to zero. Tournament selection only depends on the ranking.
	 * (The roulette also drew its selector with nextDouble() % fitnessSum, which only covers
	 * the whole wheel while fitnessSum < 1.)
	 *
	 * @return index into the population, pointing to a gene
	 */
	private int pickGene() {
		int best = random.nextInt(genes.length);

		for(int i = 1;i<TOURNAMENT_SIZE;i++){
			int candidate = random.nextInt(genes.length);
			if(fitnesses[candidate] > fitnesses[best]){
				best = candidate;
			}
		}

		return best;
	}
	
	private void calculateFitnesses(){
		
		fitnessSum = 0;
		
		minCost = -1;
		
		for(int i = 0;i<POPULATION_SIZE;i++){
			initRouteFromGene(genes[i]);
			double cost = travelingSalesman.calculateCosts(route);
			
			if(minCost < 0 || cost < minCost){
				minCost = cost;
				fittestGeneIndex = i;
			}
			
			fitnesses[i] = 1/cost;
		
			fitnessSum += fitnesses[i];
			
//			System.out.print("\ngene: ");printGene(genes[i]);
//			System.out.print("\nroute: ");printRoute();
//			System.out.println("\ncost: "+cost+", fitness: "+fitnesses[i]);
		}
	}
	
	private void initRouteFromGene(Gene gene){
		
		//init to ordered route
		for(int i = 0;i<route.length;i++){
			route[i] = i;
		}
		
		int lastAllele = travelingSalesman.n;
		
		//apply transpositions
		
		for(int i = 0;i<allelesCount;i++){
			//differeing strategies to deal with superfluous alleles - modulo n favours some numbers, 
			//but presumably so would any other scheme
			int allele = gene.getAllele(i)%travelingSalesman.n;

			//alleles form disjoint pairs (a0 a1)(a2 a3)...; the old "i > 0" applied an
			//overlapping chain (a0 a1)(a1 a2)(a2 a3)... instead
			if(i % 2 == 1){
				int tmpCity = route[allele];
				route[allele] = route[lastAllele];
				route[lastAllele] = tmpCity;
			}
			
			lastAllele = allele;
		}
		
	}
	
	private void printRoute(){
		for(int i = 0;i<route.length;i++){
			System.out.print(route[i]+" ");
		}
	}
	
	private void printGene(Gene gene){
		for(int i = 0;i<allelesCount;i++){
			//differeing strategies to deal with superfluous alleles - modulo n favours some numbers, 
			//but presumably so would any other scheme
			int allele = gene.getAllele(i)%travelingSalesman.n;
			
			if(i%2 == 0){
				System.out.print("(");
			} 
			System.out.print(allele);
			if(i%2 == 0){
				System.out.print(" ");
			} else {
				System.out.print(") ");
			}
		}
	}
	
}
