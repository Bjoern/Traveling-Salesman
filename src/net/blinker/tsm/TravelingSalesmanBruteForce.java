/*
 * Created on 08-Jan-2006
 *
 * This source code is released under the GNU Lesser General Public License Version 3, 29 June 2007
 * see http://www.gnu.org/licenses/lgpl.html or the plain text version of the LGPL included with this project
 * 
 * It comes with no warranty whatsoever
 */
package net.blinker.tsm;

/**
 * This class implements a brute force algorithm for solving the traveling salesman problem. 
 * It can not handle many cities, since it is a NP complete problem...
 * 
 * @author Bjoern Guenzel - http://blog.blinker.net
 */
public class TravelingSalesmanBruteForce implements Runnable {

	private TravelingSalesman salesman;
	
	private double minCosts;
	
	private int[] minRoute;
	
	private long count;
	
	public TravelingSalesmanBruteForce(TravelingSalesman salesman){
		this.salesman = salesman;
	}

	public void run() {
		int[] route = new int[salesman.n];
		minRoute = new int[salesman.n];
		
		minCosts = -1;
		
		count = 0;
		
		route[0] = 0;//first city always zero
		
		for(int i = 1;i < salesman.n;i++){
			route[1] = i;
			checkRoute(route,2);
		}
		
		System.out.println("Brute force results count: "+count+", minimum cost: "+minCosts+", route: ");
		salesman.printRoute(minRoute);		
	}
	
	private void checkRoute(int[] route, int offset){
		
		if(offset == salesman.n){
			count++;
			
			if(count%100000 == 0){
				System.out.println("check route "+count);
			}
			
			double cost = salesman.calculateCosts(route);
			if(minCosts < 0 || cost < minCosts){
				minCosts = cost;
				System.arraycopy(route,0,minRoute,0,route.length);
			}
			
			return;
		}
		
		loop: for(int i = 1;i<salesman.n;i++){
			for(int j = 0;j<offset;j++){
				if(route[j] == i){
					continue loop;
				}
			}
			
			route[offset] = i;
			checkRoute(route,offset+1);
		}
	}
}
