import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.lang.*;

public class Scheduler extends Thread {

	// Packets and sockets required to connect with the Elevator and Floor class

	public static DatagramSocket schedulerSocketSendReceiveElevator, schedulerSocketSendReceiveFloor;
	public static DatagramPacket schedulerElevatorSendPacket, schedulerElevatorReceivePacket, schedulerFloorSendPacket,
			schedulerFloorReceivePacket;

	public static int PORTNUM = 69;
	// Variables

	public static byte data[] = new byte[7];
	public static byte dataFloor[] = new byte[7];
	public static int elevatorOrFloor;
	public static int elevatorOrFloorID;
	public static int requestOrUpdate;
	public static int currentFloor;
	public static int upOrDown;
	public static int destFloor;
	public static int instruction;

	// number of elevators and floors. Can change here!
	public static int numElevators = 2;
	public static int numFloors = 15;

	// lists to keep track of what requests need to be handled
	public static Object obj = new Object();
	public static int limit = numFloors * numElevators;

	// scheduling alogrithm variable declaration
	public static int elevatorCurrentFloor[] = new int[numElevators];
	public static int elevatorStatus[] = new int[numElevators];
	public static int elevatorNextStop[] = new int[numElevators];

	public static int elevatorNumStops[] = new int[numElevators];
	public static int elevatorHighestRequestFloor[] = new int[numElevators];
	public static int elevatorLowestRequestFloor[] = new int[numElevators];

	// temporary sorting algorithm variables
	public static int floorRequestDirection;
	public static LinkedList<Integer>[] elevatorRequestsUp = new LinkedList[numElevators];
	public static LinkedList<Integer>[] elevatorStopsUp = new LinkedList[numElevators];
	public static LinkedList<Integer>[] elevatorRequestsDown = new LinkedList[numElevators];
	public static LinkedList<Integer>[] elevatorStopsDown = new LinkedList[numElevators];
	public static InetAddress packetAddress;
	public static int packetPort;

	public static byte[] sendData = new byte[7];

	// Declare timing constants
	public static final int TIME_PER_FLOOR = 1;// time for the elevator to travel per floor
	public static final int DOOR_DURATION = 4;// time that taken for the door to open and close when given the open
	// command (door closes automatically after alloted time)

	// Declare Motor States:
	private static final byte HOLD = 0x00;// elevator is in hold state
	private static final byte UP = 0x02;// elevator is going up
	private static final byte DOWN = 0x01;// elevator is going down
	private static final int ELEVATOR_ID = 21;// for identifying the packet's source as elevator
	private static final int FLOOR_ID = 69;// for identifying the packet's source as floor
	private static final int SCHEDULER_ID = 54;// for identifying the packet's source as scheduler
	private static final int DOOR_OPEN = 1;// the door is open when ==1
	// private static final int DOOR_DURATION=4;//duration that doors stay open for
	private static final int REQUEST = 1;// for identifying the packet sent to scheduler as a request
	private static final int UPDATE = 2;// for identifying the packet sent to scheduler as a status update

	public void linkedListInitialization() {
		for (int i = 0; i < numElevators; i++) {
			elevatorRequestsUp[i] = new LinkedList<Integer>();
			elevatorStopsUp[i] = new LinkedList<Integer>();
			elevatorRequestsDown[i] = new LinkedList<Integer>();
			elevatorStopsDown[i] = new LinkedList<Integer>();
		}
	}

	public Scheduler() {
		try {
			schedulerSocketSendReceiveElevator = new DatagramSocket(369);
			schedulerSocketSendReceiveFloor = new DatagramSocket();// can be any available port, Scheduler will reply
			// to the port
			// that's been received
		} catch (SocketException se) {// if DatagramSocket creation fails an exception is thrown
			se.printStackTrace();
			System.exit(1);
		}
	}

