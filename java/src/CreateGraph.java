/*
 * This code is to create the Markov graph which will be used to transform a trajectory to a signal form
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import DataStructures.State;

public class CreateGraph {

	public static void main(String[] args) throws IOException, ParseException {

		exploreAllStateTransitions();
		obtainStateTransitionProbabilities();	
		regularizeMarkovStateTransitionGraph();
		
		System.out.println("Done!");
		
	}

	public static void exploreAllStateTransitions() throws IOException{
		BufferedReader br = new BufferedReader(new FileReader("data/graph_trips.csv"));
		BufferedWriter bw = new BufferedWriter(new FileWriter("prerequisiteFiles/transitionsAdv.csv"));
		
		HashMap<String, HashMap<String, Integer>> transitions = new HashMap<String, HashMap<String, Integer>>();
		System.out.println("Started to count transitions...");
		String crntLine = br.readLine();
		crntLine = br.readLine();
		String nxtLine = "";

		while((nxtLine = br.readLine()) != null){
			String [] cParts = crntLine.split(",");
			String [] nParts = nxtLine.split(",");		
			
			try{			
				if(cParts[0].equals(nParts[0])){
					
					int cTime = Integer.parseInt(cParts[1]);
					int nTime = Integer.parseInt(nParts[1]);
				
					String cState = cParts[2] + "&" + cParts[3] + "&" + cParts[4]; //Speed&Acc&Angle
					String nState = nParts[2] + "&" + nParts[3] + "&" + nParts[4]; //Speed&Acc&Angle
			
					if((nTime - cTime) == 1){
						
						HashMap<String, Integer> destin = new HashMap<String, Integer>();
						
						if(transitions.containsKey(cState))
							destin = transitions.get(cState);
						
						if(destin.containsKey(nState))
							destin.put(nState, destin.get(nState) + 1);
						else
							destin.put(nState, 1);
						
						transitions.put(cState, destin);
						
					}
				}
			}
			
			catch(Exception e){
				//no further action is required to be taken!
			}
			
			crntLine = nxtLine;
		}
		
		//Iterate over all state and print existing transitions
		System.out.println("Started to write transitions...");
		Iterator it = transitions.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<String, HashMap<String, Integer>> src = (Entry<String, HashMap<String, Integer>>) it.next();
						
			Iterator it2 = src.getValue().entrySet().iterator();
			while(it2.hasNext()){
				Map.Entry<String, Integer> dst = (Entry<String, Integer>) it2.next();
				bw.write(src.getKey() + "," + dst.getKey() + "," + dst.getValue() + "\n");
			}

		}		
		bw.close();
	
	}
	
	public static HashMap<String, HashMap<String, Double>> obtainStateTransitionProbabilities() throws IOException{
		System.out.println("Started to compute probability values ...");
		
		//set Angle and Acceleration bin size
		int headingNormalizationFactor = 1;
		double accelNormalizationFactor = 0.25;
		
		//0. Reading transition raw values
		ArrayList<String> lines = (ArrayList<String>) Files.readAllLines(Paths.get("prerequisiteFiles/transitionsAdv.csv"));
		HashMap<String, Integer> stateTransitionFreq = new HashMap<String, Integer>();
		
		//1. Converting States to normalized values
		for(int i=0; i < lines.size(); i++)
			if(lines.get(i).length() > 0){
				try{
					String[] parts = lines.get(i).split(",");
					
					String[] spdAccAngle = parts[0].split("&");
					int s = (int) Double.parseDouble(spdAccAngle[0]);
					int crntSpeed = s;
					double crntAccel = Math.round(Double.parseDouble(spdAccAngle[1])/accelNormalizationFactor)*accelNormalizationFactor ;
					int crntAngle = (((int) Math.round(Double.parseDouble(spdAccAngle[2])/headingNormalizationFactor))*headingNormalizationFactor)%360;

					spdAccAngle = parts[1].split("&");
					s = (int) Double.parseDouble(spdAccAngle[0]);
					int nxtSpeed = s;
					double nxtAccel = Math.round(Double.parseDouble(spdAccAngle[1])/accelNormalizationFactor)*accelNormalizationFactor;
					int nxtAngle = (((int) Math.round(Double.parseDouble(spdAccAngle[2])/headingNormalizationFactor))*headingNormalizationFactor)%360;

					String trans = crntSpeed + "&" + crntAccel + "&" + crntAngle + "," + nxtSpeed + "&" + nxtAccel + "&" + nxtAngle;
					
					if(stateTransitionFreq.containsKey(trans))
						stateTransitionFreq.put(trans, stateTransitionFreq.get(trans) + Integer.parseInt(parts[2]));
					else
						stateTransitionFreq.put(trans, Integer.parseInt(parts[2]));
				} 
				catch(Exception e){
					
				}
			}
		
		HashMap<String, HashMap<String, Double>> probs = new HashMap<String, HashMap<String,Double>>();
		HashMap<String, Integer> count = new HashMap<String, Integer>();
	
		//calculate count for each state
		Iterator it = stateTransitionFreq.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<String, Integer> pair = (Entry<String, Integer>) it.next();
			String[] parts = pair.getKey().split(",");
			
			if(!parts[0].equals(parts[1])){
				if(count.containsKey(parts[0]))
					count.put(parts[0], count.get(parts[0]) + pair.getValue());
				else
					count.put(parts[0], pair.getValue());
			}
		}
				
		//calculate probability for state transitions
		it = stateTransitionFreq.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<String, Integer> pair = (Entry<String, Integer>) it.next();
			String[] parts = pair.getKey().split(",");
			
			HashMap<String, Double> probsForThisState = new HashMap<String, Double>();
			if(probs.containsKey(parts[0]))
				probsForThisState = probs.get(parts[0]);				
			
			if(!parts[0].equals(parts[1]))
				probsForThisState.put(parts[1], (double)pair.getValue()/count.get(parts[0]));
			
			else if(parts[0].split("&")[1].equals("0.0"))
				probsForThisState.put(parts[0], 1.0); //transfer of a state to itself is 1, if acceleration be 0! otherwise, we cannot have no increase/decrease in speed when acceleration is positive/negative
	
			probs.put(parts[0], probsForThisState);
		}
		
		//print out probability values! for analysis purpose 
		BufferedWriter bw = new BufferedWriter(new FileWriter("prerequisiteFiles/probsAdv.csv"));
		it = probs.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<String, HashMap<String, Double>> prb = (Entry<String, HashMap<String, Double>>) it.next();
			
			Iterator it2 = prb.getValue().entrySet().iterator();
			while(it2.hasNext()){
				Map.Entry<String, Double> pair = (Entry<String, Double>) it2.next();
				bw.write(prb.getKey() + "," + pair.getKey() + "," + pair.getValue() + "," + count.get(prb.getKey()) + "\n");				
			}
		}
		
		bw.close();
		return probs;
	}

	public static void regularizeMarkovStateTransitionGraph() throws IOException{
		System.out.println("Started to load probability values...");
		
		int maxSpeedThreshold = 3; //increase/decrease by steps of size 1.0
		double maxAccelThreshold = 0.25; //increase/decrease by steps of size 0.25
		int maxHeadingThreshold = 6; //Some updates on heading by steps of size 6.0 ==> this is based on Change of Heading instead of abslute heading
		double influenceFactorForAccel = 2.0; //This is a relative factor regarding the influence of accel v.s speed to calculate distance between original and updated states
		
		HashMap<String, Integer> State_To_ID = new HashMap<String, Integer>();
		HashMap<Integer, State> ID_To_State = new HashMap<Integer, State>();		
		
		//Read probability values and initialization
		HashMap<Integer, HashMap<Integer, Double>> probs = new HashMap<Integer, HashMap<Integer,Double>>();
		HashMap<Integer, HashMap<Integer, Double>> regularizedProbs = new HashMap<Integer, HashMap<Integer,Double>>();
		List<String> probValues = Files.readAllLines(Paths.get("prerequisiteFiles/probsAdv.csv"));
		
		for(int i=0; i < probValues.size(); i++){
			String[] parts = probValues.get(i).split(",");
			
			int s1, s2;
			if(!State_To_ID.containsKey(parts[0])){
				State s = new State(parts[0]);				
				State_To_ID.put(parts[0], State_To_ID.size()+1);
				ID_To_State.put(State_To_ID.get(parts[0]), s);
			}
			s1 = State_To_ID.get(parts[0]);
			
			if(!State_To_ID.containsKey(parts[1])){
				State s = new State(parts[1]);				
				State_To_ID.put(parts[1], State_To_ID.size()+1);
				ID_To_State.put(State_To_ID.get(parts[1]), s);				
			}
			s2 = State_To_ID.get(parts[1]);
			
			HashMap<Integer, Double> trans1 = new HashMap<Integer, Double>();
			HashMap<Integer, Double> trans2 = new HashMap<Integer, Double>();
			
			if(probs.containsKey(s1)){
				trans1 = probs.get(s1);
				trans2 = regularizedProbs.get(s1);
			}
			
			trans1.put(s2, Double.parseDouble(parts[2]));
			trans2.put(s2, Double.parseDouble(parts[2]));
			
			probs.put(s1, trans1);
			regularizedProbs.put(s1, trans2);
		}
		
		double lineTracker = 0;
		
		Iterator it = probs.entrySet().iterator();
		while(it.hasNext()){
			lineTracker+=1.0;
			if(regularizedProbs.size()%10 == 0)
				System.out.println("Progress: " + Math.round((lineTracker/probs.size())*10000.00)/100.00 + "%\t" + 
						"#RegularizedStatesWithTransitionsFrom: " + regularizedProbs.size() + "\t#RegularizedStates: " + State_To_ID.size());
			
			Map.Entry<Integer, HashMap<Integer, Double>> source = (Entry<Integer, HashMap<Integer, Double>>) it.next();
			State s1 = ID_To_State.get(source.getKey());
			int srcSpeed = s1.speed;
			double srcAccel = s1.accel;
			int srcHead  = s1.heading;
			
			Iterator it2 = source.getValue().entrySet().iterator();
			while(it2.hasNext()){
				Map.Entry<Integer, Double> destin = (Entry<Integer, Double>) it2.next();
				
				State s2 = ID_To_State.get(destin.getKey());				
				int dstSpeed = s2.speed;
				double dstAccel = s2.accel;
				int dstHead = s2.heading;
				
				//Regularizing by updating the Source
				for(int s=-maxSpeedThreshold; s <= maxSpeedThreshold; s+=1){
					for(double a = -maxAccelThreshold; a <= maxAccelThreshold; a+=0.25){
//						int h =0;
						for(int h = -maxHeadingThreshold; h <= maxHeadingThreshold; h+=6)
						{
							String state = (srcSpeed + s) + "&" + (srcAccel + a) + "&" + (srcHead + h);
							//s*a < 0: change in Speed and Acceleration is not in the same direction
							//Negative speed doesn't make any sense
							//Negative change of heading does'nt sound.
							if((s*a)<0 || (srcSpeed + s)<0 || (srcHead+h)<0)
								continue;
							
							int s3;
							if(!State_To_ID.containsKey(state)){
								State _s = new State(state);
								State_To_ID.put(state, State_To_ID.size()+1);
								ID_To_State.put(State_To_ID.get(state), _s);
							}
							s3 = State_To_ID.get(state);
							
							if(s3 == source.getKey() || s3 == destin.getKey())
								continue;
							
							double absoluteDistanceBetweenStates = 1.0 / (Math.sqrt(s*s + influenceFactorForAccel*a*a + h*h) + 1); //Adding 1 to further regularize the improvement on probability value
							double probAug = destin.getValue() * absoluteDistanceBetweenStates;
																				
							HashMap<Integer, Double> toTheseStates = new HashMap<Integer, Double>();
							if(regularizedProbs.containsKey(s3))
								toTheseStates = regularizedProbs.get(s3);
							
							if(toTheseStates.containsKey(destin.getKey()))
								toTheseStates.put(destin.getKey(), toTheseStates.get(destin.getKey()) + probAug);
							else
								toTheseStates.put(destin.getKey(), probAug);
							
							//Heuristic: if updated acceleration is zero, let's have self transition with probability as 1
							if((srcAccel + a) == 0)
								toTheseStates.put(s3, 1.0);
							
							regularizedProbs.put(s3, toTheseStates);
						}
					}
				}
				
				//Regularizing by updating the Destination
				for(int s=-maxSpeedThreshold; s <= maxSpeedThreshold; s+=1){
					for(double a = -maxAccelThreshold; a <= maxAccelThreshold; a+=0.25){
						for(int h = -maxHeadingThreshold; h <= maxHeadingThreshold; h+=6)
						{
							String state = (dstSpeed + s) + "&" + (dstAccel + a) + "&" + (dstHead + h);
							//s*a < 0: change in Speed and Acceleration is not in the same direction
							//Negative speed doesn't make any sense							
							if((s*a)<0 || (dstSpeed+s)<0 || (dstHead+h)<0)
								continue;
							
							int s3;
							if(!State_To_ID.containsKey(state)){
								State _s = new State(state);
								State_To_ID.put(state, State_To_ID.size()+1);
								ID_To_State.put(State_To_ID.get(state), _s);
							}
							s3 = State_To_ID.get(state);
							
							if(s3 == source.getKey() || s3 == destin.getKey())
								continue;
						
							double absoluteDistanceBetweenStates = 1.0 / (Math.sqrt(s*s + influenceFactorForAccel*a*a + h*h) + 1); //Adding 1 to further regularize the improvement on probability value
							double probAug = destin.getValue() * absoluteDistanceBetweenStates;
							
							HashMap<Integer, Double> toTheseStates = regularizedProbs.get(source.getKey()); 
						
							if(toTheseStates.containsKey(s3))
								toTheseStates.put(s3, toTheseStates.get(s3) + probAug);
							else
								toTheseStates.put(s3, probAug);
							
							regularizedProbs.put(source.getKey(), toTheseStates);
						}
					}
				}
			
			}
		}
		
		
		
		//print out probability values! for analysis purpose 
		BufferedWriter bw = new BufferedWriter(new FileWriter("prerequisiteFiles/probsRegularized.csv"));
		it = regularizedProbs.entrySet().iterator();
		int totalRegularizedTransitions = 0;
		
		while(it.hasNext()){
			Map.Entry<Integer, HashMap<Integer, Double>> prb = (Entry<Integer, HashMap<Integer, Double>>) it.next();
			
			double sum = 0;
			Iterator it2 = prb.getValue().entrySet().iterator();
			while(it2.hasNext()){
				Map.Entry<Integer, Double> pair = (Entry<Integer, Double>) it2.next();
				sum += pair.getValue();
			}
			
//			if(prb.getKey().split("&")[1].equals("0.0"))
			if(ID_To_State.get(prb.getKey()).accel == 0)
				sum -= 1; //We have self transition for this case. Then, need to substract 1 from that.
			String s1 = ID_To_State.get(prb.getKey()).speed + "&" + ID_To_State.get(prb.getKey()).accel + "&" + ID_To_State.get(prb.getKey()).heading;
			
			it2 = prb.getValue().entrySet().iterator();
			while(it2.hasNext()){
				Map.Entry<Integer, Double> pair = (Entry<Integer, Double>) it2.next();
				String s2 = ID_To_State.get(pair.getKey()).speed + "&" + ID_To_State.get(pair.getKey()).accel + "&" + ID_To_State.get(pair.getKey()).heading;
				
				if(pair.getKey() !=  prb.getKey())
					bw.write(s1 + "," + s2 + "," + (pair.getValue()/sum) + "\n");
				else
					bw.write(s1 + "," + s2 + ",1.0\n");
				
				totalRegularizedTransitions++;
			}
		}
		bw.close();
		
		System.out.println("#RegularizedTrans: " + totalRegularizedTransitions);
	}
}