import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Panel;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import javafx.scene.text.Font;

public class GUI extends ElevatorIntermediate {

	// Value of numOfElevators must be taken from elevator intermediate as it initializes the number of elevators
	int numElevators = createNumElevators;	
	
GUI() {
		//	INITIALIZATIONS
		//Frame initialization
		JFrame frame = new JFrame("Elevator GUI");
		//Button(s) & TesxtArea initialization
		JButton button[] = new JButton[numElevators] ;
		JTextArea textArea[] = new JTextArea[numElevators];
		Panel pnl1 = new Panel();
		Panel pnl2 = new Panel();
		
		//Layout Initialization
		FlowLayout flowLayout = new FlowLayout();
		GridLayout gridLayout = new GridLayout();
		pnl1.setLayout(gridLayout);	// Elevator Labels
		pnl2.setLayout(flowLayout);	// Current floors
		frame.setLayout(flowLayout);
		
		
		// Dynamically creates the number of buttons/ textAreas depending on numOfElevators 
		// initialized in elevator intermediate class
		
		//Buttons
		for(int i = 0; i< numElevators; i++) {
			button[i] = new JButton("Elevator " + i);	
			pnl1.add(button[i]);
		}
		
		//CurrentFloor
		for(int i = 0; i< numElevators; i++) {
			textArea[i] = new JTextArea("Current FLoor " /*+ elevatorCurrentFloor[i]*/);	
			pnl2.add(textArea[i]);
		}
		
		
		JTextArea TextErrorArea = new JTextArea(
			    "Elevator Error Message: \n" +
			    "A text area is a \"plain\" text component, " +
			    "which means that although it can display text " +
			    "in any font, all of the text is in the same font."
			);
		TextErrorArea.setLineWrap(true);
		TextErrorArea.setWrapStyleWord(true);
		TextErrorArea.setEditable(false);
			
		JScrollPane areaScrollPane = new JScrollPane(TextErrorArea);
		areaScrollPane.setVerticalScrollBarPolicy(
		                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		areaScrollPane.setPreferredSize(new Dimension(250, 100));
		
		frame.add(pnl1);
		frame.add(pnl2);
		frame.add(areaScrollPane);
		
		//Set Layout to frame
		frame.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
		
		//Frame dimensions
		frame.setSize(450,200); 
		 
		frame.setVisible(true);  
	}
}