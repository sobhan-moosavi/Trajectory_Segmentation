/*
 * This code is to transform a trajectory from its original space to a new space that we call it probabilistic movement dissimilarity. 
 * We perform this transformation using the Markov graph that we generate using the CreateGraph process
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import DataStructures.tripTuple;

public class TrajectoryTransformation {
	public static int zeroCounter = 0; 
	public static int totalCounter = 0;
	public static double avgTransProb = 0; //this value will be used for missing transition probability; i.e. for those with 0 prob.

	public static void main(String[] args) throws IOException {
		
		HashMap<String, ArrayList<tripTuple>> tripData = new HashMap<String, ArrayList<tripTuple>>();
		
		// loading trajectory (or trip) data
		loadTripData(tripData);
		System.out.println("data is loaded!");
		
		// performing transformation
		ComputingProbDissForAllTrips(tripData);
		
		// printing some stats
		System.out.println("\ndone!");
		System.out.println("%time that PMD was zero because of non existing states: " + 
				Math.round(((double)zeroCounter/totalCounter)*10000.00)/100.00 + "%");		
		System.out.println("zeroCounter: " + zeroCounter + "\ntotalCounter: " + totalCounter);
		
	}
	
	public static void ComputingProbDissForAllTrips(HashMap<String, ArrayList<tripTuple>> tripData) throws IOException{

		//Load State Transition Probability
		HashMap<String, HashMap<String, Double>> transProb = new HashMap<String, HashMap<String,Double>>();			
		BufferedReader br = new BufferedReader(new FileReader("prerequisiteFiles/probsRegularized.csv"));
		String line = "";
		int count = 0;
		
		while((line=br.readLine())!=null){
			String[] parts = line.split(",");
			double prob = Double.parseDouble(parts[2]);
			avgTransProb += prob; count++;
			
			HashMap<String, Double> trans = new HashMap<String, Double>();
			
			if(transProb.containsKey(parts[0]))
				trans = transProb.get(parts[0]);
			
			trans.put(parts[1], prob);
			
			transProb.put(parts[0], trans);
		}
		avgTransProb /= count;
//		System.out.println(count + "\t" + avgTransProb);
		System.out.println("probability values are loaded!");
		
		//specify output file	
		BufferedWriter bw = new BufferedWriter(new FileWriter("prerequisiteFiles/ProbabilisticDissimilarities.csv"));

		//set transition threshold 
		int minLength = 1;
		//set Angle bin size
		int angleBinSize = 1;
		//set top candidates for comparison
		int numberOfTrips = 0;
	
		Iterator it = tripData.entrySet().iterator();
		bw.write("TripId,TimeStep,ProbDissimilarity,Lat,Lng,Speed,Acceleration,Heading\n");
		while(it.hasNext()){
			Map.Entry<String, ArrayList<tripTuple>> entry = (Entry<String, ArrayList<tripTuple>>) it.next();
			
			int crntTripLegth = entry.getValue().size();
			int down = 0;
			int up = crntTripLegth;
			
			if(crntTripLegth < minLength)
				continue;
			
			//get the most recent available heading values
			//Why getting last heading? Currently, we use heading as values between 0 to 359. So, if have no GPS coordinates for some time ...
			//during trip, then we no longer can use 180. So, last heading gives the closest (time base closeness) available heading value to be used as an estimation
			
			numberOfTrips ++;
			
			System.out.println(entry.getKey() + "\t" + entry.getValue().size());

			String prevState =  (int)entry.getValue().get(down).speed + "&" +  (int)Math.round(entry.getValue().get(down).acceleration*0.25)/0.25 +
					"&" + (int)Math.round(entry.getValue().get(down).heading*angleBinSize)/angleBinSize;
			
			for(int i=down+1;i<up;i++){
				
				String crntState =  (int)entry.getValue().get(i).speed + "&" + (int)Math.round(entry.getValue().get(i).acceleration*0.25)/0.25 +
						"&" + (int)Math.round(entry.getValue().get(i).heading*angleBinSize)/angleBinSize;
			
				double distance = 0;
				
				if(!crntState.equals(prevState))
					distance = getProbabilisticDistance(crntState, prevState, transProb.get(prevState));
				
				bw.write(entry.getKey() + "," + entry.getValue().get(i).timeStep + "," + distance + "," + 
						entry.getValue().get(i).lat + "," + entry.getValue().get(i).lng + "," + entry.getValue().get(i).speed + 
						"," + entry.getValue().get(i).acceleration + "," + entry.getValue().get(i).heading + "\n");			
				
				//changing states
				prevState = crntState;
			}
		}
		
		System.out.println("number of trips: " + numberOfTrips);
		bw.close();
	}
	
	private static double getProbabilisticDistance(String crntState, String prevState, HashMap<String, Double> transProb) {
		double distance = 0;
		totalCounter++;
		if(transProb == null){
			zeroCounter++;
			return avgTransProb;
		}
		Iterator it = transProb.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<String, Double> trans = (Entry<String, Double>) it.next();
			if(trans.getKey().equals(prevState))
				continue; //no update!
			distance += (returnEuclideanDistance(crntState, trans.getKey()) * trans.getValue());//probabilistic distance
		}
		
		return distance;
	}

	public static void loadTripData(HashMap<String, ArrayList<tripTuple>> tripData) throws IOException{
		
		//load TRIP data
		BufferedReader br = new BufferedReader(new FileReader("data/segmentation_trips.csv"));
		String line = br.readLine(); // this is the header line
		// header line: TripId,Time_Step,Speed(km/h),Acceleration(m/s^2),Heading_Change(degrees),Latitude,Longitude

		while((line = br.readLine()) != null){
			try{
				String[] parts = line.split(",");
				
				tripTuple tr = new tripTuple();
				tr.timeStep = Integer.parseInt(parts[1]);
				tr.speed = Double.parseDouble(parts[2]);
				tr.acceleration = Double.parseDouble(parts[3]);
				tr.heading = Double.parseDouble(parts[4]);
				tr.lat = Double.parseDouble(parts[5]);
				tr.lng = Double.parseDouble(parts[6]);
				
				ArrayList<tripTuple> lst = new ArrayList<tripTuple>();
				if(tripData.containsKey(parts[0]))
					lst = tripData.get(parts[0]);
				lst.add(tr);
				tripData.put(parts[0], lst);
			}
			catch(Exception e){
				continue;
			}
		}
		
	}

	public static double returnEuclideanDistance(String first, String second){
		double distance = 0;
		
		double maxSpeed = 180 /*it was 178 previously*/, minSpeed = 0;
		double maxAccel = 19,  minAccel = -16;
		double maxAngle = 180, minAngle = 0;
		
		String[] firstParts  = first.split("&");
		String[] secondParts = second.split("&");
		
		double f_angle = Double.parseDouble(firstParts[2]);
		double s_angle = Double.parseDouble(secondParts[2]);
		double f_modified = 360 - f_angle;
		double s_modified = 360 - s_angle;
		
		double headingDistance = 0;
		
		if(Math.abs(f_angle-s_angle) > Math.abs(f_angle + s_modified))
			headingDistance = f_angle + s_modified;
		else if(Math.abs(f_angle-s_angle) > Math.abs(f_modified + s_angle))
			headingDistance = f_modified + s_angle;
		else
			headingDistance = Math.abs(f_angle - s_angle);
		headingDistance = (headingDistance - minAngle)/(maxAngle - minAngle);
		
		double firstSpeed = (Double.parseDouble(firstParts[0]) - minSpeed)/(maxSpeed - minSpeed);
		double firstAccel = (Double.parseDouble(firstParts[1]) - minAccel)/(maxAccel - minAccel);		
		
		double secondSpeed = (Double.parseDouble(secondParts[0]) - minSpeed)/(maxSpeed - minSpeed);
		double secondAccel = (Double.parseDouble(secondParts[1]) - minAccel)/(maxAccel - minAccel);
		
		distance = Math.sqrt(Math.pow(firstSpeed - secondSpeed, 2) + Math.pow(firstAccel - secondAccel, 2) + Math.pow(headingDistance, 2));
		
		return distance;
	}
}
