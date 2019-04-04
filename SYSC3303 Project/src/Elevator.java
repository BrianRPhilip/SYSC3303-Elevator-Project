
//no main method
//Output: floor request, 
//Input: Motor control (up, down, stop), door (open, close), Floor number (for display), direction (display)
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Elevator extends Thread {
	//UNIFIED CONSTANTS DECLARATION FOR ALL CLASSES
	//States
	private static final byte UP = 0x01;// elevator is going up
	private static final byte DOWN = 0x02;// elevator is going down
	private static final byte STOP = 0x03;
	private static final byte HOLD = 0x04;// elevator is in hold state
	private static final byte UPDATE_DISPLAY = 0x05;
	private static final byte ERROR=(byte)0xE0;//an error has occured
	//Errors
	private static final byte DOOR_ERROR=(byte)0xE1;
	private static final byte MOTOR_ERROR=(byte)0xE2;
	//still error states between 0xE3 to 0xEE for use
	private static final byte OTHER_ERROR=(byte)0xEF; 
	private static final byte NO_ERROR=(byte)0x00;
	//Object ID
	private static final int ELEVATOR_ID = 21;// for identifying the packet's source as elevator
	private static final int FLOOR_ID = 69;// for identifying the packet's source as floor
	private static final int SCHEDULER_ID = 54;// for identifying the packet's source as scheduler
	//Values for Running
	private static final int DOOR_OPEN = 1;// the door is open when == 1
	private static final int DOOR_CLOSE = 3; // the door is closed when == 3 
	private static final int DOOR_DURATION = 4;// duration (in seconds) that doors stay open for
	private static final int REQUEST = 1;// for identifying the packet type sent to scheduler as a request
	private static final int UPDATE = 2;// for identifying the packet type sent to scheduler as a status update
	private static final int MAKE_STOP=3;//
	private static final int PLACE_ON_HOLD=4;
	private static final int UPDATE_DISPLAYS=5;
	private static final int SHUT_DOWN=6;//for shutting down a hard fault problem elevator
	private static final int INITIALIZE=8;//for first communication with the scheduler
	private static final int UNUSED=0;// value for unused parts of data 
	private static final int DOOR_CLOSE_BY=6;//door shouldn't be open for longer than 6 seconds

	public byte motorDirection; 
	public boolean hasRequest = false; // make getters and setters: This Boolean will be set to true when the Elevator
	// Intermediate wants a specific elevator thread to do something.
	// if hasRequest is true, then the Elevator thread will not send another
	// request. Ie, he needs to take care of the job he is told to do by the
	// intermediate
	// before he takes more real time requests by the people. Incidentally,
	// hasRequest == true means that the elevator should move up or down a floor.
	//public boolean hasRTRequest = false; // Real time variable for *****TESTING LINE 1.0******

	public boolean dealWith = false;
	public int elevatorState; // 0x01 is moving up, 0x02 is moving down, 0x03 is stop
	public boolean isUpdate = false; // This boolean is set to true in the ElevatorIntermediate, if the elevator
	// intermediate is expecting an update from the elevator
	//public boolean isGoingUp;
	private boolean elevatorBroken=false; //whether the elevator is broken or not

	private int elevatorNumber;
	private int RealTimefloorRequest;

	protected int sensor; // this variable keeps track of the current floor of the elevator

	DatagramPacket elevatorSendPacket, elevatorReceivePacket;
	DatagramSocket elevatorSendSocket, elevatorReceiveSocket;

	private static byte[] sendData = new byte[8];

	private List<byte[]> elevatorTable;
	private boolean doorStatusOpen = false; // whether the doors are open(true) or closed (false)
	private long doorOpenTime, doorCloseTime;// for error checking that doors are closed within time

	//CONSTRUCTOR
	public Elevator(int name, int initiateFloor, List<byte[]> elevatorTable, int RealTimeFloorRequest) {
		this.elevatorNumber = name; // mandatory for having it actually declared as a thread object
		this.elevatorTable = elevatorTable;
		setSensor(initiateFloor);
		setRealTimeFloorRequest(RealTimeFloorRequest);//this.RealTimefloorRequest = RealTimefloorRequest;
		// arbitrary usage of 23 for port number of Scheduler's receive
		// use a numbering scheme for the naming

		// allocate sockets, packets
		/*
		 * try { //ClientRWSocket = new DatagramSocket(23);//initialize ClientRWSocket
		 * for reading and writing to the Intermediate server //port 23 is the
		 * well-known port number of Intermediate } catch (SocketException se) {//if
		 * DatagramSocket creation fails an exception is thrown se.printStackTrace();
		 * System.exit(1); } //run checking loop indefinitely //status of elevator floor
		 * number, input of floor requests, direction of elevator, motor input, door
		 * input //only waits for packet reception? check data of packet and change
		 * accordingly
		 */
	}
	//SETTERS & SIMPLE FUNCTION METHODS
	public void setRealTimeFloorRequest (int setRequest) {
		RealTimefloorRequest=setRequest;
		hasRequest=true;
	}
	private int setSensor(int floorSensor) { // method to initialize where the elevator starts
		sensor = floorSensor;
		return sensor;
	}
	public void shutDown() {
		elevatorState=STOP;
		System.out.println("Elevator Out of Order, Maintenance and Emergency Fire Services have been Contacted");
		if (elevatorBroken==true) {
			try {// wait for 1000
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	public void fixElevator() {
		elevatorBroken=false;
		System.out.println("Elevator: "+elevatorNumber+" has been fixed");
	}
	public void breakElevator() {
		elevatorBroken=true;
		System.out.println("Elevator: "+elevatorNumber+" is Broken");
	}


	/**
	 * 
	 * @param requestUpdateError
	 * @return byte[] to be put on the synchronized table
	 */
	public byte[] createResponsePacketData(int requestUpdateError, int destinationFloor, byte errorType) {// create the Data byte[] for
		// the response packet to be
		// sent to the scheduler

		/*
		 * ELEVATOR --> SCHEDULER (elevator or floor (elevator-21), elevator id(which
		 * elevator), FloorRequest/update, curentFloor, up or down, destFloor,
		 * instruction) (
		 */
		// creates the byte array according to the required format

		ByteArrayOutputStream requestElevator = new ByteArrayOutputStream();
		requestElevator.write(ELEVATOR_ID); // identification as an elevator, instead of floor, or scheduler
		requestElevator.write(elevatorNumber); // identity of this particular elevator object

		// request or update data
		if (requestUpdateError == REQUEST) {
			requestElevator.write(REQUEST);
			requestElevator.write((byte) sensor); // current floor
			requestElevator.write(UNUSED); // up or down (not used, only for Floors)
			requestElevator.write(destinationFloor); // dest floor
			requestElevator.write(UNUSED); // instruction (not used, only from the scheduler)
			requestElevator.write(UNUSED); // no errors
		} else if (requestUpdateError == UPDATE) {
			requestElevator.write(UPDATE);
			requestElevator.write((byte) sensor); // current floor
			requestElevator.write(elevatorState); // up or down (not used, only for Floors)
			requestElevator.write(UNUSED); // dest floor
			requestElevator.write(UNUSED); // instruction (not used, only from the scheduler)
			requestElevator.write(UNUSED); // no errors
		} else if (requestUpdateError == ERROR) {
			requestElevator.write(ERROR); // ERROR
			requestElevator.write((byte) sensor); // current floor
			requestElevator.write(UNUSED); // up or down (not used, only for Floors)
			requestElevator.write(UNUSED); // dest floor
			requestElevator.write(UNUSED); // instruction (not used, only from the scheduler)
			requestElevator.write(errorType); // error ID
		} else {// something's gone wrong with the call to this method
			requestElevator.write(ERROR); // ERROR
			System.out.println(elevatorNumber+ " Elevator ERROR: called createResponsePacketData with neither REQUEST, UPDATE, or ERROR");
			requestElevator.write((byte) sensor); // current floor
			requestElevator.write(UNUSED); // up or down (not used, only for Floors)
			requestElevator.write(UNUSED); // dest floor
			requestElevator.write(UNUSED); // instruction (not used, only from the scheduler)
			requestElevator.write(OTHER_ERROR); // something's gone wrong
		}
		return requestElevator.toByteArray();
	}

	// COMENTING OUT FOR TESTING REASONS, DO NOT DELETE
	public void openCloseDoor(byte doorOpenCloseError) { 
		//String msg; 
		
			if (doorOpenCloseError == DOOR_OPEN) { //instruction is to open the doors for DOOR_DURATION seconds

				//msg = "Opening Doors"; 
				//System.out.println(msg);
				System.out.println("Opening Doors");
				doorStatusOpen=true;//open the doors
				doorOpenTime=System.nanoTime();//time that the doors opened
				/*try { 
				int i = DOOR_DURATION ; 
				while (i != 0){ 
					System.out.format("Seconds until elevator door closes: %d second \n", i);
					i--; 
					Thread.sleep(1000); //travel time per floor
				}
			} 
			catch (InterruptedException e) {
				e.printStackTrace(); 
			}
			else { 
				msg = "Doors are closed.";
			}*/
				for (int i=DOOR_DURATION;i>0;i--) {
					System.out.format("Seconds until elevator door closes: %d second \n", i);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					} //1 sec 
					System.out.format("Should be calling to Close the Doors");
				}
				//System.out.println()
				//System.out.println(msg); 
			}
			else if (doorOpenCloseError == DOOR_CLOSE) {
				System.out.println("Closing Doors");
				doorStatusOpen=false;
				doorCloseTime=System.nanoTime();// time when the doors closed
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} 
				System.out.println("Doors Closed");
			}
			else {//error 
				sendPacket(ERROR, UNUSED, DOOR_ERROR);
			}
			//check that the doors were closed and done so on time
			if ((doorCloseTime-doorOpenTime)>DOOR_CLOSE_BY*1000000000) {
				sendPacket(ERROR, UNUSED, DOOR_ERROR);
			}
		}
		
	



	public void updateDisplay() {
		System.out.println("Elevator: "+elevatorNumber+" On Floor: " + sensor);
		//System.out.print();
		//if (isGoingUp) {
		if(elevatorState==UP) {//if (motorDirection==UP) {
			System.out.println(" Going Up");
		} 
		//else if (!isGoingUp) {
		else if(elevatorState==DOWN) {//else if (motorDirection==DOWN) {
			System.out.println(" Going Down");
		}
		else if(elevatorState==STOP) {//else if (motorDirection==STOP){
			System.out.println(" at a Stop");
		}
		else if(elevatorState==HOLD) {//else if(motorDirection==HOLD) {
			System.out.println(" on Hold");
		}
		else {
			System.out.println("motorDirection neither UP, DOWN, STOP, nor HOLD... is: "+motorDirection);
		}
	}

	/*public void runElevator() {
		// for testing
		while(elevatorState==UP||elevatorState==DOWN) {
			try {// wait for 1000
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// for
			if (elevatorState == UP) {
				System.out.println("Elevator: "+ elevatorNumber+" is going up...");
				//isGoingUp = true;
				//motorDirection=DOWN;
				sensor++; // increment the floor
				setSensor(sensor); // updates the current floor
			} else if (elevatorState == DOWN) {
				System.out.println("Elevator: "+elevatorNumber+" is going down...");
				//isGoingUp = false;
				//motorDirection=UP;
				sensor--; // decrements the floor
				setSensor(sensor); // updates the current floor
			}
			else if(elevatorState==STOP) {
				System.out.println("Elevator: "+elevatorNumber+" is making a stop");
				openCloseDoor((byte) DOOR_OPEN);
				openCloseDoor((byte) DOOR_CLOSE);
			}
			else if(elevatorState==HOLD) {

			}
			else {
				System.out.println("Elevator: "+elevatorNumber+" received elevatorState: "+elevatorState);
			}
		}
	}*/

	// sets Current location of elevator through this setter

	public synchronized void sendPacket(int requestUpdateError, int destinationFloor, byte sendErrorType) {
		synchronized (elevatorTable) {
			while (elevatorTable.size() != 0) {// wait for an opening to send the packet
				try {
					elevatorTable.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			sendData=createResponsePacketData(requestUpdateError, destinationFloor, sendErrorType);
			elevatorTable.add(sendData);
			// isUpdate = false;
			elevatorTable.notifyAll();
		}
	}

	public synchronized void waitForRequest() {
		while (!hasRequest) {
			try {
				System.out.println("waiting indefinitely");
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void run() {
		
		while (true) {
			if (hasRequest){//while (hasRequest) {// send request
				sendPacket(REQUEST,RealTimefloorRequest, NO_ERROR);
				hasRequest = false;
			}

			while (!hasRequest) {// send updates
				try {
					Thread.sleep(1);// delay for 1 second
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				while (dealWith) {
					//if (motorDirection == UP || motorDirection == DOWN) {
					if (elevatorState == UP) {// || elevatorState == DOWN) {
						//runElevator();
						System.out.println("Elevator: "+elevatorNumber+" going up, at floor: "+sensor);
						setSensor(sensor+1);
						//dealWith =false;// !dealWith;
						sendPacket(UPDATE,UNUSED, NO_ERROR);
					} 
					else if(elevatorState==DOWN) {
						System.out.println("Elevator: "+elevatorNumber+" going down, at floor: "+sensor);
						setSensor(sensor-1);
						//dealWith =false;// !dealWith;
						sendPacket(UPDATE,UNUSED, NO_ERROR);
					}
					else if (elevatorState == UPDATE_DISPLAY) {//else if (motorDirection == UPDATE_DISPLAY) {
						System.out.println("run() while(!hasRequest)'s while (dealWith): UPDATE_DISPLAY");
						updateDisplay();
						dealWith = false;//!dealWith;
						sendPacket(UPDATE,UNUSED, NO_ERROR);
						// set the lights sensors and stuff to proper value
						//isUpdate = false;
					} 
					else if (elevatorState == STOP) {//else if (motorDirection == STOP) {
						System.out.println("run() while(!hasRequest)'s while (dealWith): STOP");
						dealWith = false;//!dealWith;
						sendPacket(UPDATE,UNUSED, NO_ERROR);
					} 
					else if (elevatorState == HOLD) {//else if (motorDirection == HOLD) {
						System.out.println("run() while(!hasRequest)'s while (dealWith): HOLD");
						System.out.println("Reached Hold state in elevator");
						dealWith =false;// !dealWith;
						//waitForRequest();
					}
					else if(elevatorState==SHUT_DOWN) {//else if(motorDirection==SHUT_DOWN) {
						System.out.println("run() while(!hasRequest)'s while (dealWith): SHUT_DOWN");
						shutDown();
						dealWith=false;//
					}
					else {
						System.out.println("run() while(!hasRequest)'s while (dealWith) did not receive an expected instruction: "+elevatorState);
					}
				}
				try {// wait for 1000
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
