/*
 * Created on 08-Jan-2006
 * 
 * This source code is released under the GNU Lesser General Public License Version 3, 29 June 2007
 * see http://www.gnu.org/licenses/lgpl.html or the plain text version of the LGPL included with this project
 * 
 * It comes with no warranty whatsoever
 * 
 */
package net.blinker.tsm;

import java.util.Random;

import net.blinker.ga.Environment;

/**
 * Run the traveling salesman solver
 * 
 * The genetic algorithm will run untill it is aborted, and prints the 
 * current best solution from time to time
 * 
 * Edit the source to switch to brute force algorithm, change the random seed or the number of cities 
 * Some parameters are hardcoded in the Environment class
 * This should be improved in future versions. The current state of the code is merely a proof of concept,
 * little attention has been paid to proper engineering. 
 * 
 * @author Bjoern Guenzel - http://blog.blinker.net
 */
public class TravelingSalesmanSolver {

	public final static int RANDOM_SEED = 1234;
	
	public final static boolean RUN_BRUTE_FORCE = true;
	public final static boolean RUN_GENETIC_ALGORITHM = true;
	
	public static Random random = new Random(RANDOM_SEED);
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//TODO interpret arguments, for example random seed and number of cities, and brute force or genetic algorithm could be 
		TravelingSalesman salesman = new TravelingSalesman(10, random);
		
		salesman.printCosts();
		
		//uncomment for brute force: 
		if(RUN_BRUTE_FORCE){
			System.out.println("*** running brute force algorithm ***");
			TravelingSalesmanBruteForce bruteForce = new TravelingSalesmanBruteForce(salesman);
			bruteForce.run();
		} 
		
		if(RUN_GENETIC_ALGORITHM) {
			System.out.println("*** running genetic algorithm algorithm ***");
			Environment environment = new Environment(salesman);

			environment.run();
		}
	}

}