	public static DatagramPacket elevatorReceivePacket() {
		/* ELEVATOR RECEIVING PACKET HERE */
		schedulerElevatorReceivePacket = new DatagramPacket(data, data.length);
		// System.out.println("Server: Waiting for Packet.\n");

		// Block until a datagram packet is received from receiveSocket.
		try {
			System.out.println("waiting");
			schedulerSocketSendReceiveElevator.receive(schedulerElevatorReceivePacket);
			System.out.println("Request from elevator: " + Arrays.toString(data));

			// schedulerSocketReceiveElevator.close();
			// schedulerSocketSendReceiveElevator.close()

		} catch (IOException e) {
			System.out.print("IO Exception: likely:");
			System.out.println("Receive Socket Timed Out.\n" + e);
			e.printStackTrace();
			System.exit(1);
		}
		/* Separating byte array received */

		elevatorOrFloor = data[0];
		elevatorOrFloorID = data[1];
		requestOrUpdate = data[2];
		currentFloor = data[3];
		upOrDown = data[4];
		destFloor = data[5];
		return schedulerElevatorReceivePacket;
	}

	public static void floorReceivePacket() {
		/* FLOOR RECEIVING PACKET HERE */
		schedulerElevatorReceivePacket = new DatagramPacket(dataFloor, dataFloor.length);

		// Block until a datagram packet is received from receiveSocket.
		try {
			System.out.println("waiting");
			schedulerSocketSendReceiveFloor.receive(schedulerFloorReceivePacket);
			System.out.println("Request from elevator: " + Arrays.toString(data));
		} catch (IOException e) {
			System.out.print("IO Exception: likely:");
			System.out.println("Receive Socket Timed Out.\n" + e);
			e.printStackTrace();
			System.exit(1);
		}

		/* Separating byte array received */
		elevatorOrFloor = data[0];
		elevatorOrFloorID = data[1];
		requestOrUpdate = data[2];
		currentFloor = data[3];
		upOrDown = data[4];
		destFloor = data[5];
	}

	public static int[] calculateResponseTimes(int destination, int requestDirection) {// destination is the floor that
		// is making the request
		// elevatorLowestRequestFloor
		// elevatorHighestRequestFloor
		// TIME_PER_FLOOR
		// DOOR_DURATION
		// UP
		// DOWN
		// HOLD
		int[] responseTime = new int[numElevators];
		int distance = 0;// number of floors traveled before arriving at the destination
		int stops = 0;// number of stops that need to be made before the destination (floor that's making the request)
		int highest;// highest requested
		int lowest;// lowest requested
		int current;// current floor
		int status;// elevator's status
		int next;// next stop

		for (int i = 0; i < numElevators; i++) {
			// check and set status, highest, current, and lowest floors,
			highest = elevatorHighestRequestFloor[i];
			lowest = elevatorLowestRequestFloor[i];
			current = elevatorCurrentFloor[i];
			status = elevatorStatus[i];
			next = elevatorNextStop[i];
			if (status == HOLD) {// elevator in hold
				// distance=|destination-current|
				distance = destination - elevatorCurrentFloor[i];
				stops = 0;// stops=0 since by definition hold means there were no prior requests or stops

			} else if (status == UP) {// elevator going up
				if (requestDirection == UP) {// if requesting to go up
					// if along the way
					if (destination >= next) {
						distance = destination - current;
					}
					// distance=destination-current
					// stops=stops between destination and current
					else {
						distance = (highest - current) + (highest - lowest) + (destination - lowest);
						stops = elevatorStopsUp[i].size() + elevatorStopsDown[i].size()
								+ stopsBetween(elevatorRequestsUp[i], lowest, destination, UP);
					}
					// else if missed
					// distance=(top-current)+(top-bottom)+(destination-bottom)
					// stops=upStops+downStops+upRequests before destination
				} else if (requestDirection == DOWN) {// if requesting to go down
					distance = (highest - current) + (highest - destination);
					stops = elevatorStopsUp[i].size() + stopsBetween(elevatorStopsDown[i], highest, destination, DOWN);
					// distance=(Top-current)+(top-destination)
					// stops=upStops+downStops between destination and top
				}
			} else if (elevatorStatus[i] == DOWN) {// elevator going down
				if (requestDirection == UP) {// if requesting to go up
					distance = (current - lowest) + (destination - lowest);
					stops = elevatorStopsDown[i].size() + stopsBetween(elevatorStopsUp[i], lowest, destination, UP);
					// distance=(current-Bottom)+(destination-Bottom)
					// stops=downStops+upStops between destination and botom
				} else if (requestDirection == DOWN) {// if requesting to go down
					if (destination <= elevatorNextStop[i]) {
						distance = current - destination;
						stops = stopsBetween(elevatorStopsDown[i], current, destination, DOWN);
					} else {
						distance = (current - lowest) + (highest - lowest) + (highest - destination);
						stops = elevatorStopsUp[i].size() + elevatorStopsDown[i].size()
								+ stopsBetween(elevatorRequestsDown[i], highest, destination, DOWN);
					}
					// if along the way
					// distance=current-destination
					// stops=stops between destinatino and current
					// else if missed
					// distance=(current-Bottom)+(top-bottom)+(top-destination)
					// stops=upStops+downStops+downRequests before destination
				}
			} else {
				// catastrophic error
				System.out.println("mismatch between motor status variables, status is " + elevatorStatus[i]
						+ " should only be HOLD: " + HOLD + " , UP: " + UP + " , and DOWN: " + DOWN);
			}
			responseTime[i] = distance * TIME_PER_FLOOR + stops * DOOR_DURATION;

		}
		return responseTime;

	}

