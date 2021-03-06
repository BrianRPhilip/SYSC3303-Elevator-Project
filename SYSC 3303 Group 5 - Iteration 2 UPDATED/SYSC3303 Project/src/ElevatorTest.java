
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

class ElevatorTest {

	public static String NAMING;
	public static int floorRequest;
	private static int sensor = 1;
	/*
	 * private static byte hold = 0x00; private static byte up = 0x01; private
	 * static byte down = 0x02;
	 * 
	 * 
	 * DatagramPacket elevatorSendPacket, elevatorReceivePacket; DatagramSocket
	 * elevatorSendSocket, elevatorReceiveSocket;
	 * 
	 * public static final int TENTH_FLOOR = 10; public static final int
	 * SECOND_FLOOR = 2; public static final int BASEMENT_TWO = -2;
	 */

	private Elevator elevator;

	@Test
	public void testinitializeElevator() {
		elevator = new Elevator("one");
		assertNotNull(elevator);

	}

	@Test
	public void testResponsePacket() {
		Elevator elevator = new Elevator();

		floorRequest = 2;
		byte[] testarray = new byte[4];
		testarray[0] = 0; // write 0
		testarray[1] = 2; // floorRequest = 2
		testarray[2] = 1; // sensor = 1
		testarray[3] = 0; // write 0

		assertArrayEquals(testarray, elevator.responsePacket(floorRequest));
	}

	@Test
	public void testRun() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testElevator() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testOpenCloseDoor() throws Exception {
		Elevator elevator1 = new Elevator();
		// Test for Doors closed
		String expected = "Doors are closed.";
		assertEquals(expected, elevator1.openCloseDoor((byte) 0));
		// Test for Doors open
		String expected1 = "Doors are open.";
		assertEquals(expected1, elevator1.openCloseDoor((byte) 1));
		System.out.println("------------------End of testOpenCloseDoor()---------------------\n ");

	}

	@Test
	public void testCurrentFloor() {
		Elevator elevator = new Elevator();
		// elevator = new Elevator("one");
		assertEquals(2, elevator.currentFloor(2));

	}

	/*
	 * @Test public void testFileReader() throws Exception { Elevator elevator=new
	 * Elevator(); String s =
	 * "C:/Users/brianranjanphilip.LABS.000/Desktop/testfile.txt";
	 * System.out.println(elevator.fileReader(s) + "\n\n");
	 * 
	 * }
	 */

	/*
	 * @Test public void testElevatorRequestFromFile() throws Exception { Elevator
	 * elevator=new Elevator(); String s = "14:05:15:0 2 up 4";
	 * System.out.println(elevator.elevatorRequestFromFile(s) + "\n\n");
	 * 
	 * }
	 */

	@Test
	public void testRunElevator() throws Exception {
		Elevator elevator = new Elevator();
		elevator.currentFloor(2);
		// Elevator going up
		// System.out.println(elevator.runElevator((byte) 1,(byte) 4));
		assertEquals(6, elevator.runElevator((byte) 1, (byte) 4));
		// Elevator going down
		// assertEquals(3, elevator.runElevator((byte) 2,(byte) 4));
		System.out.println("current floor: " + elevator.sensor + "\n\n");

		assertEquals(10, elevator.runElevator((byte) 1, (byte) 4));
		System.out.println("current floor: " + elevator.sensor);

		System.out.println("------------------End of testRunElevator()-------------------\n ");

	}

}
