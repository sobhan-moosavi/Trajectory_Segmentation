/*
 * This code performs the main segmentation task by a dynamic programming approach, using the transformed version of trajectories.
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import DataStructures.MeanStd;

public class DynamicProgrammingSegmentation {

	public static void main(String[] args) throws IOException {

		List<String> probDissData = Files.readAllLines(Paths.get("prerequisiteFiles/ProbabilisticDissimilarities.csv"));
		BufferedWriter bw = new BufferedWriter(new FileWriter("output/segmentation_results.csv"));
		bw.write("TripId,TimeStep,Speed,Acceleration,HeadingChange,Latitude,Longitude,PMD,StartOfSegment\n");
		
		// You can modify the maximum number of segments for a trajectory, which is an input to MDL process.
		// The choice of 50 is based on this observation that we usually observe less than this number of segments to be found for a trajectory of moderate size (between 5 to 20 minutes)
		int maximum_number_of_segments = 50;
		
		int n_trips = 0;
		
		String crnt_trip = "";
		List<Double> points = new ArrayList<Double>();
		List<String> trip_points = new ArrayList<>();
		
		// read probabilistic dissimilarity files line by line, separate trajectories, perform segmentation, and print out the results
		for(int i=1; i < probDissData.size(); i++){
			String[] parts = probDissData.get(i).split(","); /* TripId,TimeStep,ProbDissimilarity,Lat,Lng,Speed,Acceleration,Heading */
			if(parts[0].equals(crnt_trip)){
				points.add(Double.parseDouble(parts[2]));
				trip_points.add(parts[0] + "," + parts[1] + "," + parts[5] + "," + parts[6] + 
						"," + parts[7] + "," + parts[3] + "," + parts[4] + "," + parts[2] + ",");
			}
			else{
				if (crnt_trip.length() > 0){
					// do the segmentation
					System.out.print("Segmenting trajectory " + crnt_trip + " ");
					mainSegmentationProcess(points, trip_points, bw, maximum_number_of_segments);
					System.out.println(" done!");
					n_trips += 1;
				}
				crnt_trip = parts[0];
				points = new ArrayList<Double>();
				trip_points = new ArrayList<>();
				points.add(Double.parseDouble(parts[2]));
				trip_points.add(parts[0] + "," + parts[1] + "," + parts[5] + "," + parts[6] + 
						"," + parts[7] + "," + parts[3] + "," + parts[4] + "," + parts[2] + ",");
			}
		}
		
		//do segmentation for the last trajectory
		System.out.print("Segmenting trajectory " + crnt_trip + " ");
		mainSegmentationProcess(points, trip_points, bw, maximum_number_of_segments);
		System.out.println(" done!");
		n_trips += 1;
		
		bw.close();		
		System.out.println("\nThe segmentation process is finished for " + n_trips + " trips!");
	}
	
	public static MeanStd estimateGuassian(List<Double> input, int start, int end){
		MeanStd ms = new MeanStd();
		ms.mean = 0;
		ms.std = 0;
		
		int size = end - start + 1;
		
		for(int i=start; i <= end ; i++)
			ms.mean += input.get(i);		
		ms.mean /= size;
		
		for(int i=start; i <= end ; i++)
			ms.std += Math.pow(input.get(i) - ms.mean, 2);
		
		ms.std = Math.sqrt((1.0/size) * ms.std);	
		 
		return ms;
	}
	
	public static double lnOfNormalDistribution(double x, double mean, double std){
		double value = 0;		
		//value = ln(N(x, mean, std)) where N stands for normal distribution. N(x, mean, std) = (1/std * (2PI)^0.5) * exp(- (x - mean)^2 / (2 * std^2) )
		value = -1 * ((Math.log(std * Math.sqrt(2 * Math.PI))/Math.log(Math.E)) + (Math.pow(x-mean, 2)/(2 * Math.pow(std, 2))));	
		return value;
	}
	
	public static double[][] calculateDelta(List<Double> points){
		//** Calculation of Delta for all form of segments in given trajectory
		//delta_i(n_(i-1) , n_i - 1): the formula before (3) in paper! where n_(i-1) and n_i - 1 can be any possible pairs in trajectory!
		//Simply, here we have a window of dynamic size which scans whole trajectory! we will find the likelihood of that windows based on... 
		//...distribution of its points. Such distribution is a normal distribution and we have the assumption of independence of nodes ...
		//... in order to make simplification in problem
		
		double[][] delta = new double[points.size() - 1][points.size()];
		
		for(int i=0; i < delta.length; i++){
			for(int j=0; j < delta[i].length; j++){
				delta[i][j] = Double.MAX_VALUE; //some form of initialization
				if((j - i + 1) >= 2){//the length of a segment should be at least 2!					
					//get the distribution
					MeanStd ms = estimateGuassian(points, i, j);		
					//get the ln value based on points in this segment and their corresponding distribution
					double value = 0;
					//The goal is minimizing the delta! So, when we have standard deviation = 0, this means no changes is observing in ...
					//...probabilistic dissimilarity values in range i to j! So, no change, no std! In other words, we have a uniform distribution...
					//...for this range! In this way, I decided to let the delta to be 0 in such situation. 
					if(ms.std != 0){
						for(int k=i; k <= j; k++)
							value += lnOfNormalDistribution(points.get(k), ms.mean, ms.std);
						//We have a negative sign behind the ln in original formulation
						value *= -1.0;
					}
					delta[i][j] = value;
				}
			}
		}
		return delta;
	}
	
	public static void dynamicProgramingSegmentation(List<Double> points, int Ns, double[][] delta, double[][] I, int[][] Index){		
		//** Finding optimal segmentation by having N_s as number of existing segments in given trajectory
		//Here, we will find I_k(L) for all k = 1,2,...,N_s and for all L = 1,2,...,N
		//The approach for finding these values is based on dynamic programming formula...
		//... which is provided as relation (4) in paper
		
		//initialization of I
		for(int i=0; i < I.length; i++)
			for(int j =0; j < I[i].length; j++)
				I[i][j] = Double.MAX_VALUE;
		
		//In addition to minimum values, we need minimum indexes which show the best breaking points for segments		
		//initialization of Index
		for(int i=0; i < Index.length; i++)
			for(int j =0; j < Index[i].length; j++)
				Index[i][j] = 0;
		
		//now, I am going to find the optimal values
		for(int k=0; k < Ns; k++)
			
			//Base case: this mean just having a single segment or I_1(L)
			//Note: here k is 0 but based on paper formulation is 1
			if(k == 0){
				//here, the assumption is L>=2; but I considered this once that delta is calculated
				for(int L=0; L < points.size(); L++)
					I[k][L] = delta[k][L]; //look, segments of length 0 or 1 have delta value as +infinity!
			}
		
			//Rest of the cases (having more than one segment): Well, here is the idea of dynamic programming
			else{
				//why (k*2 + 1): look at the printed picture! I have to have some lower level for L! when I have a couple of segments, ... 
				//...the minimum criteria is I have a segment from 0 to 1. And the 2nd segment would be from 2! So, when k=1, the min length...
				//... for L should be 3 in order to have at least two separate segments. 			
				for(int L=(k*2 + 1); L < points.size(); L++){
					double minValue = Double.MAX_VALUE;
					int minIndex = -1;
					//I. Why (L-1)? since I want to let the last segment be at least from L-1 to L. That is, the minimum length is 2!
					//II. Look, here I have equality condition for L not just lower than! since L is index of upper loop and is precise. 
					//III. Why nk_1 = k*2? look at the last line of formula in left column of page 2 of paper. we have nk_1 as starting index ...
					//... of last segment. When have k = 1 or just want two segments, such index is at least 2! this means k*2. 
					for(int nk_1 = k*2; nk_1 <= L-1; nk_1++){
						double value = I[k-1][nk_1 - 1] + delta[nk_1][L];
						if(value < minValue){
							minValue = value;
							minIndex = nk_1;
						}
					}
					I[k][L]     = minValue;
					Index[k][L] = minIndex;
				}				
			}
	}
	
	public static double MinimumDescriptionLength(List<Double> points, int[][] Index, int Ns){
		//Here, the goal is to find the goodness of fit without any kind of over-fitting or a problem which have unseen data. 
		//We use the formula (7) in paper which is written based on a well known research paper: "modeling by shortest data description, 1978"		
		double MDL = 0;
		
		//1. Calculation of first term
		double mle = 0;

		int segmentEnd   = points.size()-1;
		int segmentBegin = -1;
		for(int k=Ns-1; k>= 0; k--){
			double thisSegmentMLE = 0;
			segmentBegin = Index[k][segmentEnd];
			MeanStd ms = estimateGuassian(points, segmentBegin, segmentEnd);
			for(int i=segmentBegin; i < segmentEnd; i++)
				thisSegmentMLE += lnOfNormalDistribution(points.get(i), ms.mean, ms.std);
			mle += thisSegmentMLE;
			
			segmentEnd = segmentBegin -1;
		}
		//there is a negative sign behind the ln in (7)
		mle *= -1;
		MDL = mle;		
	
		//2. Calculation of second term
		//r_k = #parameters for estimated distributions + k - 1
		//Here, such number of parameters is 2*k since we have 2 parameters (mean and std) for every single estimated pdf
		//Also, k = Ns
		double r_k = 2*Ns + Ns - 1;
		MDL += (r_k/2) * (Math.log(points.size())/Math.log(Math.E)); //(r_k/2)*ln(N)
		
		return MDL;
	}

	public static void mainSegmentationProcess(List<Double> points,
			List<String> trip_points,
			BufferedWriter bw,
			int max_number_of_segments) throws IOException{
		//1. Calculation of Delta for all form of segments in given trajectory		
		double[][] delta = calculateDelta(points);
	
		//2. Optimization to find the best value of Ns
		double MDL = Double.MAX_VALUE;
		int bestNs = -1;
		int[][] optimizaedIndex = null;
						
		for(int Ns = 1; Ns <= max_number_of_segments; Ns++){
			//3. Finding optimal segmentation by having N_s as number of existing segments in given trajectory
			double[][] I = new double[Ns][points.size()];		
			//In addition to minimum values, we need minimum indexes which show the best breaking points for segments
			int[][] Index = new int[Ns][points.size()];
			dynamicProgramingSegmentation(points, Ns, delta, I, Index);
			double mdl = MinimumDescriptionLength(points, Index, Ns);
			if(mdl < MDL){
				MDL = mdl;
				bestNs  = Ns;
				optimizaedIndex = Index;
			}
			System.out.print(".");
		}
		
		//3. Using optimize I and Index to get the optimal segment boundaries			
		HashSet<Integer> segmentsPoints = new HashSet<Integer>();
		int lastEndPoint = points.size()-1;
		for(int k=bestNs-1; k>= 0; k--){
			segmentsPoints.add(optimizaedIndex[k][lastEndPoint]);
			lastEndPoint = optimizaedIndex[k][lastEndPoint] - 1;
		}
		
		//4. Writing output
		for(int i=0;i<trip_points.size();i++){
			if (segmentsPoints.contains(i))
				bw.write(trip_points.get(i) + "1\n"); // meaning that this point is start of a new segment
			else
				bw.write(trip_points.get(i) + "0\n"); // meaning that this point is not start of any segment
		}
	}
}
