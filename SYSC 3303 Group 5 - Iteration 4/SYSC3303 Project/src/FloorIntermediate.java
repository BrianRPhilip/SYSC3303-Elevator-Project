import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FloorIntermediate {

	/*
	// UNIFIED CONSTANTS DECLARATION FOR ALL CLASSES
	private static final byte HOLD = 0x00;// elevator is in hold state
	private static final byte UP = 0x01;// elevator is going up
	private static final byte DOWN = 0x02;// elevator is going down
	private static final int ELEVATOR_ID = 21;// for identifying the packet's source as elevator
	private static final int FLOOR_ID = 69;// for identifying the packet's source as floor
	private static final int SCHEDULER_ID = 54;// for identifying the packet's source as scheduler
	private static final int DOOR_OPEN = 1;// the door is open when ==1
	private static final int DOOR_DURATION = 4;// duration that doors stay open for
	private static final int REQUEST = 1;// for identifying the packet sent to scheduler as a request
	private static final int UPDATE = 2;// for identifying the packet sent to scheduler as a status update
	//*/
	
	
	// UNIFIED CONSTANTS DECLARATION FOR ALL CLASSES
		// States
		private static final byte UP = 0x01;// elevator is going up
		private static final byte DOWN = 0x02;// elevator is going down
		private static final byte STOP = 0x03;
		private static final byte HOLD = 0x04;// elevator is in hold state
		private static final byte UPDATE_DISPLAY = 0x05;
		private static final byte ERROR = (byte) 0xE0;// an error has occured
		// Errors
		private static final byte DOOR_ERROR = (byte)0xE1;
		private static final byte MOTOR_ERROR = (byte)0xE2;
		// still error states between 0xE3 to 0xEE for use
		private static final byte OTHER_ERROR = (byte)0xEF;
		private static final byte NO_ERROR = 0x00;
		// Object ID
		private static final int ELEVATOR_ID = 21;// for identifying the packet's source as elevator
		private static final int FLOOR_ID = 69;// for identifying the packet's source as floor
		private static final int SCHEDULER_ID = 54;// for identifying the packet's source as scheduler
		// Values for Running
		private static final int DOOR_OPEN = 1;// the door is open when == 1
		private static final int DOOR_CLOSE = 3; // the door is closed when == 3  
		private static final int DOOR_DURATION = 4;// duration (in seconds) that doors stay open for
		private static final int REQUEST = 1;// for identifying the packet type sent to scheduler as a request
		private static final int UPDATE = 2;// for identifying the packet type sent to scheduler as a status update
		private static final int UNUSED = 0;// value for unused parts of data
		private static final int DOOR_CLOSE_BY = 6;// door shouldn't be open for longer than 6 seconds

	public static final int EL_RECEIVEPORTNUM = 369;
	public static final int EL_SENDPORTNUM = 159;

	/*//FROM ELEVATORINTERMEDIATE
	private static final int SENDPORTNUM = 369;
	private static final int RECEIVEPORTNUM = 159;
	 */

	private static final int SENDPORTNUM = 369;
	private static final int RECEIVEPORTNUM = 1199;

	public static final int FL_RECEIVEPORTNUM = 488;
	public static final int FL_SENDPORTNUM = 1199;

	private static DatagramPacket floorSendPacket, floorReceivePacket;
	private static DatagramSocket floorReceiveSocket,floorSendSocket;

	public static List<byte[]> floorTable = Collections.synchronizedList(new ArrayList<byte[]>());
	private static Floor floorArray[];
	private static Thread floorThreadArray[];

	// arrays to keep track of the number of elevators, eliminates naming confusion
	private static int name;
	private static boolean hasRequest;
	private static int up_or_down;
	/*
	 * send sockets should be allocated dynamically since the ports would be
	 * variable to the elevator or floor we have chosen
	 */
	//public static final int SENDPORTNUM = 488;

	public FloorIntermediate() {
		try {
			floorSendSocket = new DatagramSocket();//FL_RECEIVEPORTNUM);
		} catch (SocketException se) {// if DatagramSocket creation fails an exception is thrown
			se.printStackTrace();
			System.exit(1);
		}
	}

	/*public void sendPacket(byte[] requestPacket) {
		int lengthOfByteArray = requestPacket.length;
		System.out.println("Request from Floor " + requestPacket[1] + ": " + Arrays.toString(requestPacket));
		try {

			floorSendPacket = new DatagramPacket(requestPacket, lengthOfByteArray, InetAddress.getLocalHost(), FL_SENDPORTNUM);

			System.out.print("I've sent\n");
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}
		try {
			floorSendSocket.send(floorSendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}*/

	public synchronized void sendPacket() {
		// byte[] requestElevator = new byte[7];

		/* ELEVATOR --> SCHEDULER (0, FloorRequest, cuurentFloor, 0) */

		// System.out.println("Enter floor number: ");

		// Scanner destination = new Scanner(System.in);
		// int floorRequest;
		// if (destination.nextInt() != 0) {
		// floorRequest = destination.nextInt();
		// } else {

		// }
		// destination.close();
		// requestElevator = elevatorArray[0].responsePacketRequest(1); // this goes
		// into the first index of elevatorArray list, and tells that elevator to return
		// a byte array that
		// will be the packet that is being sent to the Scheduler. This needs to be done
		// in a dynamic manner so all
		// elevators can acquire a lock to send a packet one at a time.

		// allocate sockets, packets
		synchronized (floorTable) {
			while (floorTable.isEmpty()) {
				try {
					floorTable.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (floorTable.size() != 0) {
				try {
					System.out.println("\nSending to scheduler: " + Arrays.toString(floorTable.get(0)));
					floorSendPacket = new DatagramPacket(floorTable.get(0), 7, InetAddress.getLocalHost(),
							SENDPORTNUM);
				} catch (UnknownHostException e) {
					e.printStackTrace();
					System.exit(1);
				}
				try {
					floorSendSocket.send(floorSendPacket);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				floorTable.clear();
				floorTable.notifyAll();
			}
		}

	}

	public byte[] createResponsePacketData(int requestUpdateError, byte errorType) {// create the Data byte[] for
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
		requestElevator.write(name); // identity of this particular elevator object

		// request or update data
		if (requestUpdateError == REQUEST) {
			requestElevator.write(REQUEST); // request
			// requestElevator.write((byte) setSensor(sensor)); // current floor
			// requestElevator.write(0); // up or down (not used, only for Floors)
			// requestElevator.write(RealTimefloorRequest); // dest floor
			// requestElevator.write(0); // instruction (not used, only from the scheduler)
			// (not used, only from the scheduler)
			// added error to data structure, not included here
		} else if (requestUpdateError == UPDATE) {
			requestElevator.write(UPDATE); // update
			// requestElevator.write((byte) setSensor(sensor)); // current floor
			// requestElevator.write(0); // up or down (not used, only for Floors)
			// requestElevator.write(RealTimefloorRequest); // dest floor
			// requestElevator.write(0); // instruction
			// added error to data structure, not included here
		} else if (requestUpdateError == ERROR) {
			requestElevator.write(ERROR); // update
			requestElevator.write(UNUSED);//(byte) setSensor(sensor)); // current floor
			requestElevator.write(UNUSED); // up or down (not used, only for Floors)
			requestElevator.write(UNUSED); // dest floor
			requestElevator.write(UNUSED); // instruction (not used, only from the scheduler)
			requestElevator.write(errorType); // error ID
			return requestElevator.toByteArray();
		} else {// something's gone wrong with the call to this method
			requestElevator.write(ERROR);
			System.out.println(name
					+ " Floor ERROR: called createResponsePacketData with neither REQUEST, UPDATE, or ERROR");
			requestElevator.write(UNUSED);//(byte) setSensor(sensor)); // current floor
			requestElevator.write(UNUSED); // up or down (not used, only for Floors)
			requestElevator.write(UNUSED); // dest floor
			requestElevator.write(UNUSED); // instruction (not used, only from the scheduler)
			requestElevator.write(OTHER_ERROR); // something's gone wrong
			return requestElevator.toByteArray();
		}
		requestElevator.write(UNUSED);//(byte) setSensor(sensor)); // current floor
		requestElevator.write(UNUSED); // up or down (not used, only for Floors)
		requestElevator.write(UNUSED); // dest floor
		requestElevator.write(UNUSED); // instruction (not used, only from the scheduler)
		requestElevator.write(UNUSED); // no errors
		return requestElevator.toByteArray();
	}

	public synchronized void sendPacket(int requestUpdateError, byte sendErrorType) {
		synchronized (floorTable) {
			while (floorTable.size() != 0) {// wait for an opening to send the packet
				try {
					floorTable.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			floorTable.add(createResponsePacketData(requestUpdateError, sendErrorType));
			// isUpdate = false;
			floorTable.notifyAll();
		}
	}

	public synchronized void receivePacket() {
		byte data[] = new byte[7];
		try {
			floorReceiveSocket = new DatagramSocket(RECEIVEPORTNUM);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		floorReceivePacket = new DatagramPacket(data, data.length);
		/*try {
			System.out.println("Waiting...\n"); // so we know we're waiting
			floorReceiveSocket.receive(floorReceivePacket);
			System.out.println("Got it");
		}

		catch (IOException e) {
			System.out.print("IO Exception: likely:");
			System.out.println("Receive Socket Timed Out.\n" + e);
			e.printStackTrace();
			System.exit(1);
		}*/

		try {
			// Block until a datagram packet is received from receiveSocket.
			System.out.println("waiting to receive");
			floorReceiveSocket.receive(floorReceivePacket);
			System.out.print("Received from scheduler: ");
			System.out.println(Arrays.toString(data));
		} catch (IOException e) {
			System.out.print("IO Exception: likely:");
			System.out.println("Receive Socket Timed Out.\n" + e);
			e.printStackTrace();
			System.exit(1);
		}
		floorReceiveSocket.close();

	}


	public static void main(String args[]) {// throws IOException {

		// for iteration 1 there will only be 1 elevator
		// getting floor numbers from parameters set
		int createNumFloors = Integer.parseInt(args[0]);// The number of Elevators in the system is passed via argument[0]
		FloorIntermediate floorHandler = new FloorIntermediate();
		//Floor floor = new Floor(createNumFloors);

		//floor.fileReader("M://hello.txt");
		createNumFloors = Integer.parseInt(args[0]);
		floorArray = new Floor[createNumFloors];
		floorThreadArray = new Thread[createNumFloors];
		for (int i = 0; i < createNumFloors; i++) {
			floorArray[i] = new Floor(i, floorTable);//0, floorTable, Integer.parseInt(args[i + 1])); // i names the
			// elevator, 0
			// initializes the
			// floor it
			// starts on
			floorThreadArray[i] = new Thread(floorArray[i]);
			floorThreadArray[i].start();
		}

		while(true) {
			floorHandler.sendPacket();
			floorHandler.receivePacket();
		}

		/*while (true) {
			if(floor.fileRequests.isEmpty()) {
				hasRequest = false;
			} else {
				hasRequest = true;
				String command = floor.fileRequests.remove(0);
				String segment[] = command.split(" ");
				name = Integer.parseInt(segment[1]);
				if(segment[2].equals("Up")) {
					up_or_down = UP;
				} else if(segment[2].equals("Down")) {
					up_or_down = DOWN;
				}
			}

			if(hasRequest == true) {
				floorHandler.sendPacket();//name, up_or_down));
			} else {
				floorHandler.sendPacket();//(0, 0));
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			floorHandler.receivePacket();
		}*/
	}
}