	public static int stopsBetween(LinkedList<Integer> floors, int current, int destination, int direction) {// calculates
		// how many stops between between the destination and current floor for use in responseTime calculation
		int stops = 0;
		if (direction == UP) {
			for (int i = current; i < destination; i++) {
				if (floors.contains(current)) {
					stops++;
				}
			}
		} else if (direction == DOWN) {
			for (int i = current; i > destination; i--) {
				if (floors.contains(current)) {
					stops++;
				}
			}
		}
		return stops;
	}
	
	// variable definitions used to unpack/ coordinate/ allocate actions
	public byte[] SchedulingAlgorithm(DatagramPacket Packet) {
		
		byte[] packetData = schedulerElevatorReceivePacket.getData();
		int packetElementIndex = packetData[1];// index to find/ retrieve specific element from our array of
		// elevators and floors
		// should have been the name given to threads' constructor at creation
		int packetSentFrom = packetData[0];// elevator, floor, or other(testing/ error)
		// 21=elevator, 69=floor
		int packetIsStatus = packetData[2];// whether it is a status update from elevator or a request (elevator or
		// floor but handled differently)
		// 1=request, 2=status update
		int elevatorLocation = packetData[3];// where the elevator is currently located (sensor information sent
		// from elevator as status update)
		int stopRequest;// =packetData[]; //a request to give to an elevator for stopping at a given
		// floor (from elevator or floor)
		// public static int floorRequesting;

		int[] responseTime;// response time of individual elevators to got to a floor request
		int indexOfFastestElevator = 0;// index of array for which elevator is fastest
		int temp;// temporary for finding the fastest response time

		// check whether the packet was from an elevator (requests and status) or a
		// floor(request)
		// floor: allocate to an appropriate elevator (same direction, fastest response
		// time, least load)
		// if no currently allocatable elevators then add to requests linked list
		// elevator:

		// update unpack/ coordinate/ allocate action variables
		packetData = schedulerElevatorReceivePacket.getData();
		packetAddress = schedulerElevatorReceivePacket.getAddress();
		packetPort = schedulerElevatorReceivePacket.getPort();

		// packetElementIndex = packetData[1];// index to find/ retrieve specific
		// element from our array of elevators
		// and floors
		// should have been the name given to threads' constructor at creation
		//
		// packetSentFrom = packetData[0];// elevator, floor, or other(testing/ error)
		// 0=? 1=? 2=?
		// packetIsStatus = packetData[2];// whether it is a status update from elevator
		// or a request (elevator or
		// floor but handled differently)
		//
		elevatorLocation = packetData[3];// where the elevator is currently located (sensor information sent from
		// elevator as status update)
		stopRequest = packetData[5];// a request to stop at a given floor (-1 if no request)
		if (packetSentFrom == ELEVATOR_ID) {// if it is an elevator
			// elevatorNum=__;//which elevator it is in
			// status or request
			if (packetIsStatus == UPDATE) {// status update from Elevator
				// elevatorLocation=packetData[___];//status/ floor number from sensor in
				// Elevator
				// compare floor number with next stop of the elevator (==nextStop variable)
				// if (floorStatus==nextStop[packetElementIndex])
				if (elevatorStatus[packetElementIndex] == UP) {// direction that the elevator is going is up
					if (elevatorStopsUp[packetElementIndex].contains(elevatorLocation)) {// we have reached a
						// destination stop and
						// need to stop the
						// elevator
						// open the doors (closes automatically after preallocated duration)
						// create sendpacket to stop the elevator (and open, close the door)
						// send the sendPacket
						// remove the stop from goingup linked list
						// check if there are more stops
						sendData = createSendingData(packetElementIndex, 0, 0, 3);// 3: make a stop
						if (elevatorStopsUp[packetElementIndex].isEmpty()) {// no more stops Up
							// check if there are more requests
							if (elevatorRequestsUp[packetElementIndex].isEmpty()) {// no missed floors for going Up
								// do nothing
							} else {// there are outstanding requests to go Up
								elevatorStopsUp[packetElementIndex] = elevatorRequestsUp[packetElementIndex];
								// the requests to go Up can now be met once we've finished going down first
								elevatorRequestsUp[packetElementIndex].clear();
							}
							// check if there are more stops down
							if (elevatorStopsDown[packetElementIndex].isEmpty()) {// no more stops
								// create and send sendPacket to hold the motor
								sendData = createSendingData(packetElementIndex, 0, 0, 4);// 4: place on hold state

							} else {// we have stops to go up, start fulfilling those
								// create and send SendPacket for the motor to go Up
								sendData = createSendingData(packetElementIndex, 0, 0, 1);// 1: up
							}
						} else {// finished stopping for destination floor, continue going Up to fulfill other
								// stops
								// create and send SendPacket to restart the motor/ have the motor in the up
								// direction
							sendData = createSendingData(packetElementIndex, 0, 0, 1);// 1: up
						}
					} else {// not a floor that we need to stop at
						// do nothing
					}

				} else {// elevator is going down
					if (elevatorStopsDown[packetElementIndex].contains(elevatorLocation)) {// we have reached a
						// destination stop and
						// need to stop the
						// elevator
						// open the doors (closes automatically after preallocated duration)
						// create sendpacket to stop the elevator (and open, close the door)
						// send the sendPacket
						// remove the stop from goingup linked list
						// check if there are more stops
						sendData = createSendingData(packetElementIndex, 0, 0, 3);// 3: make a stop
						if (elevatorStopsDown[packetElementIndex].isEmpty()) {
							// check if there are more requests
							if (elevatorRequestsDown[packetElementIndex].isEmpty()) {// no missed floors for going
								// down
								// do nothing
							} else {// there are outstanding requests to go down
								elevatorStopsDown[packetElementIndex] = elevatorRequestsDown[packetElementIndex];
								// the requests to go down can be met once we have finished going up first
								elevatorRequestsDown[packetElementIndex].clear();
							}
							// check if there are more stops up
							if (elevatorStopsUp[packetElementIndex].isEmpty()) {// no more stops
								// create and send sendPacket to hold the motor
								sendData = createSendingData(packetElementIndex, 0, 0, 4);// 4: hold
							} else {// we have stops to go up, start fulfilling those
								// create and send SendPacket for the motor to go Down
								sendData = createSendingData(packetElementIndex, 0, 0, 2);// 2: down
							}
						} else {// finished stopping for a destination floor, continue fulfilling other stops
							// create and send SendPacket to restart the motor/ have the motor in the down
							// direction
							sendData = createSendingData(packetElementIndex, 0, 0, 2);// 2: down
						}
					} else {// not a floor that we need to stop at
						// do nothing
					}

				}

				// }
				// update floor number and direction displays for elevator and all floors
				sendData = createSendingData(0, 0, 0, 5);// 5: status update
			} else {// elevator sent a request

				// floorRequesting=packetElementIndex;//the floor# of the requesting floor
				// proximity

				// check availability and either allocate to a moving elevator, initiate the
				// movement of another, or add to request linked list if none available (or
				// wrong direction)

				// CHECK IF THE REQUEST IS A DUPLICATE, if so then ignore
				if (elevatorStatus[packetElementIndex] != HOLD) {// elevator is not in hold mode, currently moving
					// check direction
					if (elevatorStatus[packetElementIndex] == UP) {// elevator is going up
						if (elevatorLocation < stopRequest) {// we haven't reached that floor yet and can still stop
							// in time
							if (elevatorStopsUp[packetElementIndex].contains(stopRequest)) {// check if the request
								// is already in the
								// linked list
								// (duplicate) if so
								// then do nothing, else
								// add it
								// do nothing, don't want duplicates
							} else {
								elevatorStopsUp[packetElementIndex].add(stopRequest);// add to the stopsUp
								// linkedlist for the
								// current elevator
							}
						} else {// the stop has already been missed
							elevatorStopsDown[packetElementIndex].add(stopRequest);
							// add it to the stopDown linked list
						}
					} else {// elevator is going down
						if (elevatorLocation > stopRequest) {// we haven't reached that floor yet and can still stop
							// in time
							if (elevatorStopsDown[packetElementIndex].contains(stopRequest)) {// check if the
								// request is
								// already in the
								// linked list
								// (duplicate) if so
								// then do nothing,
								// else add it
								// do nothing, don't want duplicates
							} else {
								elevatorStopsDown[packetElementIndex].add(stopRequest);// add to the stopsDown
								// linkedlist for the
								// current elevator
							}

						} else {// the stop has already been missed
							elevatorStopsUp[packetElementIndex].add(stopRequest);
							// add it to the stopDown linked list
						}
					}
				} else {// currently in hold mode, we can fulfill that request immediately
					// can assume no stops or requests exist, don't need to check for duplicates
					if (elevatorLocation < stopRequest) {// we are below the destination floor, we need to go up
						elevatorStopsUp[packetElementIndex].add(stopRequest);
						// create and send sendPacket to start the motor
						sendData = createSendingData(packetElementIndex, 0, 0, 1);// 1: up
					} else {// we are above the destination floor, we need to go down
						elevatorStopsDown[packetElementIndex].add(stopRequest);
						// create and send sendPacket to start the motor
						sendData = createSendingData(packetElementIndex, 0, 0, 2);// 2: down
					}

				}
			}
		} else {// request is from floor
			responseTime = calculateResponseTimes(packetElementIndex, floorRequestDirection);
			temp = responseTime[0];
			for (int i = 1; i < responseTime.length; i++) {
				if (responseTime[i] < temp) {
					temp = responseTime[i];
					indexOfFastestElevator = i;
				}
			}

			// RECALL THE FLOOR CALLING SHOULD ONLY LET PASSENGERS IN WHEN IN THE CHOSEN
			// DIRECTION (UP/DOWN)
			if (elevatorStatus[indexOfFastestElevator] != HOLD) {// not in hold
				if (elevatorStatus[indexOfFastestElevator] == UP) {// elevator is going up
					if (floorRequestDirection == elevatorStatus[indexOfFastestElevator]) {// floor is requesting to
						// go up also
						if (packetElementIndex > elevatorLocation) {// still time
							if (elevatorStopsUp[indexOfFastestElevator].contains(packetElementIndex)) {
								// already have that stop requested, don't want to duplicate
							} else {
								elevatorStopsUp[indexOfFastestElevator].add(packetElementIndex);// add to stops list
							}
						} else {// missed
							if (elevatorRequestsUp[indexOfFastestElevator].contains(packetElementIndex)) {
								// already have that stop requested, don't want to duplicate
							} else {
								elevatorRequestsUp[indexOfFastestElevator].add(packetElementIndex);// add the floor
								// to requests
							}
						}
					} else {// elevator is currently fulfilling down stops
						if (elevatorRequestsUp[indexOfFastestElevator].contains(packetElementIndex)) {
							// already have that stop requested, don't want to duplicate
						} else {
							elevatorRequestsUp[indexOfFastestElevator].add(packetElementIndex);// add the floor to
							// requests
						}
					}
				} else {// elevator is going down
					if (floorRequestDirection == elevatorStatus[indexOfFastestElevator]) {// floor is requesting to
						// go Down also
						if (packetElementIndex < elevatorLocation) {// still time
							if (elevatorStopsDown[indexOfFastestElevator].contains(packetElementIndex)) {
								// already have that stop requested, don't want to duplicate
							} else {
								elevatorStopsDown[indexOfFastestElevator].add(packetElementIndex);// add to stops
								// list
							}
						} else {// missed
							if (elevatorRequestsDown[indexOfFastestElevator].contains(packetElementIndex)) {
								// already have that stop requested, don't want to duplicate
							} else {
								elevatorRequestsDown[indexOfFastestElevator].add(packetElementIndex);// add the
								// floor to
								// requests
							}
						}
					} else {// eleveator is currently fulfilling Up stops
						if (elevatorRequestsDown[indexOfFastestElevator].contains(packetElementIndex)) {
							// already have that stop requested, don't want to duplicate
						} else {
							elevatorRequestsDown[indexOfFastestElevator].add(packetElementIndex);// add the floor to
							// requests
						}
					}
				}
			} else {// holding, can fulfill immediately
				// can assume no stops or requests exist, don't need to check for duplicates
				// if above
				if (packetElementIndex > elevatorLocation) {// the floor requesting is above the elevator's current
					// location
					elevatorRequestsDown[indexOfFastestElevator].add(packetElementIndex);
					// create and send sendPacket to start motor in Down direction
					sendData = createSendingData(packetElementIndex, 0, 0, 2);// 2: down
				}

				else {// (packetElementIndex<elevatorLocation) {//the floor requesting is below the
						// elevator's current location
					elevatorRequestsUp[indexOfFastestElevator].add(packetElementIndex);
					// create and send sendPacket to start motor in Up direction
					sendData = createSendingData(packetElementIndex, 0, 0, 2);// 1: up
				}
			}
		}

	/*	sendData[0] = 54;
		sendData[1] = 21;
		sendData[2] = 1;
		sendData[3] = packetData[3];
		sendData[4] = 0;
		//sendData[5] = 2; floor request from elevator hardcoded to be 3
		sendData[6] = UP;

		System.out.println("Send Data: " + Arrays.toString(sendData));
		System.out.println("Packet Data: " + Arrays.toString(packetData));  */
		
		return sendData;
	}

