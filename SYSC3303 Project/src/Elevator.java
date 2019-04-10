
//no main method
//Output: floor request, 
//Input: Motor control (up, down, stop), door (open, close), Floor number (for display), direction (display)
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;//for measuring time to respond
/**
 * Each elevator thread acts as one of the 4 elevators. Takes requests from the user to go the floors.
 * There is display that shows movement of the elevators and opening and closing of doors. 
 * @author Group 5
 *
 */
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
	private static final int FIX_ELEVATOR=7;//
	private static final int INITIALIZE=8;//for first communication with the scheduler
	private static final int UNUSED=0;// value for unused parts of data 
	private static final int DOOR_CLOSE_BY=6;//door shouldn't be open for longer than 6 seconds

	public byte motorDirection; 
	public boolean hasRequest = false; // This Boolean will be set to true when the Elevator
	// Intermediate wants a specific elevator thread to do something.
	// if hasRequest is true, then the Elevator thread will not send another
	// request. Ie, it needs to take care of the job it is told to do by the
	// intermediate before it takes more real time requests by the user. Incidentally,
	// hasRequest == true means that the elevator should move up or down a floor.

	protected boolean dealWith = false;// Elevator Intermediate sets this boolean as true for a specific elevator to do a sertain job. Elevator will set it back to false when done. 
	public int elevatorState; // 0x01 is moving up, 0x02 is moving down, 0x03 is stop
	public int previousState;
	public boolean isUpdate = false; // This boolean is set to true in the ElevatorIntermediate, if the elevator
	// intermediate is expecting an update from the elevator
	//public boolean isGoingUp;
	private boolean elevatorBroken=false; //whether the elevator is broken or not
	private byte elevatorError;

	private int elevatorNumber;
	private int RealTimefloorRequest;

	protected int sensor; // this variable keeps track of the current floor of the elevator

	DatagramPacket elevatorSendPacket, elevatorReceivePacket;// Datagram packet for sending and receiving
	DatagramSocket elevatorSendSocket, elevatorReceiveSocket;// Datagram socket for sending and receiving
	
	private static byte[] sendData = new byte[8];

	private List<byte[]> elevatorTable;
	private boolean doorStatusOpen = false; // whether the doors are open(true) or closed (false)
	private long doorOpenTime, doorCloseTime;// for error checking that doors are closed within time

	//FOR TIMING
	private static long elevatorTravelTimingStart, elevatorTravelTimingEnd;
	private static long schedulerSendTime,schedulerReeiveTime;
	private static boolean initialTravelTimeCall=true;

	private static final int SENDPORTNUM = 369;//FOR THIS TESTING ONLY


	//CONSTRUCTOR
	public Elevator(int name, int initiateFloor, DatagramSocket sendSocket) {//List<byte[]> elevatorTable) {//, int RealTimeFloorRequest) {
		this.elevatorNumber = name; // mandatory for having it actually declared as a thread object
		//this.elevatorTable = elevatorTable;
		this.elevatorSendSocket=sendSocket;//
		setSensor(initiateFloor);
		elevatorState=HOLD;
		previousState=HOLD;
	}
	//SETTERS & SIMPLE FUNCTION METHODS
	/**
	 * 
	 * @param int parameter will be set as the real time request for the elevator's motion
	 */
	public void setRealTimeFloorRequest (int setRequest) {
		RealTimefloorRequest=setRequest;
		hasRequest=true;
	}
	/**
	 * 
	 * @param int floorSensor will be set as the current location of the elevator
	 * @return returns sensor(current location of the elevator)
	 */
	private int setSensor(int floorSensor) { // method to initialize where the elevator starts
		sensor = floorSensor;
		return sensor;
	}
	/**
	 * Puts the elevator into out of order mode during a hard fault. "Emergency services" are automatically contacted
	 */
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
	/**
	 * fixes the broekn elevator, lets know in the console that the elevator has been marked "fixed"
	 */
	public void fixElevator() {
		elevatorBroken=false;
		System.out.println("Elevator: "+elevatorNumber+" has been fixed\n");
		elevatorState=previousState;
		dealWith=true;
	}
	/**
	 * 
	 * @param errorType byte is passed on, has to match one of the error bytes that are standard for the whole system
	 * breaks the elevator with that error type. 
	 */
	public void breakElevator(byte errorType) {
		elevatorBroken=true;
		elevatorError=errorType;
		//System.out.println("Elevator: "+elevatorNumber+" is Broken");

		if (elevatorError==MOTOR_ERROR){
			System.out.println("MOTOR_ERROR/ 'floor timer fault': hard fault");
			//System.out.println("Before MOTOR_ERROR sendpacket() call");
			sendPacket(ERROR,UNUSED, MOTOR_ERROR);
			//System.out.println("After MOTOR_ERROR sendpacket() call");
		}
		else if (elevatorError==DOOR_ERROR){
			System.out.println("Before DOOR_ERROR sendpacket() call");
			//System.out.println("DOOR_ERROR: transient fault");
			sendPacket(ERROR,UNUSED, DOOR_ERROR);
			//System.out.println("After MOTOR_ERROR sendpacket() call");
		}
		else {
			System.out.println("Before OTHER_ERROR sendpacket() call");
			//System.out.println("OTHER_ERROR: transient fault ");
			sendPacket(ERROR,UNUSED, OTHER_ERROR);
			//System.out.println("After MOTOR_ERROR sendpacket() call");
		}
		//System.out.println("end of breakElevator() method reached");
	}


	//SEND AND RECEIVE METHODS
	/**
	 * 
	 * @param requestUpdateError: int that says whether this is a Request, Update, or and Error code for the scheduler
	 * @param destinationFloor: int that says which floor the elevator wants to go to. This is unused for Updates and Error calls.
	 * @param errorType: If createResponsePacketData has been called due to error, then what type of error is it
	 * @return returns the Response byte to be sent to the Scheduler
	 */
	public byte[] createResponsePacketData(int requestUpdateError, int destinationFloor, byte errorType) {// create the Data byte[] for
		// the response packet to be sent to the scheduler

		// creates the byte array according to the required format
		ByteArrayOutputStream requestElevator = new ByteArrayOutputStream();
		requestElevator.write(ELEVATOR_ID); // identification as an elevator, instead of floor, or scheduler
		requestElevator.write(elevatorNumber); // identity of this particular elevator object

		// request, update, error data
		if (requestUpdateError == REQUEST) {
			requestElevator.write(REQUEST);
			requestElevator.write((byte) sensor); // current floor
			requestElevator.write(UNUSED); // direction
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
	/**
	 * 
	 * @param requestUpdateError: int that says whether this is a Request, Update, or and Error code for the scheduler
	 * @param destinationFloor: int that says which floor the elevator wants to go to. This is unused for Updates and Error calls.
	 * @param sendErrorType: If createResponsePacketData has been called due to error, then what type of error is it
	 * creates a packet through createResponsePacketData() method and sends it to the Scheduler class
	 */
	public void sendPacket(int requestUpdateError, int destinationFloor, byte sendErrorType) {
		//FOR THIS TESTING ONLY
		//System.out.println("Elevator "+elevatorNumber+"'s SendPacket() called");
		synchronized(elevatorSendSocket) {
			sendData=createResponsePacketData(requestUpdateError,destinationFloor,sendErrorType);
			try {
				elevatorSendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getLocalHost() ,	SENDPORTNUM);
			} catch (UnknownHostException e) {
				System.out.println("Send Error for Elevator: "+elevatorNumber);
			}
			
			try {
				System.out.println("\nElevator: "+elevatorNumber+" Sending to scheduler: " + Arrays.toString(sendData));
				elevatorSendSocket.send(elevatorSendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			elevatorSendSocket.notifyAll();
		}
	}

	// COMENTING OUT FOR TESTING REASONS, DO NOT DELETE
	/**
	 * 
	 * @param doorOpenCloseError: Instruction to Open or Close the door of the elevator 
	 */
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
	/**
	 * Updates the display(Console) of the elevator depending on the current state of the elevator
	 */
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
		else if (elevatorBroken==true) {
			System.out.println(" Broken");
		}
		else {
			System.out.println("motorDirection neither UP, DOWN, STOP, nor HOLD... is: "+motorDirection);
		}
	}
	/**
	 * Waiting method that the elevator will go on to once the HOLD state is reached from the scheduler's instructions
	 */
	public void waitForRequest() {
		while (!hasRequest) {
			try {
				System.out.println("waiting indefinitely");
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
	
		}
	}
	/**
	 * Run method for the Elevator thread that will take care of the elevator states, instructions, and real time requests when the system is in use
	 */
	public void run() {

		while (true) {
			if (hasRequest){//while (hasRequest) {// send request
				sendPacket(REQUEST,RealTimefloorRequest, NO_ERROR);
				System.out.println("Elevator: "+elevatorNumber+"'s Request sent");
				hasRequest = false;
				//sendPacket(REQUEST,RealTimefloorRequest, NO_ERROR);
			}

			while (!hasRequest) {// send updates
				try {
					Thread.sleep(1);// delay for 1 second
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				while (dealWith) {
					//if (motorDirection == UP || motorDirection == DOWN) {
					if (elevatorState == UP) {
						/*if (initialTravelTimeCall) {
							initialTravelTimeCall=false;
							elevatorTravelTimingStart=System.nanoTime();
						}
						elevatorTravelTimingEnd=System.nanoTime();
						if (elevatorTravelTimingEnd-elevatorTravelTimingStart>=1000000000) {
							System.out.println("Elevator: "+elevatorNumber+" going up, at floor: "+sensor);
							setSensor(sensor+1);
							//dealWith =false;// !dealWith;
							sendPacket(UPDATE,UNUSED, NO_ERROR);
						}*/
						runElevator();//UP);
						dealWith=false;
					} 
					else if(elevatorState==DOWN) {
						/*System.out.println("Elevator: "+elevatorNumber+" going down, at floor: "+sensor);
						setSensor(sensor-1);
						 */
						runElevator();//DOWN);
						dealWith =false;// !dealWith;
						//sendPacket(UPDATE,UNUSED, NO_ERROR);
					}
					else if (elevatorState == UPDATE_DISPLAY) {//else if (motorDirection == UPDATE_DISPLAY) {
						System.out.println("Elevator: "+elevatorNumber+"'s run() while(!hasRequest)'s while (dealWith): UPDATE_DISPLAY");
						updateDisplay();
						
						sendPacket(UPDATE,UNUSED, NO_ERROR);
						dealWith = false;//!dealWith;
						// set the lights sensors and stuff to proper value
						//isUpdate = false;
					} 
					else if (elevatorState == STOP) {//else if (motorDirection == STOP) {
						System.out.println("Elevator: "+elevatorNumber+"'s run() while(!hasRequest)'s while (dealWith): STOP");
						dealWith = false;//!dealWith;
						//sendPacket(UPDATE,UNUSED, NO_ERROR);
					} 
					else if (elevatorState == HOLD) {//else if (motorDirection == HOLD) {
						System.out.println("Elevator: "+elevatorNumber+"'s run() while(!hasRequest)'s while (dealWith): HOLD");
						System.out.println("Reached Hold state in elevator");
						dealWith =false;// !dealWith;
						//sendPacket(UPDATE,UNUSED, NO_ERROR);
						//waitForRequest();
					}
					else if(elevatorState==SHUT_DOWN) {//else if(motorDirection==SHUT_DOWN) {
						System.out.println("Elevator: "+elevatorNumber+"'s run() while(!hasRequest)'s while (dealWith): SHUT_DOWN");
						shutDown();
						dealWith=false;//
						//sendPacket(UPDATE,UNUSED, NO_ERROR);
					}
					else if (elevatorState==FIX_ELEVATOR) {
						fixElevator();
						dealWith=false;
						//sendPacket(UPDATE,UNUSED, NO_ERROR);
					}
					else if(elevatorState==UNUSED) {
						//do nothing
						dealWith=false;
					}
					else {
						System.out.println("Elevator: "+elevatorNumber+"'s run() while(!hasRequest)'s while (dealWith) did not receive an expected instruction: "+elevatorState);
						dealWith=false;
					}
				}
			}
		}
	}
	
	/**
	 * Turns the elevator Motor up or down floors when instructed by the scheduler. 
	 */
	public void runElevator() {//int direction) {
		System.out.println("runElevator() method called");
		if(elevatorState==UP) {
			sensor++;
			
		}
		else if(elevatorState==DOWN) {
			sensor--;
		}
		else {
			System.out.println("Something went wrong in runElevator() of Elevator: "+elevatorNumber);
		}
		sendPacket(UPDATE,UNUSED,NO_ERROR);
			try {// wait for 1000
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			dealWith=true;
		//}
	}
}
