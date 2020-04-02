package DataStructures;

public class State {
	public int speed;
	public double accel;
	public int heading;
	
	public State(String input){
		String[] parts = input.split("&");
		this.speed = Integer.parseInt(parts[0]);
		this.accel = Double.parseDouble(parts[1]);
		this.heading = Integer.parseInt(parts[2]);
	}
}
