import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class Elevator extends Thread {
	
	/* Packet declaration to send and receive data */
	private static DatagramPacket ElevatorSendPacket, ElevatorReceivePacket;
	
	/* DatagramSocket declaration */
	private static DatagramSocket ElevatorSendRecieveReceiveSocket;

	/* UNIFIED CONSTANTS DECLARATION FOR ALL CLASSES*/
	public static final byte HOLD = 0x00;// Elevator is in hold state
	public static final byte UP = 0x01;// Elevator is going up
	public static final byte DOWN = 0x02;// Elevator is going down
	public static final int Elevator_ID = 21;// for identifying the packet's source as Elevator
	public static final int DOOR_OPEN = 1;// the door is open when ==1
	public static final int DOOR_DURATION = 4;// duration that doors stay open for
	public static final int REQUEST = 1;// for identifying the packet sent to scheduler as a request
	public static final int UPDATE = 2;// for identifying the packet sent to scheduler as a status update
	
	/* Variables used in this class*/
	private int sensor;
	private int nameOfElevator;
	private int toDoID;
	private byte instruction;
	private int initialFloor;
	private static List<String> fileRequests = new ArrayList<String>();

	private boolean hasRequest = false;
	private static int floorRequest;
	//private byte motorDirection;

	private static int floorButton;

	/* Table to synchronize threads */
	public LinkedList<byte[]> ElevatorTable =new LinkedList<byte[]>();
	//public static List<byte[]> ElevatorTable = Collections.synchronizedList(new ArrayList<byte[]>());
	public boolean runningStatus= false;

	private boolean holdReceived;
	
	public Elevator(int nameOfElevator, int initialFloor,  LinkedList<byte[]> ElevatorTable) {
		this.nameOfElevator = nameOfElevator;
		this.setInitialFloor(initialFloor);
		this.ElevatorTable = ElevatorTable;
	}
	
	public int currentFloor(int floorSensor) { // method to initialize where the Elevator starts
		sensor = floorSensor;
		return sensor;
	}
	

	public int getInitialFloor() {
		return sensor;
	}

	public void setInitialFloor(int initialFloor) {
		sensor = initialFloor;
	}
	
	public int runElevator(byte motorDirection) {
		if (motorDirection == UP || motorDirection == DOWN) {
			try {
				System.out.println("current floor: " + sensor + " --> of Elevator "+nameOfElevator); // sensor = current floor
				Thread.sleep(3000);
				if (motorDirection == UP) {
					System.out.println("Elevator going up");
					sensor++; // increment the floor
					setInitialFloor(sensor); // updates the current floor
				} else if (motorDirection == DOWN) {
					System.out.println("Elevator going down");
					sensor--; // decrements the floor
					setInitialFloor(sensor); // updates the current floor
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else if (motorDirection == HOLD) {
			setInitialFloor(sensor); // updates current floor - in this case nothing changes
		}
		System.out.println("current floor: " + sensor + " --> of Elevator "+nameOfElevator); // prints out the current floor - in this case destination floor
		return getInitialFloor(); // returns and updates the final current of the floor - in this case destination floor
	}
	
	public byte[] responsePacketRequest(int requestUpdate, int floorRequest) {

		/*
		 * Elevator --> SCHEDULER (Elevator or floor (Elevator-21), Elevator id(which
		 * Elevator), FloorRequest/update, curentFloor, up or down, destFloor,
		 * instruction) (
		 */
		// creates the byte array according to the required format

		ByteArrayOutputStream requestElevator = new ByteArrayOutputStream();
		requestElevator.write(Elevator_ID); // Elevator
		requestElevator.write(nameOfElevator); // Elevator id

		// request/ update
		if (requestUpdate == REQUEST) {
			requestElevator.write(REQUEST); // request/
			requestElevator.write((byte) getInitialFloor()); // current floor
			requestElevator.write(0); // up or down
			requestElevator.write(floorRequest); // dest floor
			requestElevator.write(0); // instruction
		} else if (requestUpdate == UPDATE) {
			requestElevator.write(UPDATE); // update
			requestElevator.write((byte) getInitialFloor()); // current floor
			requestElevator.write(0); // up or down
			requestElevator.write(floorRequest); // dest floor
			requestElevator.write(0); // instruction
		} else {
			requestElevator.write(requestUpdate); // update
			requestElevator.write((byte) getInitialFloor()); // current floor
			requestElevator.write(0); // up or down
			requestElevator.write(floorRequest); // dest floor
			requestElevator.write(0);
		}
		return requestElevator.toByteArray();
	}

	public String openCloseDoor(byte door) {
		String msg;
		if (door == DOOR_OPEN) {
			msg = "Doors are open.";
			System.out.println("\n" + msg);
			try {
				int i = 4;
				while (i != 0) {
					System.out.format("Seconds until Elevator %d door closes: %d second \n", nameOfElevator,i);
					i--;
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			msg = "Doors are closed.";
			System.out.println(msg);
		}
		return msg;
	}
	
	public synchronized static void sendPacket(byte[] toSend) throws InterruptedException {

		byte[] data = new byte[7];
		data = toSend;
		
		System.out.print("Sendind to scheduler: ");
		System.out.println(Arrays.toString(data));
		try {
			InetAddress address = InetAddress.getByName("134.117.59.127");
			//System.out.println("\nSending to scheduler from Elevator "+ data[1] + ":" + Arrays.toString(data));
			ElevatorSendPacket = new DatagramPacket(data, 7, address, 369);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}
		try {
			ElevatorSendRecieveReceiveSocket.send(ElevatorSendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public synchronized byte[] receivePacket() throws InterruptedException {
		
		byte data[] = new byte[7];
		ElevatorReceivePacket = new DatagramPacket(data, data.length);
		
		try {
			// Block until a datagram packet is received from receiveSocket.
			ElevatorSendRecieveReceiveSocket.receive(ElevatorReceivePacket);
			//System.out.print("Received from scheduler: ");
			//System.out.println(Arrays.toString(data));
		} catch (IOException e) {
			System.out.print("IO Exception: likely:");
			System.out.println("Receive Socket Timed Out.\n" + e);
			e.printStackTrace();
			System.exit(1);
		}
		
		//ElevatorTable.add(data);
		System.out.print("Received from scheduler: ");
		System.out.println(Arrays.toString(data));
		return data;

	}


	public void fileReader(String fullFile) { 
		String text = "";
		int i=0;
		try { 
			FileReader input = new FileReader(fullFile);
			Scanner reader = new Scanner(input);
			reader.useDelimiter("[\n]");

			while (reader.hasNext()){
				text = reader.next();
				if (i<=1) {
					i++;
				} else if(i>=2) {
					fileRequests.add(text);
					i++;
				}
			}
		}catch(Exception e) { e.printStackTrace(); }
	}
	
	public void run() {
		while(!isInterrupted()) {
				if(runningStatus == true) {
					if(toDoID == nameOfElevator) {
						//System.out.println("Elevator number" +toDoID + " working");
						if(instruction == 2 || instruction == 1) {
							//System.out.println(instruction);
							this.runElevator(instruction);
							try {
								sendPacket(responsePacketRequest(UPDATE,0));
							} catch (InterruptedException e) {
								e.printStackTrace();
							}				
						} else if (instruction == 0) {
							System.out.println();
							System.out.printf("------------------------------ OPENING DOOR FOR ELEVATOR %d -----------------", nameOfElevator);
							openCloseDoor((byte)DOOR_OPEN);
							this.holdReceived = true;
/*							try {
								sendPacket(responsePacketRequest(UPDATE,0));
							} catch (InterruptedException e) {
								e.printStackTrace();
							}*/
						} else if (instruction == 4) {
							//System.out.println(instruction + "  ---> ELEVATOR " + nameOfElevator);
							System.out.printf("No requests. Elevator %d has stopped\n", nameOfElevator);
						} else if(instruction == 5) {
							
						}
					}
					this.runningStatus = false;
				}
			}
		System.out.println("Elevator " + nameOfElevator + " Thread ended");
	}

	
	
	public static void main(String args[]) throws InterruptedException {
		
		int initialFloor0 = Integer.parseInt(args[0]);	// The number of Elevators in the system is passed via
		int initialFloor1 = Integer.parseInt(args[1]);	
		
		LinkedList<byte[]> ElevatorTable1 =new LinkedList<byte[]>();
		Elevator Elevator0 = new Elevator(0, initialFloor0, ElevatorTable1);
		Elevator Elevator1 = new Elevator(1, initialFloor1, ElevatorTable1);
		
		try {
			ElevatorSendRecieveReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {// if Socket creation fails an exception is thrown
			se.printStackTrace();
			System.exit(1);
		}
		
		Elevator0.fileReader("M://hello.txt");
		//System.out.println(fileRequests.get(0));
		
		sendPacket(Elevator1.responsePacketRequest(UPDATE,0));
		sendPacket(Elevator0.responsePacketRequest(UPDATE,0));
		
		sendPacket(Elevator1.responsePacketRequest(3,0));
		//sendPacket(Elevator1.responsePacketRequest(UPDATE,0));
/*		Elevator0.ElevatorTable.add(0,Elevator0.responsePacketRequest(1, 6));
		Elevator1.ElevatorTable.add(1,Elevator1.responsePacketRequest(1, 4));
		
		sendPacket(ElevatorTable1.get(0));
		sendPacket(ElevatorTable1.get(1));
		ElevatorTable1.clear();*/

		Thread fileStuff = new Thread() {
			public void run() {
				while(true) {
					while(Elevator0.holdReceived || Elevator1.holdReceived) {
						if(fileRequests.isEmpty() && Elevator0.holdReceived) {
							try {
								sendPacket(Elevator0.responsePacketRequest(UPDATE,0));
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							Elevator0.holdReceived = false;
							break;
						} else if(fileRequests.isEmpty() && Elevator1.holdReceived) {
							try {
								sendPacket(Elevator1.responsePacketRequest(UPDATE,0));
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							Elevator1.holdReceived = false;
							break;
						} else {
							for(int i = 0; i <fileRequests.size(); i++) {
								String command = fileRequests.get(i);
								String segment[] = command.split(" ");
								floorButton = Integer.parseInt(segment[1]);
								System.out.println("HERE" + segment[3] +".....");
								floorRequest = Integer.parseInt(segment[3]);
								if(floorButton == Elevator0.getInitialFloor()) {								
									try {
										sendPacket(Elevator0.responsePacketRequest(REQUEST, floorRequest));
										fileRequests.remove(i);
										break;
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								} else if(floorButton == Elevator1.getInitialFloor()) {
									try {
										sendPacket(Elevator1.responsePacketRequest(REQUEST, floorRequest));
										fileRequests.remove(i);
										break;
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
							}
							Elevator1.holdReceived = false;
							Elevator0.holdReceived = false;
							break;
						}
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		Elevator0.start();
		Elevator1.start();
		fileStuff.start();

		try {
			while(true) {
				byte[] x = new byte[7];
				byte[] data = new byte[7];
				byte[] data1 = new byte[7];
				long startTime = System.nanoTime();
				x = Elevator0.receivePacket();
				ElevatorTable1.add(x);
				if (x[1] == 0 && Elevator0.runningStatus == false) {
					data = Elevator0.ElevatorTable.remove(0);
					Elevator0.toDoID = data[1];
					Elevator0.instruction = data[6];
					Elevator0.runningStatus = true;
				}
				if(x[1] == 1 && Elevator1.runningStatus == false) {
					data1 = Elevator1.ElevatorTable.remove(0);
					Elevator1.toDoID = data1[1];
					Elevator1.instruction = data1[6];
					Elevator1.runningStatus = true;
				
				} 
				long endTime = System.nanoTime();
				long timeElapsed = endTime - startTime;
				System.out.println("\n\nExecution time in milliseconds : " + 
						timeElapsed / 1000000);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
}