	public static byte[] createSendingData(int target, int currentFloor, int direction, int instruction) {

		ByteArrayOutputStream sendingOutputStream = new ByteArrayOutputStream();
		sendingOutputStream.write(SCHEDULER_ID); // Identifying as the scheduler
		sendingOutputStream.write(target); // (exact floor or elevator to receive)
		sendingOutputStream.write(0); // not needed (request or status update: for sending to scheduler)
		// somewhat redundant usage since floors would only receive updates and
		// elevators would only receive requests
		if (instruction == 5) {// update displays of the floors
			sendingOutputStream.write(currentFloor); // (current floor of elevator)
			sendingOutputStream.write(direction); // (direction of elevator)
		} else {
			sendingOutputStream.write(0); // not needed (current floor of elevator)
			sendingOutputStream.write(0); // not needed (direction of elevator)
		}
		sendingOutputStream.write(0); // not needed (destination request)
		sendingOutputStream.write(instruction); // scheduler instruction
		sendData = sendingOutputStream.toByteArray();
		return sendData;
	}

	public static void elevatorSendPacket(byte[] sendData) {
		/* SENDING ELEVATOR PACKET HERE */

		byte[] responseByteArray = new byte[7];
		responseByteArray = sendData;

		// responseByteArray = createSendingData(elevatorOrFloor, currentFloor,
		// upOrDown, instruction);
		System.out.println("Response to elevator " + data[1] + ": " + Arrays.toString(responseByteArray) + "\n");
		schedulerElevatorSendPacket = new DatagramPacket(responseByteArray, responseByteArray.length,
				schedulerElevatorReceivePacket.getAddress(), schedulerElevatorReceivePacket.getPort());
		try {
			schedulerSocketSendReceiveElevator.send(schedulerElevatorSendPacket);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	public static void floorSendPacket() {
		/* FLOOR SENDING PACKET HERE */

		byte[] responseByteArray = new byte[5];
		responseByteArray = createSendingData(elevatorOrFloor, currentFloor, upOrDown, instruction);
		System.out.println("Response to Floor " + data[1] + ": " + Arrays.toString(responseByteArray) + "\n");
		try {
			schedulerFloorSendPacket = new DatagramPacket(responseByteArray, responseByteArray.length,
					InetAddress.getLocalHost(), PORTNUM);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		try {
			schedulerSocketSendReceiveFloor.send(schedulerFloorSendPacket);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/*------------------------------------------------------------------*/
	public static void main(String args[]) throws InterruptedException {
		
		Scheduler Scheduler = new Scheduler();
		Scheduler.linkedListInitialization();

		/*
		 * //elevatorStopsUp[0].add(3); //elevatorStopsDown[1].add(2);
		 * 
		 * 
		 * //create temporary testing packets to simulate "Requests" sent to the
		 * schedulingAlrogirthm because it needs a parameter
		 * 
		 * byte tempTestData[] = new byte[7]; tempTestData[0]= ELEVATOR_ID;//pretending
		 * to be an elevator tempTestData[1]= 0;//pretending to be elevator #1
		 * tempTestData[2]= REQUEST;//simulating a request tempTestData[3]= 0;//ground
		 * floor tempTestData[4]= 0;//up or down tempTestData[5]= 2;//destination floor,
		 * request for floor 2 tempTestData[6]= 0;//scheduler instruction- not used now
		 * 
		 * DatagramPacket testingPacket=new DatagramPacket
		 * (tempTestData,tempTestData.length); Scheduler.SchedulingAlgorithm(
		 * schedulerElevatorReceivePacket);//call method with simulated packet for
		 * elevator #1 tempTestData[1]=1;//for elevator number 2
		 * tempTestData[5]=3;//destination floor, request for floor 3 DatagramPacket
		 * testingPacket2=new DatagramPacket (tempTestData,tempTestData.length);
		 * Scheduler.SchedulingAlgorithm(schedulerElevatorReceivePacket);//call method
		 * with simulated packet for elevator #2
		 */
		
		 
		for (;;) {

			// Receives the Packet from Elevator
			DatagramPacket packetRecieved = Scheduler.elevatorReceivePacket();
			// Sorts the received Packet and returns the byte array to be sent
			sendData = Scheduler.SchedulingAlgorithm(packetRecieved);
			// Sends the Packet to Elevator
			elevatorSendPacket(sendData);

		}
	}
}
